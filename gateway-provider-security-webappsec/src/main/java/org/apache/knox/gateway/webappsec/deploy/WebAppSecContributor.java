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
package org.apache.knox.gateway.webappsec.deploy;

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributorBase;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

public class WebAppSecContributor extends ProviderDeploymentContributorBase {
  private static final String ROLE = "webappsec";
  private static final String NAME = "WebAppSec";
  private static final String CSRF_SUFFIX = "_CSRF";
  private static final String CSRF_FILTER_CLASSNAME = "org.apache.knox.gateway.webappsec.filter.CSRFPreventionFilter";
  private static final String CSRF_ENABLED = "csrf.enabled";
  private static final String CORS_SUFFIX = "_CORS";
  private static final String CORS_FILTER_CLASSNAME = "com.thetransactioncompany.cors.CORSFilter";
  private static final String CORS_ENABLED = "cors.enabled";
  private static final String XFRAME_OPTIONS_SUFFIX = "_XFRAMEOPTIONS";
  private static final String XFRAME_OPTIONS_FILTER_CLASSNAME = "org.apache.knox.gateway.webappsec.filter.XFrameOptionsFilter";
  private static final String XFRAME_OPTIONS_ENABLED = "xframe.options.enabled";
  private static final String XCONTENT_TYPE_OPTIONS_SUFFIX = "_XCONTENTTYPEOPTIONS";
  private static final String XCONTENT_TYPE_OPTIONS_FILTER_CLASSNAME = "org.apache.knox.gateway.webappsec.filter.XContentTypeOptionsFilter";
  private static final String XCONTENT_TYPE_OPTIONS_ENABLED = "xcontent-type.options.enabled";
  private static final String XSS_PROTECTION_SUFFIX = "_XSSPROTECTION";
  private static final String XSS_PROTECTION_FILTER_CLASSNAME = "org.apache.knox.gateway.webappsec.filter.XSSProtectionFilter";
  private static final String XSS_PROTECTION_ENABLED = "xss.protection.enabled";
  private static final String STRICT_TRANSPORT_SUFFIX = "_STRICTTRANSPORT";
  private static final String STRICT_TRANSPORT_FILTER_CLASSNAME = "org.apache.knox.gateway.webappsec.filter.StrictTransportFilter";
  private static final String STRICT_TRANSPORT_ENABLED = "strict.transport.enabled";
  private static final String RATE_LIMITING_FILTER_CLASSNAME = "org.eclipse.jetty.servlets.DoSFilter";
  private static final String RATE_LIMITING_PREFIX = "rate.limiting";
  private static final String RATE_LIMITING_SUFFIX = "_RATE.LIMITING";
  private static final String RATE_LIMITING_ENABLED = RATE_LIMITING_PREFIX + ".enabled";

