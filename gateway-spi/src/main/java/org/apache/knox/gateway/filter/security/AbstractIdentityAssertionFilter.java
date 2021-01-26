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

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.security.principal.PrincipalMapper;
import org.apache.knox.gateway.security.principal.PrincipalMappingException;
import org.apache.knox.gateway.security.principal.SimplePrincipalMapper;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.TokenUtils;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

public abstract class AbstractIdentityAssertionFilter extends AbstractIdentityAssertionBase implements Filter {

  private static final GatewaySpiMessages LOG = MessagesFactory.get( GatewaySpiMessages.class );
  private static final String PARAM_NAME_SIGNATURE_ALG = "signature.algorithm";
  protected PrincipalMapper mapper = new SimplePrincipalMapper();
  protected String signatureAlgorithm;

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
    loadPrincipalMappings(filterConfig);

    setSignatureAlgorithm(filterConfig);
  }

  private void setSignatureAlgorithm(FilterConfig filterConfig) throws ServletException {
    try {
      final String configuredSignatureAlgorithm = filterConfig.getInitParameter(PARAM_NAME_SIGNATURE_ALG);
      final GatewayServices services = (GatewayServices) filterConfig.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
      final GatewayConfig gatewayConfig = (GatewayConfig) filterConfig.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
      final AliasService aliasService = services.getService(ServiceType.ALIAS_SERVICE);
      this.signatureAlgorithm = TokenUtils.getSignatureAlgorithm(configuredSignatureAlgorithm, aliasService, gatewayConfig.getSigningKeystoreName());
    } catch(AliasServiceException e) {
      throw new ServletException(e);
    }
  }

  @Override
  public void destroy() {}
}
