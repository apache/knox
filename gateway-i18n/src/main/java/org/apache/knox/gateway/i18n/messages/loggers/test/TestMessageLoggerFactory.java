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

import org.apache.knox.gateway.i18n.messages.MessageLogger;
import org.apache.knox.gateway.i18n.messages.MessageLoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public class TestMessageLoggerFactory implements MessageLoggerFactory {

  private static TestMessageLoggerFactory INSTANCE;
  private static final Map<String,MessageLogger> LOGGERS = new ConcurrentHashMap<String,MessageLogger>();

  public static TestMessageLoggerFactory getFactory() {
    if( INSTANCE == null ) {
      INSTANCE = new TestMessageLoggerFactory();
    }
    return INSTANCE;
  }

  public TestMessageLoggerFactory() {
    INSTANCE = this;
  }

  @Override
  public MessageLogger getLogger( String name ) {
    MessageLogger logger = LOGGERS.get( name );
    if( logger == null ) {
      logger = new TestMessageLogger( name );
      LOGGERS.put( name, logger );
    }
    return logger;
  }

}
