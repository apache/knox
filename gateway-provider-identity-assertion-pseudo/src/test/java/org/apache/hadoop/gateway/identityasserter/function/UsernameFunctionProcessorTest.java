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
package org.apache.hadoop.gateway.identityasserter.function;

import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.hadoop.gateway.filter.security.AbstractIdentityAssertionBase;
import org.apache.hadoop.gateway.security.PrimaryPrincipal;
import org.junit.Test;

import javax.security.auth.Subject;
import java.security.PrivilegedExceptionAction;
import java.util.Iterator;
import java.util.ServiceLoader;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.fail;

public class UsernameFunctionProcessorTest {

  @Test
  public void testInitialize() throws Exception {
    UsernameFunctionProcessor processor = new UsernameFunctionProcessor();
    // Shouldn't fail.
    processor.initialize( null, null );
  }

  @Test
  public void testDestroy() throws Exception {
    UsernameFunctionProcessor processor = new UsernameFunctionProcessor();
    // Shouldn't fail.
    processor.destroy();
  }

  @Test
  public void testResolve() throws Exception {
    final UsernameFunctionProcessor processor = new UsernameFunctionProcessor();
    assertThat( processor.resolve( null, null ), nullValue() );
    assertThat( processor.resolve( null, "test-input" ), is( "test-input" ) );
    Subject subject = new Subject();
    subject.getPrincipals().add( new PrimaryPrincipal( "test-username" ) );
    subject.setReadOnly();
    Subject.doAs( subject, new PrivilegedExceptionAction<Object>() {
      @Override
      public Object run() throws Exception {
        assertThat( processor.resolve( null, null ), is( "test-username" ) );
        assertThat( processor.resolve( null, "test-ignored" ), is( "test-username" ) );
        return null;
      }
    } );
  }

  @Test
  public void testServiceLoader() throws Exception {
    ServiceLoader loader = ServiceLoader.load( UrlRewriteFunctionProcessor.class );
    Iterator iterator = loader.iterator();
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof UsernameFunctionProcessor ) {
        return;
      }
    }
    fail( "Failed to find UsernameFunctionProcessor via service loader." );
  }

}
