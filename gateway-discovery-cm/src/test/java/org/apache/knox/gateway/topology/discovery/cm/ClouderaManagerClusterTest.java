/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements. See the NOTICE file distributed with this
 *  * work for additional information regarding copyright ownership. The ASF
 *  * licenses this file to you under the Apache License, Version 2.0 (the
 *  * "License"); you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations under
 *  * the License.
 *
 */
package org.apache.knox.gateway.topology.discovery.cm;

import org.apache.knox.gateway.topology.discovery.cm.model.solr.SolrServiceModelGenerator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.knox.gateway.topology.discovery.cm.ServiceModel.QUALIFYING_SERVICE_PARAM_PREFIX;
import static org.apache.knox.gateway.topology.discovery.cm.model.solr.SolrServiceModelGenerator.DISCOVERY_SERVICE_DISPLAY_NAME;
import static org.apache.knox.gateway.topology.discovery.cm.model.solr.SolrServiceModelGenerator.DISCOVERY_SERVICE_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClouderaManagerClusterTest {

    /**
     * This is the default case, with no service parameters declared for the SOLR service.
     */
    @Test
    public void testGetUnqualifiedMultipleSOLRServiceURLs_NoServiceParams() {
        doTestGetSOLRServiceURLs(Arrays.asList("SOLR-1", "SOLR-2"),
                                 Collections.emptyMap(),
                                 Arrays.asList("SOLR-1", "SOLR-2"));
    }

    /**
     * Ensure that declared service parameters, which are NOT qualifying service parameters, do not affect the default
     * discovery.
     */
    @Test
    public void testGetUnqualifiedMultipleSOLRServiceURLs_NoDiscoveryServiceParams() {
        doTestGetSOLRServiceURLs(Arrays.asList("SOLR-3", "SOLR-7"),
                                 Collections.singletonMap("test-param", "test-value"),
                                 Arrays.asList("SOLR-3", "SOLR-7"));
    }

    /**
     * Ensure that an incorrectly-specified SOLR service display name qualifying parameter results in no matching
     * services, and consequently with no service URLs.
     */
    @Test
    public void testGetIncorrectlyQualifiedSOLRServiceURLs() {
        doTestGetSOLRServiceURLs(Arrays.asList("SOLR-3", "SOLR-7"),
                Collections.singletonMap(QUALIFYING_SERVICE_PARAM_PREFIX + DISCOVERY_SERVICE_DISPLAY_NAME, "SOLR-5Display"),
                Collections.emptyList());
    }

    /**
     * Ensure that a correctly-specified SOLR service display name qualifying parameter is honored.
     */
    @Test
    public void testGetDisplayNameQualifiedSOLRServiceURLs() {
        doTestGetSOLRServiceURLs(Arrays.asList("SOLR-3", "SOLR-7"),
                Collections.singletonMap(QUALIFYING_SERVICE_PARAM_PREFIX + DISCOVERY_SERVICE_DISPLAY_NAME, "SOLR-7Display"),
                Collections.singletonList("SOLR-7"));
    }

    /**
     * Ensure that a correctly-specified SOLR service name qualifying parameter is honored.
     */
    @Test
    public void testGetServiceNameQualifiedSOLRServiceURLs() {
        doTestGetSOLRServiceURLs(Arrays.asList("SOLR-5", "SOLR-6"),
                Collections.singletonMap(QUALIFYING_SERVICE_PARAM_PREFIX + DISCOVERY_SERVICE_NAME, "SOLR-5"),
                Collections.singletonList("SOLR-5"));
    }

    /**
     * Ensure that a correctly-specified SOLR service name and display name qualifying parameters are honored.
     */
    @Test
    public void testGetServiceAndDisplayNameQualifiedSOLRServiceURLs() {
        Map<String, String> serviceParams = new HashMap<>();
        serviceParams.put(QUALIFYING_SERVICE_PARAM_PREFIX + DISCOVERY_SERVICE_NAME, "SOLR-6");
        serviceParams.put(QUALIFYING_SERVICE_PARAM_PREFIX + DISCOVERY_SERVICE_DISPLAY_NAME, "SOLR-6Display");
        doTestGetSOLRServiceURLs(Arrays.asList("SOLR-5", "SOLR-6"),
                                 serviceParams,
                                 Collections.singletonList("SOLR-6"));
    }

    /**
     * Ensure that an incorrectly-specified service name qualifying parameter, even with a correctly-specified SOLR
     * display name qualifying parameter, results in no matching services.
     */
    @Test
    public void testGetServiceAndDisplayNameQualifiedSOLRServiceURLs_IncorrectServiceName() {
        Map<String, String> serviceParams = new HashMap<>();
        serviceParams.put(QUALIFYING_SERVICE_PARAM_PREFIX + DISCOVERY_SERVICE_NAME, "SOLR-3");
        serviceParams.put(QUALIFYING_SERVICE_PARAM_PREFIX + DISCOVERY_SERVICE_DISPLAY_NAME, "SOLR-6Display");
        doTestGetSOLRServiceURLs(Arrays.asList("SOLR-5", "SOLR-6"),
                                 serviceParams,
                                 Collections.emptyList());
    }

    /**
     * Ensure that an incorrectly-specified service display name qualifying parameter, even with a correctly-specified
     * SOLR service name qualifying parameter, results in no matching services.
     */
    @Test
    public void testGetServiceAndDisplayNameQualifiedSOLRServiceURLs_IncorrectDisplayName() {
        Map<String, String> serviceParams = new HashMap<>();
        serviceParams.put(QUALIFYING_SERVICE_PARAM_PREFIX + DISCOVERY_SERVICE_NAME, "SOLR-6");
        serviceParams.put(QUALIFYING_SERVICE_PARAM_PREFIX + DISCOVERY_SERVICE_DISPLAY_NAME, "SOLR-8Display");
        doTestGetSOLRServiceURLs(Arrays.asList("SOLR-5", "SOLR-6"),
                                 serviceParams,
                                 Collections.emptyList());
    }

    /**
     * Test the SOLR service URL discovery based on the specified parameters.
     *
     * @param serviceNames The name(s) of the SOLR service(s) in the cluster.
     * @param serviceParams Any service parameter declarations (i.e., from the supposed descriptor)
     * @param serviceNamesToExpectURLs The service(s) for which URLs are expected.
     */
    private void doTestGetSOLRServiceURLs(final List<String> serviceNames,
                                          final Map<String, String> serviceParams,
                                          final List<String> serviceNamesToExpectURLs) {
        final Set<ServiceModel> testSolrServiceModels = new HashSet<>();
        final List<String> expectedURLs = new ArrayList<>();

        for (String serviceName : serviceNames) {
            ServiceModel model = createSOLRServiceModel("http://" + serviceName + "-host:1234/solr");
            model.addQualifyingServiceParam(DISCOVERY_SERVICE_NAME, serviceName);
            model.addQualifyingServiceParam(DISCOVERY_SERVICE_DISPLAY_NAME, serviceName + "Display");
            testSolrServiceModels.add(model);
            if (serviceNamesToExpectURLs.contains(serviceName)) {
                expectedURLs.add(model.getServiceUrl());
            }
        }

        final ClouderaManagerCluster cluster = new ClouderaManagerCluster("test");
        cluster.addServiceModels(testSolrServiceModels);

        List<String> solrURLs = cluster.getServiceURLs("SOLR", serviceParams);
        assertEquals("Unexpected URL count.", expectedURLs.size(), solrURLs.size());
        for (String expectedURL : expectedURLs) {
            assertTrue("Missing expected URL: " + expectedURL, solrURLs.contains(expectedURL));
        }
    }

    private static ServiceModel createSOLRServiceModel(final String url) {
        return new ServiceModel(ServiceModel.Type.API,
                                SolrServiceModelGenerator.SERVICE,
                                SolrServiceModelGenerator.SERVICE_TYPE,
                                SolrServiceModelGenerator.ROLE_TYPE,
                                url);
    }

}
