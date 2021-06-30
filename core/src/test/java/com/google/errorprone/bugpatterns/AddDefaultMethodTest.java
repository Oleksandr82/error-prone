/*
 * Copyright 2015 The Error Prone Authors.
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

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** {@link AddDefaultMethod}Test */
@RunWith(JUnit4.class)
public class AddDefaultMethodTest {
  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(AddDefaultMethod.class, getClass());

  private static final String[] FOO_INTERFACE = {
    "interface Foo {", "  String foo();", "}",
  };

  @Test
  public void negative_thenReturn() {
    compilationHelper
        .addSourceLines("Foo.java", FOO_INTERFACE)
        .addSourceLines(
            "Test.java",
            "class Test {",
            "  String test() {",
            "    return \"2\";",
            "  }",
            "}")
        .doTest();
  }
}