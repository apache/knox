/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.cm.model.yarn;

import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.AbstractServiceModelGeneratorTest;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class YarnSUIServiceModelGeneratorTest extends AbstractServiceModelGeneratorTest {

    @Test
    public void testServiceModelMetadata() throws Exception {
        final Map<String, String> serviceConfig = Collections.emptyMap();

        final Map<String, String> roleConfig = new HashMap<>();
        roleConfig.put(YarnSUIServiceModelGenerator.RM_HTTP_PORT, "8088");
        roleConfig.put(YarnSUIServiceModelGenerator.RM_HTTPS_PORT, "8090");

        validateServiceModel(createServiceModel(serviceConfig, roleConfig), serviceConfig, roleConfig);
    }

    @Override
    protected String getServiceType() {
        return YarnSUIServiceModelGenerator.SERVICE_TYPE;
    }

    @Override
    protected String getRoleType() {
        return YarnSUIServiceModelGenerator.ROLE_TYPE;
    }

    @Override
    protected ServiceModelGenerator newGenerator() {
        return new YarnSUIServiceModelGenerator(){
            @Override // To bypass the HDFS ssl_enable check
            protected String generateURL(ApiService service, ApiServiceConfig serviceConfig, ApiRole role,
                                         ApiConfigList roleConfig, ApiServiceConfig coreConfig) {

                String hostname = role.getHostRef().getHostname();
                String port = getRoleConfigValue(roleConfig, YarnSUIServiceModelGenerator.RM_HTTP_PORT);

                return String.format(Locale.getDefault(), "http://%s:%s", hostname, port);
            }
        };
    }
}
