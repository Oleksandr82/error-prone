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
import com.google.errorprone.scanner.Scanner;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.google.errorprone.matchers.Matchers.throwsException;

@RunWith(JUnit4.class)
public class MethodThrowsTest extends CompilerBasedAbstractTest {
  final List<ScannerTest> tests = new ArrayList<>();

  @After
  public void tearDown() {
    for (ScannerTest test : tests) {
      test.assertDone();
    }
  }

  @Test
  public void noThrowDoesntMatch() {
    writeFile(
        "A.java",
        "class A {",
        "@FunctionalInterface",
        "interface Foo<I, O> {",
        "   O fun(I input) throws Exception;",
        "}",
        "",
        "void receiver(Foo fun) {",
        "   receiver(this::fun1);",
        "}",
        "",
        "private String fun1(Object o) {",
        "   return o.toString();",
        "}",
        "",
        "}");
    assertCompiles(
        methodThrowsException(/* shouldMatch= */ false, throwsException("java.lang.Exception")));
  }

  @Test
  public void shouldMatchThrows() {
    writeFile(
        "A.java",
        "class A {",
        "@FunctionalInterface",
        "interface Foo<I, O> {",
        "   O fun(I input) throws Exception;",
        "}",
        "",
        "void receiver(Foo fun) {",
        "   receiver(this::fun2);",
        "}",
        "",
        "private String fun2(Object o) throws Exception {",
        "   return o.toString();",
        "}",
        "",
        "}");

    assertCompiles(
        methodThrowsException(/* shouldMatch= */ true, throwsException("java.lang.Exception")));
  }

  @Test
  public void wrongExceptionDoesntMatch() {
    writeFile(
        "A.java",
        "class A {",
        "@FunctionalInterface",
        "interface Foo<I, O> {",
        "   O fun(I input) throws Exception;",
        "}",
        "",
        "void receiver(Foo fun) {",
        "   receiver(this::fun2);",
        "}",
        "",
        "private String fun2(Object o) throws Exception {",
        "   return o.toString();",
        "}",
        "",
        "}");

    assertCompiles(
        methodThrowsException(
            /* shouldMatch= */ false, throwsException("java.lang.IllegalStateException")));
  }

  private abstract static class ScannerTest extends Scanner {
    abstract void assertDone();
  }

  private Scanner methodThrowsException(
      final boolean shouldMatch, final Matcher<ExpressionTree> toMatch) {
    ScannerTest test =
        new ScannerTest() {
          private boolean matched = false;

          @Override
          public Void visitMemberReference(MemberReferenceTree node, VisitorState visitorState) {
            visitorState = visitorState.withPath(getCurrentPath());
            if (toMatch.matches(node, visitorState)) {
              matched = true;
            }

            return super.visitMemberReference(node, visitorState);
          }

          @Override
          public void assertDone() {
            assertThat(shouldMatch).isEqualTo(matched);
          }
        };
    tests.add(test);
    return test;
  }
}