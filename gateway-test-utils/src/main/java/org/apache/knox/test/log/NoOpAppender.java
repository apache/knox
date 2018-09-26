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
package org.apache.knox.test.log;

import org.apache.log4j.Appender;
import org.apache.log4j.Layout;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;

import java.util.Enumeration;

public class NoOpAppender implements Appender {

  public static Enumeration<Appender> setUp() {
    Enumeration<Appender> appenders = (Enumeration<Appender>)Logger.getRootLogger().getAllAppenders();
    Logger.getRootLogger().removeAllAppenders();
    Logger.getRootLogger().addAppender( new NoOpAppender() );
    return appenders;
  }

  public static void tearDown( Enumeration<Appender> appenders ) {
    if( appenders != null ) {
      while( appenders.hasMoreElements() ) {
        Logger.getRootLogger().addAppender( appenders.nextElement() );
      }
    }
  }

  @Override
  public void addFilter( Filter newFilter ) {
  }

  @Override
  public Filter getFilter() {
    return null;
  }

  @Override
  public void clearFilters() {
  }

  @Override
  public void close() {
  }

  @Override
  public void doAppend( LoggingEvent event ) {
  }

  @Override
  public String getName() {
    return this.getClass().getName();
  }

  @Override
  public void setErrorHandler( ErrorHandler errorHandler ) {
  }

  @Override
  public ErrorHandler getErrorHandler() {
    return null;
  }

  @Override
  public void setLayout( Layout layout ) {
  }

  @Override
  public Layout getLayout() {
    return null;
  }

  @Override
  public void setName( String name ) {
  }

  @Override
  public boolean requiresLayout() {
    return false;
  }
}
