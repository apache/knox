/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.filter.rewrite.impl.html;

import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.junit.Test;

import java.util.Iterator;
import java.util.ServiceLoader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;

public class HtmlPostfixProcessorTest {

  public HtmlPostfixProcessorTest() {
    super();
  }

  @SuppressWarnings("rawtypes")
  @Test
  public void testServiceLoader() throws Exception {
    ServiceLoader loader = ServiceLoader.load( UrlRewriteFunctionProcessor.class );
    Iterator iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof HtmlPostfixProcessor) {
        return;
      }
    }
    fail( "Failed to find " + HtmlPostfixProcessor.class.getName() + " via service loader." );
  }

  @Test
  public void testName() throws Exception {
    HtmlPostfixProcessor processor = new HtmlPostfixProcessor();
    assertThat( processor.name(), is( "postfix" ) );
  }


}
