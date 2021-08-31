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
package org.apache.knox.gateway.performance.test;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;
import org.apache.knox.gateway.performance.test.knoxtoken.KnoxTokenAction;

@Messages(logger = "org.apache.knox.gateway.performance.test")
public interface PerformanceTestMessages {

  @Message(level = MessageLevel.INFO, text = "Running Knox Token Workers on {0} threads ...")
  void runKnoxTokenWorkers(int numOfThreads);

  @Message(level = MessageLevel.INFO, text = "I am a Knox Token {0} thread")
  void knoxTokenWorkerName(KnoxTokenAction action);

  @Message(level = MessageLevel.INFO, text = "Knox token worker thread finished")
  void finishKnoxTokenWorker();

  @Message(level = MessageLevel.ERROR, text = "Failed to run {0} Knox token worker: {1}")
  void failedToRunKnoxTokenWorker(KnoxTokenAction action, String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR, text = "Failed to execute {0} Knox token action: {1}")
  void failedToExecuteKnoxTokenAction(KnoxTokenAction action, String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.INFO, text = "Sleeping {0} seconds...")
  void sleep(long sleepTime);

  @Message(level = MessageLevel.INFO, text = "Acquiring Knox token...")
  void acquireKnoxToken();

  @Message(level = MessageLevel.INFO, text = "Acquired Knox token")
  void acquiredKnoxToken();

  @Message(level = MessageLevel.INFO, text = "Renewing Knox token {0}")
  void renewKnoxToken(String knoxToken);

  @Message(level = MessageLevel.INFO, text = "Renewed Knox token: {0}")
  void renewedKnoxToken(String expiration);

  @Message(level = MessageLevel.INFO, text = "Failed to renew Knox token: {0}")
  void failedToRenewKnoxToken(String error);

  @Message(level = MessageLevel.INFO, text = "There is no token to be renewed yet")
  void nothingToRenew();

  @Message(level = MessageLevel.INFO, text = "Using Knox token {0}")
  void useKnoxToken(String knoxToken);

  @Message(level = MessageLevel.INFO, text = "Failed to use Knox token: {0}")
  void failedToUseKnoxToken(String error);

  @Message(level = MessageLevel.INFO, text = "There is no token to be used yet")
  void nothingToUse();

  @Message(level = MessageLevel.ERROR, text = "Error while generating {0} report: {1}")
  void failedToGenerateReport(String reportType, String errorMessage, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.INFO, text = "Metrics reporter is shut down")
  void shutDownMetricsReporter();
}
