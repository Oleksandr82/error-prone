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

package com.google.errorprone.bugpatterns;

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link IdentityConversion} */
@RunWith(JUnit4.class)
public class IdentityConversionTest {
  private final BugCheckerRefactoringTestHelper helper =
      BugCheckerRefactoringTestHelper.newInstance(IdentityConversion.class, getClass());

  // XXX: Add test cases for: `Char`, `Double`, `Float`.
  @Test
  public void removeValueOf() {
    helper
        .addInputLines(
            "Foo.java",
            "public final class Foo {",
            "  public void foo() {",
            "    Byte b1 = Byte.valueOf((Byte) Byte.MIN_VALUE);",
            "    Byte b2 = Byte.valueOf(\"1\");",
            "",
            "    Integer i1 = Integer.valueOf((Integer)0);",
            "    Integer i2 = Integer.valueOf(1);",
            "",
            "    String s1 = String.valueOf(0);",
            "    String s2 = String.valueOf(\"1\");",
            "  }",
            "}")
        .addOutputLines(
            "Foo.java",
            "public final class Foo {",
            "  public void foo() {",
            "    Byte b1 = (Byte) Byte.MIN_VALUE;",
            "    Byte b2 = Byte.valueOf(\"1\");",
            "",
            "    Integer i1 = (Integer)0;",
            "    Integer i2 = Integer.valueOf(1);",
            "",
            "    String s1 = String.valueOf(0);",
            "    String s2 = \"1\";",
            "  }",
            "}")
        .doTest();
  }
}
