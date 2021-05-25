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

package com.google.errorprone.matchers;

import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.tools.javac.code.Symbol;

public abstract class MethodThrows implements Matcher<ExpressionTree> {

  private final String throwsException;

  public MethodThrows(String throwsException) {
    this.throwsException = throwsException;
  }

  public MethodThrows() {
    throwsException = "java.lang.Exception";
  }

  @Override
  public boolean matches(ExpressionTree expressionTree, VisitorState state) {
    if (!(expressionTree instanceof MemberReferenceTree)) {
      return false;
    }
    Symbol.MethodSymbol symbol = ASTHelpers.getSymbol((MemberReferenceTree) expressionTree);
    if (symbol == null) {
      return false;
    }
    return symbol.getThrownTypes().stream()
        .anyMatch(type -> type.tsym.toString().equals(throwsException));
  }
}