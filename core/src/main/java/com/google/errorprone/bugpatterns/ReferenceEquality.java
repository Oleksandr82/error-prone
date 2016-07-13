/*
 * Copyright 2015 Google Inc. All Rights Reserved.
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

package com.google.errorprone.bugpatterns;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.errorprone.BugPattern.Category.JDK;
import static com.google.errorprone.BugPattern.MaturityLevel.MATURE;
import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Scope.WriteableScope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.util.Name;

/** @author cushon@google.com (Liam Miller-Cushon) */
@BugPattern(
  name = "ReferenceEquality",
  summary = "Comparison using reference equality instead of value equality",
  category = JDK,
  severity = WARNING,
  maturity = MATURE
)
public class ReferenceEquality extends AbstractReferenceEquality {

  @Override
  protected boolean matchArgument(ExpressionTree tree, VisitorState state) {
    Type type = ASTHelpers.getType(tree);
    if (!type.isReference()) {
      return false;
    }
    ClassTree classTree = ASTHelpers.findEnclosingNode(state.getPath(), ClassTree.class);
    if (classTree == null) {
      return false;
    }
    Type classType = ASTHelpers.getType(classTree);
    if (classType == null) {
      return false;
    }
    if (inEqualsImplementation(classType, type, state)) {
      return false;
    }
    if (ASTHelpers.isSubtype(type, state.getSymtab().enumSym.type, state)) {
      return false;
    }
    if (ASTHelpers.isSubtype(type, state.getSymtab().classType, state)) {
      return false;
    }
    if (!implementsEquals(type, state)) {
      return false;
    }
    return true;
  }

  private boolean inEqualsImplementation(Type classType, Type type, VisitorState state) {
    MethodTree methodTree = ASTHelpers.findEnclosingNode(state.getPath(), MethodTree.class);
    if (methodTree == null) {
      return false;
    }
    MethodSymbol sym = ASTHelpers.getSymbol(methodTree);
    if (sym == null || sym.isStatic() || !sym.toString().equals("equals(java.lang.Object)")) {
      return false;
    }
    if (!ASTHelpers.isSameType(type, classType, state)) {
      return false;
    }
    return true;
  }

  /** Check if the method declares or inherits an implementation of .equals() */
  public static boolean implementsEquals(Type type, VisitorState state) {
    Name equalsName = state.getName("equals");
    Type objectType = state.getSymtab().objectType;
    Symbol objectEquals = getOnlyElement(objectType.tsym.members().getSymbolsByName(equalsName));
    for (Type sup : state.getTypes().closure(type)) {
      if (sup.tsym.isInterface()) {
        continue;
      }
      if (ASTHelpers.isSameType(sup, objectType, state)) {
        return false;
      }
      WriteableScope scope = sup.tsym.members();
      if (scope == null) {
        continue;
      }
      for (Symbol sym : scope.getSymbolsByName(equalsName)) {
        if (sym.overrides(objectEquals, type.tsym, state.getTypes(), false)) {
          return true;
        }
      }
    }
    return false;
  }
}

