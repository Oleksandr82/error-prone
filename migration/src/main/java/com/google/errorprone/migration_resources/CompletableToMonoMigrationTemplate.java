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

package com.google.errorprone.migration_resources;

import com.google.errorprone.matchers.IsParentReturnTree;
import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.Matches;
import com.google.errorprone.refaster.annotation.MigrationTemplate;
import io.reactivex.Completable;
import reactor.adapter.rxjava.RxJava2Adapter;
import reactor.core.publisher.Mono;

public class CompletableToMonoMigrationTemplate {
  static final class CompletableToMono {
    @MigrationTemplate(value = false)
    static final class MigrateCompletableToMono {
      @BeforeTemplate
      Completable before(@Matches(IsParentReturnTree.class) Completable completable) {
        return completable;
      }

      @AfterTemplate
      Mono<Void> after(Completable completable) {
        return RxJava2Adapter.completableToMono(completable);
      }
    }

    @MigrationTemplate(value = true)
    static final class MigrateMonoToCompletable {
      @BeforeTemplate
      Mono<Void> before(@Matches(IsParentReturnTree.class) Mono<Void> mono) {
        return mono;
      }

      @AfterTemplate
      Completable after(Mono<Void> mono) {
        return RxJava2Adapter.monoToCompletable(mono);
      }
    }
  }
}