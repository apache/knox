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
package org.apache.hadoop.gateway.yarn.rm;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ServiceDeploymentContributorBase;
import org.apache.hadoop.gateway.descriptor.FilterParamDescriptor;
import org.apache.hadoop.gateway.descriptor.ResourceDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptorFactory;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteServletFilter;
import org.apache.hadoop.gateway.topology.Service;

public class ResourceManagerDeploymentContributor extends
    ServiceDeploymentContributorBase {
  private static final String RULES_RESOURCE = ResourceManagerDeploymentContributor.class
      .getName().replace( '.', '/' ) + "/rewrite.xml";
  private static final String EXTERNAL_PATH = "/resourcemanager";
  private static final String PROXY_PATH = "/resourcemanager/proxy";

  @Override
  public String getRole() {
    return "RESOURCEMANAGER";
  }

  @Override
  public String getName() {
    return "resourcemanager";
  }

  @Override
  public void contributeService(DeploymentContext context, Service service)
      throws Exception {
    contributeRewriteRules( context, service );
    contributeResources( context, service );
  }

  private void contributeRewriteRules(DeploymentContext context, Service service)
      throws URISyntaxException, IOException {
    UrlRewriteRulesDescriptor serviceRules = loadRulesFromClassPath();
    UrlRewriteRulesDescriptor clusterRules = context.getDescriptor( "rewrite" );
    clusterRules.addRules( serviceRules );
  }

  private UrlRewriteRulesDescriptor loadRulesFromClassPath() throws IOException {
    InputStream stream = this.getClass().getClassLoader()
        .getResourceAsStream( RULES_RESOURCE );
    Reader reader = new InputStreamReader( stream );
    UrlRewriteRulesDescriptor rules = UrlRewriteRulesDescriptorFactory.load(
        "xml", reader );
    reader.close();
    stream.close();
    return rules;
  }

  private void contributeResources(DeploymentContext context, Service service)
      throws URISyntaxException {
    Map<String, String> filterParams = new HashMap<String, String>();

    contributeResource( context, service, EXTERNAL_PATH + "/v1/cluster/", null );

    contributeResource( context, service, EXTERNAL_PATH + "/v1/cluster/**?**", null );

    filterParams.clear();
    filterParams.put( UrlRewriteServletFilter.RESPONSE_BODY_FILTER_PARAM, getQualifiedName() + "/apps/outbound" );
    contributeResource( context, service, EXTERNAL_PATH + "/v1/cluster/apps?**", filterParams );

    filterParams.clear();
    filterParams.put( UrlRewriteServletFilter.RESPONSE_BODY_FILTER_PARAM, getQualifiedName() + "/app/outbound" );
    contributeResource( context, service, EXTERNAL_PATH + "/v1/cluster/apps/*?**", filterParams );

    filterParams.clear();
    filterParams.put( UrlRewriteServletFilter.RESPONSE_BODY_FILTER_PARAM, getQualifiedName() + "/appattempts/outbound" );
    contributeResource( context, service, EXTERNAL_PATH + "/v1/cluster/apps/*/appattempts?**", filterParams );

    filterParams.clear();
    filterParams.put( UrlRewriteServletFilter.RESPONSE_BODY_FILTER_PARAM, getQualifiedName() + "/nodes/outbound" );
    contributeResource( context, service, EXTERNAL_PATH + "/v1/cluster/nodes?**", filterParams );

    filterParams.clear();
    filterParams.put( UrlRewriteServletFilter.REQUEST_URL_RULE_PARAM, getQualifiedName() + "/nodeId/inbound" );
    filterParams.put( UrlRewriteServletFilter.RESPONSE_BODY_FILTER_PARAM, getQualifiedName() + "/node/outbound" );
    contributeResource( context, service, EXTERNAL_PATH + "/v1/cluster/nodes/*?**", filterParams );

    filterParams.clear();
    filterParams.put( UrlRewriteServletFilter.REQUEST_URL_RULE_PARAM, getQualifiedName() + "/inbound/proxy" );
    contributeResource( context, service, PROXY_PATH + "/*/ws/v1/**?**", filterParams );

    filterParams.clear();
    filterParams.put( UrlRewriteServletFilter.RESPONSE_BODY_FILTER_PARAM, getQualifiedName() + "/proxy/jobattempts/outbound" );
    contributeResource( context, service, PROXY_PATH + "/*/ws/v1/mapreduce/jobs/*/jobattempts", filterParams );


    filterParams.clear();
    filterParams.put( UrlRewriteServletFilter.RESPONSE_BODY_FILTER_PARAM, getQualifiedName() + "/proxy/taskattempts/outbound" );
    contributeResource( context, service, PROXY_PATH + "/*/ws/v1/mapreduce/jobs/*/tasks/*/attempts", filterParams );

    filterParams.clear();
    filterParams.put( UrlRewriteServletFilter.RESPONSE_BODY_FILTER_PARAM, getQualifiedName() + "/proxy/taskattempt/outbound" );
    contributeResource( context, service, PROXY_PATH + "/*/ws/v1/mapreduce/jobs/*/tasks/*/attempts/*", filterParams );
  }

  private void contributeResource( DeploymentContext context, Service service, String pattern, Map<String, String> filterParams ) throws URISyntaxException {
    List<FilterParamDescriptor> params = new ArrayList<FilterParamDescriptor>();
    ResourceDescriptor resource = context.getGatewayDescriptor().addResource();
    resource.role( service.getRole() );
    resource.pattern( pattern );
    addWebAppSecFilters( context, service, resource );
    addAuthenticationFilter( context, service, resource );
    addIdentityAssertionFilter( context, service, resource );
    addAuthorizationFilter( context, service, resource );
    if ( filterParams != null ) {
      for( Entry<String, String> filterParam : filterParams.entrySet() ) {
        params.add( resource.createFilterParam().name( filterParam.getKey() ).value( filterParam.getValue() ) );
      }
    }
    addRewriteFilter( context, service, resource, params );
    addDispatchFilter( context, service, resource, "dispatch", "http-client" );
  }

  private String getQualifiedName() {
    return getRole() + "/" + getName();
  }
}
