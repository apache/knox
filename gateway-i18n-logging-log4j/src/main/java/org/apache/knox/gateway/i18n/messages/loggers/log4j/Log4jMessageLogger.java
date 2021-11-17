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
package org.apache.knox.gateway.i18n.messages.loggers.log4j;

import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.MessageLogger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.SimpleMessage;

public class Log4jMessageLogger implements MessageLogger {
  private static String CLASS_NAME = Log4jMessageLogger.class.getName();
  private Logger logger;

  Log4jMessageLogger( Logger logger ) {
    this.logger = logger;
  }

  @Override
  public final boolean isLoggable( final MessageLevel level ) {
    return logger.isEnabled( toLevel( level ) );
  }

  @Override
  public final void log( final StackTraceElement caller, final MessageLevel messageLevel, final String messageId, final String messageText, final Throwable thrown ) {
    logger.logMessage(toLevel(messageLevel), null, CLASS_NAME, caller, new SimpleMessage(messageText), thrown);
  }

  private static Level toLevel(final MessageLevel level ) {
    switch( level ) {
      case FATAL: return Level.FATAL;
      case ERROR: return Level.ERROR;
      case WARN: return Level.WARN;
      case INFO: return Level.INFO;
      case DEBUG: return Level.DEBUG;
      case TRACE: return Level.TRACE;
      default: return Level.OFF;
    }
  }

}
