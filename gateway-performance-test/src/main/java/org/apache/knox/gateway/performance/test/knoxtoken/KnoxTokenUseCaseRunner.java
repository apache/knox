/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.performance.test.knoxtoken;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.performance.test.ExecutorServiceUtils;
import org.apache.knox.gateway.performance.test.PerformanceTestConfiguration;
import org.apache.knox.gateway.performance.test.PerformanceTestLifeCyleListener;
import org.apache.knox.gateway.performance.test.PerformanceTestMessages;
import org.apache.knox.gateway.performance.test.ResponseTimeCache;
import org.apache.knox.gateway.performance.test.UseCaseRunner;

public class KnoxTokenUseCaseRunner implements UseCaseRunner {

  public static final String USE_CASE_NAME = "knoxtoken";

  private static final PerformanceTestMessages LOG = MessagesFactory.get(PerformanceTestMessages.class);
  private static final String PARAM_NUMBER_OF_THREADS = "numOfThreads";

  private final KnoxTokenCache knoxTokenCache = new KnoxTokenCache();
  private ResponseTimeCache responseTimeCache;
  private ExecutorService pool;

  @Override
  public void setResponseTimeCache(ResponseTimeCache responseTimeCache) {
    this.responseTimeCache = responseTimeCache;
  }

  @Override
  public void execute(PerformanceTestConfiguration configuration, List<PerformanceTestLifeCyleListener> lifeCyleListeners) {
    final ThreadFactory tfthreadFactory = new BasicThreadFactory.Builder().namingPattern("KnoxTokenUseCaseRunner-%d").build();
    final int numberOfThreads = Integer.parseInt(configuration.getUseCaseParam(getUseCaseName(), PARAM_NUMBER_OF_THREADS));
    LOG.runKnoxTokenWorkers(numberOfThreads);
    pool = Executors.newFixedThreadPool(numberOfThreads + 2, tfthreadFactory);
    try {
      for (int i = 0; i < numberOfThreads; i++) {
        pool.submit(new KnoxTokenWorkerThread(configuration, KnoxTokenAction.ACQUIRE, knoxTokenCache, responseTimeCache));
      }

      // 2 other jobs to renew and use qcquired Knox Tokens
      pool.submit(new KnoxTokenWorkerThread(configuration, KnoxTokenAction.RENEW, knoxTokenCache, responseTimeCache));
      pool.submit(new KnoxTokenWorkerThread(configuration, KnoxTokenAction.USE_TOKEN, knoxTokenCache, responseTimeCache));
    } catch (Exception ex) {
      ExecutorServiceUtils.shutdownAndAwaitTermination(pool, 10, TimeUnit.SECONDS);
    } finally {
      final long testDuration = Long.parseLong(configuration.getUseCaseParam(getUseCaseName(), KnoxTokenWorkerThread.PARAM_DURATION_IN_SECONDS));
      final long upperBound = Long.parseLong(configuration.getUseCaseParam(getUseCaseName(), KnoxTokenWorkerThread.PARAM_REQUEST_DELAY_UPPERBOUND));
      ExecutorServiceUtils.shutdownAndAwaitTermination(pool, testDuration + upperBound + 10, TimeUnit.SECONDS);
      lifeCyleListeners.forEach(lifeCyleListener -> lifeCyleListener.onFinish());
    }
  }

  @Override
  public String getUseCaseName() {
    return USE_CASE_NAME;
  }
}
