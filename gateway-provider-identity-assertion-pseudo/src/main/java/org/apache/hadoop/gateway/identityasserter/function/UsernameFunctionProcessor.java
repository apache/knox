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

import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.hadoop.gateway.filter.security.AbstractIdentityAssertionBase;
import org.apache.hadoop.gateway.i18n.GatewaySpiMessages;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.security.principal.PrincipalMapper;
import org.apache.hadoop.gateway.security.principal.SimplePrincipalMapper;

import javax.security.auth.Subject;
import java.security.AccessController;

public class UsernameFunctionProcessor
    extends AbstractIdentityAssertionBase
    implements UrlRewriteFunctionProcessor<UsernameFunctionDescriptor> {

  private static final GatewaySpiMessages LOG = MessagesFactory.get( GatewaySpiMessages.class );
  protected PrincipalMapper mapper = new SimplePrincipalMapper();
//  private UrlRewriteEnvironment environment;
//  private UsernameFunctionDescriptor descriptor;

  @Override
  public String name() {
    return UsernameFunctionDescriptor.FUNCTION_NAME;
  }

  @Override
  public void initialize( UrlRewriteEnvironment environment, UsernameFunctionDescriptor descriptor ) throws Exception {
//    this.environment = environment;
//    this.descriptor = descriptor;
  }

  @Override
  public void destroy() throws Exception {
  }

  @Override
  public String resolve( UrlRewriteContext context, String parameter ) throws Exception {
    String value = parameter;
    Subject subject = Subject.getSubject( AccessController.getContext() );
    if( subject != null ) {
      value = getPrincipalName( subject );
    }
    return value;
  }

}

