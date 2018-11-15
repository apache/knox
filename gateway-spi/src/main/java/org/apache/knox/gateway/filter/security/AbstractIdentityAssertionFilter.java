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
package org.apache.knox.gateway.filter.security;

import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.GatewaySpiResources;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;
import org.apache.knox.gateway.security.principal.PrincipalMapper;
import org.apache.knox.gateway.security.principal.PrincipalMappingException;
import org.apache.knox.gateway.security.principal.SimplePrincipalMapper;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

public abstract class AbstractIdentityAssertionFilter extends AbstractIdentityAssertionBase implements Filter {

  private static final GatewaySpiMessages LOG = MessagesFactory.get( GatewaySpiMessages.class );
  private static final GatewaySpiResources RES = ResourcesFactory.get( GatewaySpiResources.class );
  private static AuditService auditService = AuditServiceFactory.getAuditService();
  private static Auditor auditor = auditService.getAuditor(
      AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
      AuditConstants.KNOX_COMPONENT_NAME );
  protected PrincipalMapper mapper = new SimplePrincipalMapper();

  public AbstractIdentityAssertionFilter() {
    super();
  }

  protected void loadPrincipalMappings(FilterConfig filterConfig) {
    String principalMapping = filterConfig.getServletContext().getInitParameter("principal.mapping");
    String groupMapping = filterConfig.getServletContext().getInitParameter("group.principal.mapping");
    if (principalMapping != null && !principalMapping.isEmpty() || groupMapping != null && !groupMapping.isEmpty()) {
      try {
        mapper.loadMappingTable(principalMapping, groupMapping);
      }
      catch (PrincipalMappingException pme) {
        LOG.failedToLoadPrincipalMappingTable( pme );
      }
    }
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    // load principal mappings
    loadPrincipalMappings(filterConfig);
  }

  @Override
  public void destroy() {
    
  }
}
