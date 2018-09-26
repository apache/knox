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
package org.apache.knox.gateway.i18n.messages.loggers.jdk;

import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.MessageLogger;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 *
 */
final class JdkMessageLogger implements MessageLogger {

  private final Logger logger;

  JdkMessageLogger( final Logger logger ) {
    this.logger = logger;
  }

  @Override
  public final boolean isLoggable( final MessageLevel level ) {
    return logger.isLoggable( toLevel( level ) );
  }

  //TODO: Handle message ID.
  @Override
  public final void log( final StackTraceElement caller, final MessageLevel level, final String id, final String message, final Throwable thrown ) {
    LogRecord record = new LogRecord( toLevel( level ), message );
    record.setSourceClassName( caller.getClassName() );
    record.setSourceMethodName( caller.getMethodName() );
    if( thrown != null ) {
      record.setThrown( thrown );
    }
    logger.log( record );
  }

  private static final Level toLevel( final MessageLevel level ) {
    switch( level ) {
      case FATAL: return Level.SEVERE;
      case ERROR: return Level.SEVERE;
      case WARN: return Level.WARNING;
      case INFO: return Level.INFO;
      case DEBUG: return Level.FINE;
      case TRACE: return Level.FINEST;
      default: return Level.OFF;
    }
  }

}
