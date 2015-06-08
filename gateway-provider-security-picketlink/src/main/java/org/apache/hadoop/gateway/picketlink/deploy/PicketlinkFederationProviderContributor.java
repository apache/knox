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
package org.apache.hadoop.gateway.picketlink.deploy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.picketlink.PicketlinkMessages;
import org.apache.hadoop.gateway.services.security.AliasService;
import org.apache.hadoop.gateway.services.security.AliasServiceException;
import org.apache.hadoop.gateway.services.security.MasterService;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.Service;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.picketlink.identity.federation.web.filters.ServiceProviderContextInitializer;

public class PicketlinkFederationProviderContributor extends
    ProviderDeploymentContributorBase {
  private static final String ROLE = "federation";
  private static final String NAME = "Picketlink";
  private static final String PICKETLINK_FILTER_CLASSNAME = "org.picketlink.identity.federation.web.filters.SPFilter";
  private static final String CAPTURE_URL_FILTER_CLASSNAME = "org.apache.hadoop.gateway.picketlink.filter.CaptureOriginalURLFilter";
  private static final String IDENTITY_ADAPTER_CLASSNAME = "org.apache.hadoop.gateway.picketlink.filter.PicketlinkIdentityAdapter";
  private static final String IDENTITY_URL_PARAM = "identity.url";
  private static final String SERVICE_URL_PARAM = "service.url";
  private static final String KEYSTORE_URL_PARAM = "keystore.url";
  private static final String SIGNINGKEY_ALIAS = "gateway-identity";
  private static final String VALIDATING_ALIAS_KEY = "validating.alias.key";
  private static final String VALIDATING_ALIAS_VALUE = "validating.alias.value";
  private static final String CLOCK_SKEW_MILIS = "clock.skew.milis";
  private static PicketlinkMessages log = MessagesFactory.get( PicketlinkMessages.class );

  private MasterService ms = null;
  private AliasService as = null;

  @Override
  public String getRole() {
    return ROLE;
  }

  @Override
  public String getName() {
    return NAME;
  }
  
  public void setMasterService(MasterService ms) {
    this.ms = ms;
  }

  public void setAliasService(AliasService as) {
    this.as = as;
  }

  @Override
  public void initializeContribution(DeploymentContext context) {
    super.initializeContribution(context);
  }

  @Override
  public void contributeProvider(DeploymentContext context, Provider provider) {
    // LJM TODO: consider creating a picketlink configuration provider to
    // handle the keystore secrets without putting them in a config file directly.
    // Once that is done then we can remove the unneeded gateway services from those
    // that are available to providers.
    context.getWebAppDescriptor().createListener().listenerClass( ServiceProviderContextInitializer.class.getName());

    PicketlinkConf config = new PicketlinkConf( );
    Map<String,String> params = provider.getParams();
    config.setIdentityURL(params.get(IDENTITY_URL_PARAM));
    config.setServiceURL(params.get(SERVICE_URL_PARAM));
    config.setKeystoreURL(params.get(KEYSTORE_URL_PARAM));
    if (ms != null) {
      config.setKeystorePass(new String(ms.getMasterSecret()));
    }
    config.setSigningKeyAlias(SIGNINGKEY_ALIAS);
    if (as != null) {
      char[] passphrase = null;
      try {
        passphrase = as.getGatewayIdentityPassphrase();
        config.setSigningKeyPass(new String(passphrase));
      } catch (AliasServiceException e) {
        log.unableToGetGatewayIdentityPassphrase(e);
      }
    }
    config.setValidatingAliasKey(params.get(VALIDATING_ALIAS_KEY));
    config.setValidatingAliasValue(params.get(VALIDATING_ALIAS_VALUE));
    config.setClockSkewMilis(params.get(CLOCK_SKEW_MILIS));
    String configStr = config.toString();
    if( config != null ) {
      context.getWebArchive().addAsWebInfResource( new StringAsset( configStr ), "picketlink.xml" );
    }
  }

  @Override
  public void contributeFilter(DeploymentContext context, Provider provider, Service service, 
      ResourceDescriptor resource, List<FilterParamDescriptor> params) {
    // blindly add all the provider params as filter init params
    if (params == null) {
      params = new ArrayList<FilterParamDescriptor>();
    }
    Map<String, String> providerParams = provider.getParams();
    for(Entry<String, String> entry : providerParams.entrySet()) {
      params.add( resource.createFilterParam().name( entry.getKey().toLowerCase() ).value( entry.getValue() ) );
    }
    resource.addFilter().name( getName() ).role( getRole() ).impl( CAPTURE_URL_FILTER_CLASSNAME ).params( params );
    resource.addFilter().name( getName() ).role( getRole() ).impl( PICKETLINK_FILTER_CLASSNAME ).params( params );
    resource.addFilter().name( getName() ).role( getRole() ).impl( IDENTITY_ADAPTER_CLASSNAME ).params( params );
  }

}
