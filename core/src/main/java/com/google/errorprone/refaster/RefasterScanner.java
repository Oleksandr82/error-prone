/*
 * Copyright 2014 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.refaster;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.DescriptionListener;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.refaster.annotation.CanTransformToTargetType;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.parser.Tokens.Token;
import com.sun.tools.javac.parser.Tokens.TokenKind;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import java.nio.CharBuffer;

/**
 * Scanner that outputs suggested fixes generated by a {@code RefasterMatcher}.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
@AutoValue
abstract class RefasterScanner<M extends TemplateMatch, T extends Template<M>>
    extends TreeScanner<Void, Context> {
  static <M extends TemplateMatch, T extends Template<M>> RefasterScanner<M, T> create(
      RefasterRule<M, T> rule, DescriptionListener listener) {
    return new AutoValue_RefasterScanner<>(rule, listener);
  }

  abstract RefasterRule<M, T> rule();

  abstract DescriptionListener listener();

  @Override
  public Void visitClass(ClassTree node, Context context) {
    if (isSuppressed(node, context)) {
      return null;
    }
    Symbol sym = ASTHelpers.getSymbol(node);
    if (sym == null || !sym.getQualifiedName().contentEquals(rule().qualifiedTemplateClass())) {
      ListBuffer<JCStatement> statements = new ListBuffer<>();
      for (Tree tree : node.getMembers()) {
        if (tree instanceof JCStatement) {
          statements.append((JCStatement) tree);
        } else {
          tree.accept(this, context);
        }
      }
      scan(TreeMaker.instance(context).Block(0, statements.toList()), context);
    }
    return null;
  }

  @Override
  public Void visitMethod(MethodTree node, Context context) {
    if (isSuppressed(node, context)) {
      return null;
    }
    return super.visitMethod(node, context);
  }

  @Override
  public Void visitVariable(VariableTree node, Context context) {
    if (isSuppressed(node, context)) {
      return null;
    }
    return super.visitVariable(node, context);
  }

  @Override
  public Void scan(Tree tree, Context context) {
    if (tree == null) {
      return null;
    }
    JCCompilationUnit compilationUnit = context.get(JCCompilationUnit.class);
    for (T beforeTemplate : rule().beforeTemplates()) {
      matchLoop:
      for (M match : beforeTemplate.match((JCTree) tree, context)) {
        // Check here whether the match is indeed correct? If the annotation is present.
        // This would work if the annotation is on the method instead of parameter.
        //match.unifier.types().isConvertible()
        //rule().beforeTemplates().get(0).expressionArgumentTypes().get("b").inline(match.createInliner())
        // rule().afterTemplates().get(0).expressionArgumentTypes().get("b").inline(match.createInliner()) -> This gives a type.
        // ASTHelpers.getSymbol(((JCTree.JCMethodInvocation) tree).getArguments().get(0))
        // ((Symbol.MethodSymbol)ASTHelpers.getSymbol(((JCTree.JCMethodInvocation) tree).getArguments().get(0))).getThrownTypes()
        //match.unifier.types().isConvertible(rule().beforeTemplates().get(0).expressionArgumentTypes().get("b").inline(match.createInliner()), rule().afterTemplates().get(0).expressionArgumentTypes().get("b").inline(match.createInliner()) )
        if (beforeTemplate.annotations().containsKey(CanTransformToTargetType.class)) {
          List<Type> t = rule().afterTemplates().get(0).actualTypes(match.createInliner());
          ImmutableMap<String, UType> stringUTypeImmutableMap =
              rule().afterTemplates().get(0).expressionArgumentTypes();
          rule().afterTemplates().get(0).templateTypeVariables();
        }
        if (rule().rejectMatchesWithComments()) {
          String matchContents = match.getRange(compilationUnit);
          JavaTokenizer tokenizer =
              new JavaTokenizer(
                  ScannerFactory.instance(context), CharBuffer.wrap(matchContents)) {};
          for (Token token = tokenizer.readToken();
              token.kind != TokenKind.EOF;

              token = tokenizer.readToken()) {
            if (token.comments != null && !token.comments.isEmpty()) {
              continue matchLoop;
            }
          }
        }
        Description.Builder builder =
            Description.builder(
                match.getLocation(),
                rule().qualifiedTemplateClass(),
                "",
                SeverityLevel.WARNING,
                "");

        if (rule().afterTemplates().isEmpty()) {
          builder.addFix(SuggestedFix.prefixWith(match.getLocation(), "/* match found */ "));
        } else {
          for (T afterTemplate : rule().afterTemplates()) {
            builder.addFix(afterTemplate.replace(match));
          }
        }
        listener().onDescribed(builder.build());
      }
    }
    return super.scan(tree, context);
  }

  private static final SimpleTreeVisitor<Tree, Void> SKIP_PARENS =
      new SimpleTreeVisitor<Tree, Void>() {
        @Override
        public Tree visitParenthesized(ParenthesizedTree node, Void v) {
          return node.getExpression().accept(this, null);
        }

        @Override
        protected Tree defaultAction(Tree node, Void v) {
          return node;
        }
      };

  /*
   * Matching on the parentheses surrounding the condition of an if, while, or do-while
   * is nonsensical, as those parentheses are obligatory and should never be changed.
   */

  @Override
  public Void visitDoWhileLoop(DoWhileLoopTree node, Context context) {
    scan(node.getStatement(), context);
    scan(SKIP_PARENS.visit(node.getCondition(), null), context);
    return null;
  }

  @Override
  public Void visitWhileLoop(WhileLoopTree node, Context context) {
    scan(SKIP_PARENS.visit(node.getCondition(), null), context);
    scan(node.getStatement(), context);
    return null;
  }

  @Override
  public Void visitSynchronized(SynchronizedTree node, Context context) {
    scan(SKIP_PARENS.visit(node.getExpression(), null), context);
    scan(node.getBlock(), context);
    return null;
  }

  @Override
  public Void visitIf(IfTree node, Context context) {
    scan(SKIP_PARENS.visit(node.getCondition(), null), context);
    scan(node.getThenStatement(), context);
    scan(node.getElseStatement(), context);
    return null;
  }

  private boolean isSuppressed(Tree node, Context context) {
    return RefasterSuppressionHelper.suppressed(rule(), node, context);
  }
}
