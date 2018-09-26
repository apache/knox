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
package org.apache.knox.gateway.i18n.messages;

import org.apache.knox.gateway.i18n.messages.loggers.test.TestMessageLogger;
import org.apache.knox.gateway.i18n.messages.loggers.test.TestMessageLoggerFactory;
import org.apache.knox.gateway.i18n.messages.loggers.test.TestMessageRecord;
import org.apache.knox.test.category.FastTests;
import org.apache.knox.test.category.UnitTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 *
 */
@Category( { UnitTests.class, FastTests.class } )
public class MessagesTest {

  @Test
  public void testFirst() {
    MessagesTestSubject log = MessagesFactory.get( MessagesTestSubject.class );

    log.withFullAnnotationAndParameter( 7 );

    TestMessageLogger logger = (TestMessageLogger)TestMessageLoggerFactory.getFactory().getLogger( "some.logger.name" );
    assertThat( logger.records.size(), equalTo( 1 ) );

    TestMessageRecord record = logger.records.get( 0 );

    assertThat( record.getCaller().getClassName(), is( this.getClass().getName() ) );
    assertThat( record.getCaller().getMethodName(), is( "testFirst" ) );

  }

}
