package com.google.errorprone.migration;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.MigrationTemplate;

public final class FirstMigrationTemplate {
  private FirstMigrationTemplate() {}

  @MigrationTemplate(value = false, from = String.class, to = Integer.class)
  static final class MigrateStringToInteger {
    @BeforeTemplate
    String before(String s) {
      return s;
    }

    @AfterTemplate
    Integer after(String s) {
      return Integer.valueOf(s);
    }
  }

  @MigrationTemplate(value = true, from = Integer.class, to = String.class)
  static final class MigrateIntegerToString {
    @BeforeTemplate
    Integer before(Integer s) {
      return s;
    }

    @AfterTemplate
    String after(Integer s) {
      return String.valueOf(s);
    }
  }
}