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


import static org.apache.knox.gateway.performance.test.knoxtoken.KnoxTokenUseCaseRunner.USE_CASE_NAME;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.performance.test.PerformanceTestConfiguration;
import org.apache.knox.gateway.performance.test.PerformanceTestMessages;
import org.apache.knox.gateway.performance.test.ResponseTimeCache;
import org.apache.knox.gateway.shell.ErrorResponse;
import org.apache.knox.gateway.shell.KnoxSession;
import org.apache.knox.gateway.shell.KnoxShellException;
import org.apache.knox.gateway.shell.hdfs.Hdfs;
import org.apache.knox.gateway.shell.knox.token.Get;
import org.apache.knox.gateway.shell.knox.token.Token;
import org.apache.knox.gateway.shell.knox.token.TokenLifecycleResponse;
import org.apache.knox.gateway.util.JsonUtils;
import org.apache.knox.gateway.util.Tokens;

@SuppressWarnings("PMD.DoNotUseThreads")
public class KnoxTokenWorkerThread implements Runnable {
  private static final PerformanceTestMessages LOG = MessagesFactory.get(PerformanceTestMessages.class);
  static final String PARAM_DURATION_IN_SECONDS = "testDurationInSecs";
  private static final String PARAM_REQUEST_DELAY_LOWERBOUND = "requestDelayLowerBoundInSecs";
  static final String PARAM_REQUEST_DELAY_UPPERBOUND = "requestDelayUpperBoundInSecs";

  private final PerformanceTestConfiguration configuration;
  private final KnoxTokenAction action;
  private final KnoxTokenCache knoxTokenCache;
  private final ResponseTimeCache responseTimeCache;
  private Instant startTime;

  public KnoxTokenWorkerThread(PerformanceTestConfiguration configuration, KnoxTokenAction action, KnoxTokenCache knoxTokenCache, ResponseTimeCache responseTimeCache) {
    this.configuration = configuration;
    this.action = action;
    this.knoxTokenCache = knoxTokenCache;
    this.responseTimeCache = responseTimeCache;
  }

  @Override
  public void run() {
    try {
      LOG.knoxTokenWorkerName(action);
      final KnoxSession gatewayKnoxSession = KnoxSession.login(configuration.getUseCaseUrl(USE_CASE_NAME, "gateway"), configuration.getGatewayUser(),
          configuration.getGatewayPassword());
      final KnoxSession tokenBasedKnoxSession = KnoxSession.login(configuration.getUseCaseUrl(USE_CASE_NAME, "tokenbased"), configuration.getGatewayUser(),
          configuration.getGatewayPassword());
      final long testDuration = Long.parseLong(configuration.getUseCaseParam(USE_CASE_NAME, PARAM_DURATION_IN_SECONDS));
      final long lowerBound = Long.parseLong(configuration.getUseCaseParam(USE_CASE_NAME, PARAM_REQUEST_DELAY_LOWERBOUND));
      final long upperBound = Long.parseLong(configuration.getUseCaseParam(USE_CASE_NAME, PARAM_REQUEST_DELAY_UPPERBOUND));
      this.startTime = Instant.now();
      int requestCount = 0;
      while (shouldRun(testDuration)) {
        executeAction(gatewayKnoxSession, tokenBasedKnoxSession);

        if (requestCount > 0) {
          TimeUnit.SECONDS.sleep(calculateSleepTime(lowerBound, upperBound));
        }
        requestCount++;
      }
    } catch (Exception e) {
      LOG.failedToRunKnoxTokenWorker(action, e.getMessage(), e);
    } finally {
      LOG.finishKnoxTokenWorker();
    }
  }

  private void executeAction(final KnoxSession gatewayKnoxSession, final KnoxSession tokenBasedKnoxSession) {
    try {
      switch (this.action) {
      case ACQUIRE:
        knoxTokenCache.saveKnoxToken(acquireKnoxToken(gatewayKnoxSession));
        break;
      case RENEW:
        renewKnoxToken(gatewayKnoxSession);
        break;
      case USE_TOKEN:
        useKnoxToken(tokenBasedKnoxSession);
        break;
      default:
        // NOP
        break;
      }
    } catch (Exception e) {
      LOG.failedToExecuteKnoxTokenAction(action, e.getMessage(), e);
    }
  }

  private boolean shouldRun(long testDuration) {
    final long timeElapsed = Duration.between(startTime, Instant.now()).getSeconds();
    final boolean shouldRun = timeElapsed < testDuration;
    return shouldRun;
  }

  private long calculateSleepTime(long lowerBound, long upperBound) {
    final long sleepTime = ThreadLocalRandom.current().nextLong(KnoxTokenAction.ACQUIRE == this.action ? lowerBound : lowerBound * 2,
        KnoxTokenAction.ACQUIRE == this.action ? upperBound : upperBound * 2);
    LOG.sleep(sleepTime);
    return sleepTime;
  }

  private String acquireKnoxToken(KnoxSession knoxSession) throws IOException {
    LOG.acquireKnoxToken();
    long getStart = System.currentTimeMillis();
    final Get.Response getTokenResponse = Token.get(knoxSession).now();
    final long getResponseTime = System.currentTimeMillis() - getStart;
    LOG.acquiredKnoxToken();
    responseTimeCache.saveAcquireResponseTime(getResponseTime);
    final Map<String, String> tokenAsMap = JsonUtils.getMapFromJsonString(getTokenResponse.getString());
    return tokenAsMap.get("access_token");
  }

  private void renewKnoxToken(KnoxSession knoxSession) throws Exception {
    final String knoxTokenToRenew = this.knoxTokenCache.getKnoxToken();
    if (knoxTokenToRenew != null) {
      LOG.renewKnoxToken(Tokens.getTokenDisplayText(knoxTokenToRenew));
      final long renewStart = System.currentTimeMillis();
      final TokenLifecycleResponse renewResponse = Token.renew(knoxSession, knoxTokenToRenew).now();
      final long renewResponseTime = System.currentTimeMillis() - renewStart;
      responseTimeCache.saveRenewResponseTime(renewResponseTime);
      final Map<String, String> map = JsonUtils.getMapFromJsonString(renewResponse.getString());
      boolean renewed = Boolean.parseBoolean(map.get("renewed"));
      if (renewed) {
        LOG.renewedKnoxToken(Instant.ofEpochMilli(Long.parseLong(map.get("expires"))).toString());
      } else {
        LOG.failedToRenewKnoxToken(map.get("error"));
      }
    } else {
      LOG.nothingToRenew();
    }
  }

  private void useKnoxToken(KnoxSession knoxSession) {
    try {
      final String knoxToken = this.knoxTokenCache.getKnoxToken();
      if (knoxToken != null) {
        LOG.useKnoxToken(Tokens.getTokenDisplayText(knoxToken));
        Hdfs.ls(knoxSession).knoxToken(knoxToken).now();
      } else {
        LOG.nothingToUse();
      }
    } catch (KnoxShellException e) {
      final ErrorResponse errorResponse = (ErrorResponse) e.getCause();
      final int errorCode = errorResponse.getResponse().getStatusLine().getStatusCode();
      // if unauthorized -> it's a failure (every other error code is irrelevant here)
      if (HttpServletResponse.SC_UNAUTHORIZED == errorCode) {
        LOG.failedToUseKnoxToken(e.getMessage());
      }
    }
  }

}
