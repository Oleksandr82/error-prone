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
import io.reactivex.Single;
import reactor.adapter.rxjava.RxJava2Adapter;
import reactor.core.publisher.Mono;

public final class SingleToMonoMigrationTemplate {
  private SingleToMonoMigrationTemplate() {}

  static final class SingleToMono {
    @MigrationTemplate(value = false)
    static final class MigrateSingleToMono<T> {
      @BeforeTemplate
      Single<T> before(@Matches(IsParentReturnTree.class) Single<T> single) {
        return single;
      }

      @AfterTemplate
      Mono<T> after(Single<T> single) {
        return RxJava2Adapter.singleToMono(single);
      }
    }

    @MigrationTemplate(value = true)
    static final class MigrateMonoToSingle<T> {
      @BeforeTemplate
      Mono<T> before(@Matches(IsParentReturnTree.class) Mono<T> single) {
        return single;
      }

      @AfterTemplate
      Single<T> after(Mono<T> single) {
        return RxJava2Adapter.monoToSingle(single);
      }
    }
  }
}