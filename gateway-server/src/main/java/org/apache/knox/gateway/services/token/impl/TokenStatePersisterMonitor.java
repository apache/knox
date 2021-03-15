/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.services.token.impl;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.util.ExecutorServiceUtils;

class TokenStatePersisterMonitor {
  private static final TokenStateServiceMessages log = MessagesFactory.get(TokenStateServiceMessages.class);
  private final ExecutorService monitor;
  private final ScheduledFuture<?> taskToMonitor;
  private final Set<TokenStatePeristerMonitorListener> listeners;

  TokenStatePersisterMonitor(ScheduledFuture<?> taskToMonitor, TokenStatePeristerMonitorListener listener) {
    this(taskToMonitor, Collections.singleton(listener));
  }

  TokenStatePersisterMonitor(ScheduledFuture<?> taskToMonitor, Set<TokenStatePeristerMonitorListener> listeners) {
    this.monitor = Executors.newSingleThreadExecutor(new BasicThreadFactory.Builder().namingPattern("TokenStatePeristerMonitor-%d").build());
    this.taskToMonitor = taskToMonitor;
    this.listeners = listeners;
  }

  void startMonitor() {
    monitor.submit(this::monitorPersisterTask);
  }

  private void monitorPersisterTask() {
    try {
      /*
       * This call doesn't return at each scheduled invocation but that it returns
       * - either for the last invocation of the task, that is a task cancellation caused by ScheduledFuture.cancel()
       * - or a exception thrown in the task.
       */
      taskToMonitor.get();
    } catch (CancellationException e) {
      log.cancelingTokenStateAliasePersisterTask();
    } catch (Throwable e) {
      log.errorRunningTokenStateAliasePersisterTask(e);
      listeners.forEach(listener -> listener.onTokenStatePeristerTaskError(e));
    } finally {
      ExecutorServiceUtils.shutdownAndAwaitTermination(monitor, 10, TimeUnit.SECONDS);
    }
  }

}
