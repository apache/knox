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
package org.apache.hadoop.gateway.filter.security;

import java.security.Principal;
import java.util.Set;

import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

import org.apache.hadoop.gateway.i18n.GatewaySpiMessages;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.security.PrimaryPrincipal;
import org.apache.hadoop.gateway.security.principal.PrincipalMapper;
import org.apache.hadoop.gateway.security.principal.PrincipalMappingException;
import org.apache.hadoop.gateway.security.principal.SimplePrincipalMapper;

public abstract class AbstractIdentityAssertionFilter implements Filter {

  private static final GatewaySpiMessages LOG = MessagesFactory.get( GatewaySpiMessages.class );
  protected PrincipalMapper mapper = new SimplePrincipalMapper();

  public AbstractIdentityAssertionFilter() {
    super();
  }

  protected void loadPrincipalMappings(FilterConfig filterConfig) {
    String principalMapping = filterConfig.getServletContext().getInitParameter("principal.mapping");
    if (principalMapping != null && !principalMapping.isEmpty()) {
      try {
        mapper.loadMappingTable(principalMapping);
      }
      catch (PrincipalMappingException pme) {
        LOG.failedToLoadPrincipalMappingTable( pme );
      }
    }
  }

  /**
   * Retrieve the principal to represent the asserted identity from
   * the provided Subject.
   * @param subject
   * @return principalName
   */
  protected String getPrincipalName(Subject subject) {
    // look first for the knox specific PrimaryPrincipal to use as the asserted identity
    // if not found fallback to the first principal found
    String name = null;
    Set<PrimaryPrincipal> primaryPrincipals = subject.getPrincipals(PrimaryPrincipal.class);
    if (primaryPrincipals.size() > 0) {
      return ((PrimaryPrincipal)primaryPrincipals.toArray()[0]).getName();
    }
    
    // LJM TODO: this implementation assumes the first one found 
    // should configure through context param based on knowledge
    // of the authentication provider in use
    Set<Principal> principals = subject.getPrincipals();
    for (Principal p : principals) {
      name = p.getName();
      break;
    }
    return name;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    // load principal mappings
    loadPrincipalMappings(filterConfig);
  }

  public void destroy() {
    
  }

}
