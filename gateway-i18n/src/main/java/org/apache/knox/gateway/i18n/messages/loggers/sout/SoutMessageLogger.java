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
package org.apache.knox.gateway.i18n.messages.loggers.sout;

import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.MessageLogger;

import java.text.MessageFormat;
import java.util.Locale;

public class SoutMessageLogger implements MessageLogger {

  private static final String FORMAT_WITHOUT_ID = "{0}: {2}";
  private static final String FORMAT_WITH_ID = "{0}: {2} [{1}]";

  @Override
  public boolean isLoggable( MessageLevel level ) {
    return true;
  }

  private static String getFormat( final String id ) {
    return( id == null ) ? FORMAT_WITHOUT_ID : FORMAT_WITH_ID;
  }

  @Override
  public void log( final StackTraceElement caller, final MessageLevel level, final String id, final String message, final Throwable thrown ) {
    MessageFormat messageFormat = new MessageFormat(getFormat( id ), Locale.ROOT );
    System.out.println( messageFormat.format(new Object[]{level, id, message}) ); // I18N not required.
    if( thrown != null ) {
      thrown.printStackTrace();
    }
  }
}
