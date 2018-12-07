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
package org.apache.knox.gateway.i18n.messages.loggers.test;

import org.apache.knox.gateway.i18n.messages.MessageLevel;

public class TestMessageRecord {

  private final TestMessageLogger logger;
  private final StackTraceElement caller;
  private final MessageLevel level;
  private final String id;
  private final String message;
  private final Throwable throwable;

  public TestMessageRecord( TestMessageLogger logger, StackTraceElement caller, MessageLevel level, String id, String message, Throwable throwable ) {
    this.logger = logger;
    this.caller = caller;
    this.level = level;
    this.id = id;
    this.message = message;
    this.throwable = throwable;
  }

  public TestMessageLogger getLogger() {
    return logger;
  }

  public StackTraceElement getCaller() {
    return caller;
  }

  public MessageLevel getLevel() {
    return level;
  }

  public String getId() {
    return id;
  }

  public String getMessage() {
    return message;
  }

  public Throwable getThrowable() {
    return throwable;
  }

}
