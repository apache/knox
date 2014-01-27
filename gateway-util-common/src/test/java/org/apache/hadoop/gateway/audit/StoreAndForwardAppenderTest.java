/**
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
package org.apache.hadoop.gateway.audit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.IOException;

import org.apache.hadoop.test.log.CollectAppender;
import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

public class StoreAndForwardAppenderTest {

  @Before
  public void setup() throws IOException {
    String absolutePath = "target/audit";
    File db = new File( absolutePath + ".db" );
    if( db.exists() ) {
      assertThat( db.delete(), is( true ) );
    }
    File lg = new File( absolutePath + ".lg" );
    if( lg.exists() ) {
      assertThat( lg.delete(), is( true ) );
    }
  }
  
  @Test(timeout = 500000)
  public void testAppender() throws Exception {
    int iterations = 1000;
    Logger logger = Logger.getLogger( "audit.store" );
    for( int i = 1; i <= iterations; i++ ) {
      logger.info( Integer.toString( i ) );
    }
    while( CollectAppender.queue.size() < iterations ) {
    }
    assertThat( CollectAppender.queue.size(), is( iterations ) );
    Appender appender = (Appender)logger.getAllAppenders().nextElement();
    appender.close();
  }

}