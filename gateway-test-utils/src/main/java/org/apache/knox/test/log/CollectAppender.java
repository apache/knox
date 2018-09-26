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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

public class CollectAppender extends AppenderSkeleton {

  public CollectAppender() {
    super();
  }

  public static final BlockingQueue<LoggingEvent> queue = new LinkedBlockingQueue<>();
  
  @Override
  protected void append( LoggingEvent event ) {
    event.getProperties();
    queue.add( event );
  }

  @Override
  public void close() {
    closed = true;
  }

  @Override
  public boolean requiresLayout() {
    return false;
  }

}