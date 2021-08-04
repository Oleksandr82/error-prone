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

package com.google.errorprone.migration.templates;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.MigrationTemplate;
import io.reactivex.Maybe;
import reactor.adapter.rxjava.RxJava2Adapter;
import reactor.core.publisher.Mono;

public final class MaybeNumberToMonoNumberMigrationTemplate {
  private MaybeNumberToMonoNumberMigrationTemplate() {}

  static final class MaybeNumberToMonoNumber {
    @MigrationTemplate(value = false)
    static final class MigrateMaybeNumberToMonoNumber<T extends Number> {
      @BeforeTemplate
      Maybe<T> before(Maybe<T> maybe) {
        return maybe;
      }

      @AfterTemplate
      Mono<T> after(Maybe<T> maybe) {
        return maybe.as(RxJava2Adapter::maybeToMono);
      }
    }

    @MigrationTemplate(value = true)
    static final class MigrateMonoNumberToMaybeNumber<T extends Number> {
      @BeforeTemplate
      Mono<T> before(Mono<T> mono) {
        return mono;
      }

      @AfterTemplate
      Maybe<T> after(Mono<T> mono) {
        return mono.as(RxJava2Adapter::monoToMaybe);
      }
    }
  }
}