  @Override
  public String getRole() {
    return ROLE;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public void initializeContribution(DeploymentContext context) {
    super.initializeContribution(context);
  }

  @Override
  public void contributeFilter(DeploymentContext           context,
                               Provider                    provider,
                               Service                     service,
                               ResourceDescriptor          resource,
                               List<FilterParamDescriptor> params) {

    Provider webappsec = context.getTopology().getProvider(ROLE, NAME);
    if (webappsec != null && webappsec.isEnabled()) {
      Map<String,String> map = provider.getParams();
      if (params == null) {
        params = new ArrayList<>();
      }

      Map<String, String> providerParams = provider.getParams();

      // Rate limiting
      String rateLimitingEnabled = map.get(RATE_LIMITING_ENABLED);
      if (Boolean.parseBoolean(rateLimitingEnabled)) {
        provisionConfig(resource, providerParams, params, RATE_LIMITING_PREFIX + ".", true, false);
        resource.addFilter().name(getName() + RATE_LIMITING_SUFFIX)
                .role(getRole())
                .impl(RATE_LIMITING_FILTER_CLASSNAME)
                .params(params);
      }

      // CORS support
      params = new ArrayList<>();
      String corsEnabled = map.get(CORS_ENABLED);
      if (Boolean.parseBoolean(corsEnabled)) {
        provisionConfig(resource, providerParams, params, "cors.");
        resource.addFilter().name(getName() + CORS_SUFFIX)
                            .role(getRole())
                            .impl(CORS_FILTER_CLASSNAME)
                            .params(params);
      }

      // CRSF
      params = new ArrayList<>();
      String csrfEnabled = map.get(CSRF_ENABLED);
      if (Boolean.parseBoolean(csrfEnabled)) {
        provisionConfig(resource, providerParams, params, "csrf.");
        resource.addFilter().name(getName() + CSRF_SUFFIX)
                            .role(getRole())
                            .impl(CSRF_FILTER_CLASSNAME)
                            .params(params);
      }

      // X-Frame-Options - clickjacking protection
      params = new ArrayList<>();
      String xframeOptionsEnabled = map.get(XFRAME_OPTIONS_ENABLED);
      if (Boolean.parseBoolean(xframeOptionsEnabled)) {
        provisionConfig(resource, providerParams, params, "xframe.");
        resource.addFilter().name(getName() + XFRAME_OPTIONS_SUFFIX)
                            .role(getRole())
                            .impl(XFRAME_OPTIONS_FILTER_CLASSNAME)
                            .params(params);
      }

      // X-Content-Type-Options - MIME type sniffing protection
      params = new ArrayList<>();
      String xContentTypeOptionsEnabled = map.get(XCONTENT_TYPE_OPTIONS_ENABLED);
      if (Boolean.parseBoolean(xContentTypeOptionsEnabled)) {
        provisionConfig(resource, providerParams, params, "xcontent-type.");
        resource.addFilter().name(getName() + XCONTENT_TYPE_OPTIONS_SUFFIX)
                            .role(getRole())
                            .impl(XCONTENT_TYPE_OPTIONS_FILTER_CLASSNAME)
                            .params(params);
      }

      // X-XSS-Protection - browser xss protection
      params = new ArrayList<>();
      String xssProtectionEnabled = map.get(XSS_PROTECTION_ENABLED);
      if (Boolean.parseBoolean(xssProtectionEnabled)) {
        provisionConfig(resource, providerParams, params, "xss.");
        resource.addFilter().name(getName() + XSS_PROTECTION_SUFFIX)
                            .role(getRole())
                            .impl(XSS_PROTECTION_FILTER_CLASSNAME)
                            .params(params);
      }

      // HTTP Strict-Transport-Security
      params = new ArrayList<>();
      String strictTranportEnabled = map.get(STRICT_TRANSPORT_ENABLED);
      if (Boolean.parseBoolean(strictTranportEnabled)) {
        provisionConfig(resource, providerParams, params, "strict.");
        resource.addFilter().name(getName() + STRICT_TRANSPORT_SUFFIX)
                            .role(getRole())
                            .impl(STRICT_TRANSPORT_FILTER_CLASSNAME)
                            .params(params);
      }
    }
  }

  private void provisionConfig(ResourceDescriptor resource, Map<String, String> providerParams,
                               List<FilterParamDescriptor> params, String prefix, boolean cutPrefix, boolean toLowerCase) {
    for (Entry<String, String> entry : providerParams.entrySet()) {
      if (entry.getKey().startsWith(prefix)) {
        String key = entry.getKey();
        if (cutPrefix) {
          key = key.substring(prefix.length());
        }
        if (toLowerCase) {
          key = key.toLowerCase(Locale.ROOT);
        }
        params.add(resource.createFilterParam().name(key).value(entry.getValue()));
      }
    }
  }

  private void provisionConfig(ResourceDescriptor resource, Map<String, String> providerParams,
                               List<FilterParamDescriptor> params, String prefix) {
    provisionConfig(resource, providerParams, params, prefix, false, true);
  }
}
