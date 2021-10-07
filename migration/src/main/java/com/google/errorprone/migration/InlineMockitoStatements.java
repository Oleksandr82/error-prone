/*
 * Copyright 2021 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.migration;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import static com.google.errorprone.matchers.Matchers.instanceMethod;
import static com.google.errorprone.matchers.Matchers.staticMethod;
import static com.google.errorprone.util.ASTHelpers.getSymbol;
import static com.google.errorprone.util.ASTHelpers.hasAnnotation;
import static com.google.errorprone.util.MoreAnnotations.asStringValue;
import static com.google.errorprone.util.MoreAnnotations.getValue;
import static com.sun.tools.javac.code.Symbol.MethodSymbol;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.refaster.CouldNotResolveImportException;
import com.google.errorprone.refaster.Inliner;
import com.google.errorprone.refaster.UType;
import com.google.errorprone.refaster.Unifier;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// XXX: Known limitation: methodInvocation in the `thenReturn` is of type Answer<T>, which is hard
// to convert to the correct type and therefore out of scope.
@AutoService(BugChecker.class)
@BugPattern(
    name = "InlineMockitoStatements",
    summary = "Migrate Mockito statements that call a method annotated with `@InlineMe`.",
    severity = WARNING)
public class InlineMockitoStatements extends BugChecker implements MethodInvocationTreeMatcher {

  private static final String INLINE_ME = "com.google.errorprone.annotations.InlineMe";

  private static final Matcher<ExpressionTree> MOCKITO_MATCHER_WHEN =
      staticMethod().onClass("org.mockito.Mockito").named("when");

  private static final Matcher<ExpressionTree> MOCKITO_MATCHER_DO_WHEN =
      instanceMethod().onDescendantOf("org.mockito.stubbing.Stubber").named("when");

  private static final Matcher<ExpressionTree> MOCKITO_VERIFY =
      staticMethod().onClass("org.mockito.Mockito").named("verify");

  private static final Matcher<ExpressionTree> THEN_RETURN =
      instanceMethod().anyClass().named("thenReturn");

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    ImmutableList<MigrationCodeTransformer> migrationDefinitions =
        MigrationTransformersProvider.MIGRATION_TRANSFORMATIONS.get();

    TreePath grandParentPath = state.getPath().getParentPath().getParentPath();
    Tree grandParent = grandParentPath.getLeaf();
    if (MOCKITO_MATCHER_WHEN.matches(tree, state)) {
      if (grandParent instanceof BlockTree) {
        // The `when` does not contain a `thenReturn` or `thenAnswer`.
        return Description.NO_MATCH;
      }

      ImmutableList<? extends ExpressionTree> thenReturnArguments =
          getThenReturnArguments(grandParentPath, state);
      ExpressionTree whenArgument = Iterables.getOnlyElement(tree.getArguments());
      Symbol whenSymbol = getSymbol(whenArgument);

      boolean methodAlreadyMigrated = isMethodAlreadyMigrated(whenSymbol, state);

      if (methodWithoutInlineOrMigratedReplacement(whenSymbol, state)
          || !(whenArgument instanceof MethodInvocationTree)
          || methodAlreadyMigrated
          || isOfTypeMockitoStubbingAnswer(thenReturnArguments, state)) {
        return Description.NO_MATCH;
      }

      Optional<MigrationCodeTransformer> suitableMigration =
          getSuitableMigrationForMethod(whenSymbol, migrationDefinitions, state);
      if (!suitableMigration.isPresent()) {
        return Description.NO_MATCH;
      }

      return getDescriptionForMigratedMethod(
          tree,
          whenArgument,
          whenSymbol,
          getDescriptionsForArguments(state, suitableMigration.get(), thenReturnArguments),
          state);
    } else if (MOCKITO_MATCHER_DO_WHEN.matches(tree, state)) {
      if (methodWithoutInlineOrMigratedReplacement(ASTHelpers.getSymbol(grandParent), state)) {
        return Description.NO_MATCH;
      }

      Tree parent = state.getPath().getParentPath().getLeaf();
      Symbol whenSymbol = getSymbol(parent);

      boolean methodAlreadyMigrated = isMethodAlreadyMigrated(whenSymbol, state);

      Optional<MigrationCodeTransformer> suitableMigration =
          getSuitableMigrationForMethod(whenSymbol, migrationDefinitions, state);
      if (!suitableMigration.isPresent() || methodAlreadyMigrated) {
        return Description.NO_MATCH;
      }

      List<? extends ExpressionTree> arguments =
          ((MethodInvocationTree) ((MemberSelectTree) tree.getMethodSelect()).getExpression())
              .getArguments();

      return getDescriptionForMigratedMethod(
          tree,
          grandParent,
          whenSymbol,
          getDescriptionsForArguments(state, suitableMigration.get(), arguments),
          state);
    } else if (MOCKITO_VERIFY.matches(tree, state)) {
      Symbol symbol = getSymbol(grandParent);
      if (methodWithoutInlineOrMigratedReplacement(symbol, state)) {
        return Description.NO_MATCH;
      }
      return getDescriptionForMigratedMethod(tree, grandParent, symbol, new ArrayList<>(), state);
    }
    return Description.NO_MATCH;
  }

  /**
   * There is no easy to check to see how many `thenReturn` statements are in the `when` statement.
   * This method returns all the arguments of the adjacent `thenReturn` statements.
   */
  private ImmutableList<? extends ExpressionTree> getThenReturnArguments(
      TreePath grandParentPath, VisitorState state) {
    List<MethodInvocationTree> thenReturns = new ArrayList<>();
    TreePath currentPath = grandParentPath;
    do {
      thenReturns.add(((MethodInvocationTree) currentPath.getLeaf()));
      currentPath = currentPath.getParentPath().getParentPath();
    } while (currentPath.getLeaf() instanceof ExpressionTree
        && THEN_RETURN.matches((ExpressionTree) currentPath.getLeaf(), state));

    return thenReturns.stream()
        .map(MethodInvocationTree::getArguments)
        .flatMap(Collection::stream)
        .collect(toImmutableList());
  }

  /**
   * If one of the thenArguments is a MethodInvocation that returns a Answer<T>, the current way of
   * inlining won't work. Therefore, exclude these rewrites.
   */
  private boolean isOfTypeMockitoStubbingAnswer(
      List<? extends ExpressionTree> thenReturnArguments, VisitorState state) {
    Type mockitoStubbingAnswerType = state.getTypeFromString("org.mockito.stubbing.Answer");
    return thenReturnArguments.stream()
        .anyMatch(
            arg ->
                arg instanceof MethodInvocationTree
                    && ASTHelpers.isSubtype(
                        ASTHelpers.getType(arg), mockitoStubbingAnswerType, state));
  }

  private boolean isMethodAlreadyMigrated(Symbol whenSymbol, VisitorState state) {
    TreePath pathToEnclosingMethod = state.findPathToEnclosing(MethodTree.class);
    DoesPathContainMemberSelect scan =
        new DoesPathContainMemberSelect(whenSymbol.getQualifiedName() + "_migrated");
    if (pathToEnclosingMethod == null) {
      return false;
    }
    scan.scan(pathToEnclosingMethod, null);
    return scan.isAlreadyMigrated();
  }

  /** AST Visitor that matches identifiers in a Tree */
  private static class DoesPathContainMemberSelect extends TreePathScanner<Boolean, Void> {
    private boolean isMigrated = false;
    private final String name;

    public boolean isAlreadyMigrated() {
      return isMigrated;
    }

    public DoesPathContainMemberSelect(String s) {
      name = s;
    }

    @Override
    public Boolean visitMemberSelect(MemberSelectTree node, Void unused) {
      if (node.getIdentifier().contentEquals(name)) {
        isMigrated = true;
        return null;
      } else {
        return super.visitMemberSelect(node, unused);
      }
    }
  }

  private Description getDescriptionForMigratedMethod(
      MethodInvocationTree originalTree,
      Tree grandParent,
      Symbol whenSymbol,
      List<Description> descriptions,
      VisitorState state) {
    SuggestedFix.Builder fix = SuggestedFix.builder();
    descriptions.add(
        describeMatch(
            grandParent,
            SuggestedFixes.renameMethodInvocation(
                (MethodInvocationTree) grandParent,
                whenSymbol.getQualifiedName() + "_migrated",
                state)));
    descriptions.forEach(
        d -> {
          if (!d.fixes.isEmpty()) {
            fix.merge((SuggestedFix) getOnlyElement(d.fixes));
          }
        });

    return describeMatch(originalTree, fix.build());
  }

  private List<Description> getDescriptionsForArguments(
      VisitorState state,
      MigrationCodeTransformer suitableMigration,
      List<? extends ExpressionTree> arguments) {
    return arguments.stream()
        .map(e -> getMigrationReplacementForNormalMethod(e, suitableMigration, state))
        .collect(Collectors.toList());
  }

  private Optional<MigrationCodeTransformer> getSuitableMigrationForMethod(
      Symbol whenSymbol,
      ImmutableList<MigrationCodeTransformer> migrationDefinitions,
      VisitorState state) {
    MethodSymbol whenMethodSymbol = (MethodSymbol) whenSymbol;
    Inliner inliner = new Unifier(state.context).createInliner();
    return migrationDefinitions.stream()
        .filter(
            migration ->
                TypeMigrationHelper.isMethodTypeUndesiredMigrationType(
                    inliner.types(),
                    whenMethodSymbol.getReturnType(),
                    inlineType(inliner, migration.typeFrom()),
                    state))
        .findFirst();
  }

  private boolean methodWithoutInlineOrMigratedReplacement(Symbol whenSymbol, VisitorState state) {
    if (!hasAnnotation(whenSymbol, INLINE_ME, state)) {
      return true;
    }
    Attribute.Compound inlineMe =
        whenSymbol.getRawAttributes().stream()
            .filter(a -> a.type.tsym.getQualifiedName().contentEquals(INLINE_ME))
            .collect(onlyElement());
    String replacement = asStringValue(getValue(inlineMe, "replacement").get()).get();

    return !replacement.contains("_migrated");
  }

  private static Type inlineType(Inliner inliner, UType uType) {
    // XXX: Explain new `Inliner` creation.
    try {
      return uType.inline(new Inliner(inliner.getContext(), inliner.bindings));
    } catch (CouldNotResolveImportException e) {
      throw new IllegalStateException("Couldn't inline UType" + uType.getClass() + ";" + uType, e);
    }
  }

  private Description getMigrationReplacementForNormalMethod(
      Tree tree, MigrationCodeTransformer migrationCodeTransformer, VisitorState state) {
    JCTree.JCCompilationUnit compilationUnit =
        (JCTree.JCCompilationUnit) state.getPath().getCompilationUnit();
    TreePath compUnitTreePath = new TreePath(compilationUnit);
    TreePath methodPath = new TreePath(compUnitTreePath, tree);

    java.util.List<Description> matches = new ArrayList<>();
    migrationCodeTransformer.transformFrom().apply(methodPath, state.context, matches::add);

    return describeMatch(
        tree, MatchesSolver.collectNonOverlappingFixes(matches, compilationUnit.endPositions));
  }
}
