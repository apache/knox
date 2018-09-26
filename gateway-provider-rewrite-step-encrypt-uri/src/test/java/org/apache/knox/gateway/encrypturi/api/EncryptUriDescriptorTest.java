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
package org.apache.knox.gateway.encrypturi.api;

import java.util.Iterator;
import java.util.ServiceLoader;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteStepDescriptor;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class EncryptUriDescriptorTest {

  @Test
  @SuppressWarnings("rawtypes")
  public void testServiceLoader() throws Exception {
    ServiceLoader loader = ServiceLoader.load( UrlRewriteStepDescriptor.class );
    Iterator iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof EncryptUriDescriptor ) {
        return;
      }
    }
    fail( "Failed to find " + EncryptUriDescriptor.class.getName() + " via service loader." );
  }

  @Test
  public void testGetAndSet() {
    EncryptUriDescriptor descriptor = new EncryptUriDescriptor();
    assertThat( descriptor.type(), is( "encrypt" ) );
    assertThat( descriptor.getParam(), nullValue() );

  }
}
