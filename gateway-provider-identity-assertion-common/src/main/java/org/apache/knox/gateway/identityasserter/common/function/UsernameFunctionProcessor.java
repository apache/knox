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
package org.apache.knox.gateway.identityasserter.common.function;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.security.SubjectUtils;

import javax.security.auth.Subject;
import java.util.ArrayList;
import java.util.List;

public class UsernameFunctionProcessor
    implements UrlRewriteFunctionProcessor<UsernameFunctionDescriptor> {

  private static final GatewaySpiMessages LOG = MessagesFactory.get( GatewaySpiMessages.class );
//  private PrincipalMapper mapper = null;

  @Override
  public String name() {
    return UsernameFunctionDescriptor.FUNCTION_NAME;
  }

  @Override
  public void initialize( UrlRewriteEnvironment environment, UsernameFunctionDescriptor descriptor ) throws Exception {
//    if( environment != null ) {
//      GatewayServices services = environment.getAttribute( GatewayServices.GATEWAY_SERVICES_ATTRIBUTE );
//      if( services != null ) {
//        mapper = (PrincipalMapper)services.getService( "PrincipalMapperService" /*GatewayServices.PRINCIPAL_MAPPER_SERVICE*/ );
//      }
//    }
  }

  @Override
  public void destroy() throws Exception {
  }

  @Override
  public List<String> resolve( UrlRewriteContext context, List<String> parameters ) throws Exception {
    List<String> results = null;
    Subject subject = SubjectUtils.getCurrentSubject( );
    if( subject != null ) {
      results = new ArrayList<>( 1 );
      String username = SubjectUtils.getEffectivePrincipalName(subject);
      results.add( username );
    } else if( parameters != null && !parameters.isEmpty() ) {
      results = new ArrayList<>( 1 );
      results.add( parameters.get( 0 ) );
    }
    return results;
  }

}

