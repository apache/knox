/**
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
package org.apache.hadoop.gateway.topology.simple;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.IOException;

import java.net.URI;
import java.net.URISyntaxException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.gateway.GatewayServer;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.Service;
import org.apache.hadoop.gateway.services.security.AliasService;
import org.apache.hadoop.gateway.services.security.KeystoreService;
import org.apache.hadoop.gateway.services.security.MasterService;
import org.apache.hadoop.gateway.topology.discovery.DefaultServiceDiscoveryConfig;
import org.apache.hadoop.gateway.topology.discovery.ServiceDiscovery;
import org.apache.hadoop.gateway.topology.discovery.ServiceDiscoveryFactory;


/**
 * Processes simple topology descriptors, producing full topology files, which can subsequently be deployed to the
 * gateway.
 */
public class SimpleDescriptorHandler {

    private static final Service[] NO_GATEWAY_SERVICES = new Service[]{};

    private static final SimpleDescriptorMessages log = MessagesFactory.get(SimpleDescriptorMessages.class);

    private static Map<String, ServiceDiscovery> discoveryInstances = new HashMap<>();

    public static Map<String, File> handle(File desc) throws IOException {
        return handle(desc, NO_GATEWAY_SERVICES);
    }

    public static Map<String, File> handle(File desc, Service...gatewayServices) throws IOException {
        return handle(desc, desc.getParentFile(), gatewayServices);
    }

    public static Map<String, File> handle(File desc, File destDirectory) throws IOException {
        return handle(desc, destDirectory, NO_GATEWAY_SERVICES);
    }

    public static Map<String, File> handle(File desc, File destDirectory, Service...gatewayServices) throws IOException {
        return handle(SimpleDescriptorFactory.parse(desc.getAbsolutePath()), desc.getParentFile(), destDirectory, gatewayServices);
    }

    public static Map<String, File> handle(SimpleDescriptor desc, File srcDirectory, File destDirectory) {
        return handle(desc, srcDirectory, destDirectory, NO_GATEWAY_SERVICES);
    }

    public static Map<String, File> handle(SimpleDescriptor desc, File srcDirectory, File destDirectory, Service...gatewayServices) {
        Map<String, File> result = new HashMap<>();

        File topologyDescriptor;

        DefaultServiceDiscoveryConfig sdc = new DefaultServiceDiscoveryConfig(desc.getDiscoveryAddress());
        sdc.setUser(desc.getDiscoveryUser());
        sdc.setPasswordAlias(desc.getDiscoveryPasswordAlias());

        // Use the discovery type from the descriptor. If it's unspecified, employ the default type.
        String discoveryType = desc.getDiscoveryType();
        if (discoveryType == null) {
            discoveryType = "AMBARI";
        }

        // Use the cached discovery object for the required type, if it has already been loaded
        ServiceDiscovery sd = discoveryInstances.get(discoveryType);
        if (sd == null) {
            sd = ServiceDiscoveryFactory.get(discoveryType, gatewayServices);
            discoveryInstances.put(discoveryType, sd);
        }
        ServiceDiscovery.Cluster cluster = sd.discover(sdc, desc.getClusterName());

        List<String> validServiceNames = new ArrayList<>();

        Map<String, Map<String, String>> serviceParams = new HashMap<>();
        Map<String, List<String>>        serviceURLs   = new HashMap<>();

        if (cluster != null) {
            for (SimpleDescriptor.Service descService : desc.getServices()) {
                String serviceName = descService.getName();

                List<String> descServiceURLs = descService.getURLs();
                if (descServiceURLs == null || descServiceURLs.isEmpty()) {
                    descServiceURLs = cluster.getServiceURLs(serviceName);
                }

                // Validate the discovered service URLs
                List<String> validURLs = new ArrayList<>();
                if (descServiceURLs != null && !descServiceURLs.isEmpty()) {
                    // Validate the URL(s)
                    for (String descServiceURL : descServiceURLs) {
                        if (validateURL(serviceName, descServiceURL)) {
                            validURLs.add(descServiceURL);
                        }
                    }

                    if (!validURLs.isEmpty()) {
                        validServiceNames.add(serviceName);
                    }
                }

                // If there is at least one valid URL associated with the service, then add it to the map
                if (!validURLs.isEmpty()) {
                    serviceURLs.put(serviceName, validURLs);
                } else {
                    log.failedToDiscoverClusterServiceURLs(serviceName, cluster.getName());
                }

                // Service params
                if (descService.getParams() != null) {
                    serviceParams.put(serviceName, descService.getParams());
                    if (!validServiceNames.contains(serviceName)) {
                        validServiceNames.add(serviceName);
                    }
                }
            }
        } else {
            log.failedToDiscoverClusterServices(desc.getClusterName());
        }

        // Provision the query param encryption password here, rather than relying on the random password generated
        // when the topology is deployed. This is to support Knox HA deployments, where multiple Knox instances are
        // generating topologies based on a shared remote descriptor, and they must all be able to encrypt/decrypt
        // query params with the same credentials. (KNOX-1136)
        if (!provisionQueryParamEncryptionCredential(desc.getName())) {
            log.unableCreatePasswordForEncryption(desc.getName());
        }

        BufferedWriter fw = null;
        topologyDescriptor = null;
        File providerConfig;
        try {
            // Verify that the referenced provider configuration exists before attempting to reading it
            providerConfig = resolveProviderConfigurationReference(desc.getProviderConfig(), srcDirectory);
            if (providerConfig == null) {
                log.failedToResolveProviderConfigRef(desc.getProviderConfig());
                throw new IllegalArgumentException("Unresolved provider configuration reference: " +
                                                   desc.getProviderConfig() + " ; Topology update aborted!");
            }
            result.put("reference", providerConfig);

            // TODO: Should the contents of the provider config be validated before incorporating it into the topology?

            String topologyFilename = desc.getName();
            if (topologyFilename == null) {
                topologyFilename = desc.getClusterName();
            }
            topologyDescriptor = new File(destDirectory, topologyFilename + ".xml");

            fw = new BufferedWriter(new FileWriter(topologyDescriptor));

            fw.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");

            fw.write("<!--==============================================-->\n");
            fw.write("<!-- DO NOT EDIT. This is an auto-generated file. -->\n");
            fw.write("<!--==============================================-->\n");

            fw.write("<topology>\n");

            // KNOX-1105 Indicate that this topology was auto-generated
            fw.write("    <generated>true</generated>\n");

            // Copy the externalized provider configuration content into the topology descriptor in-line
            InputStreamReader policyReader = new InputStreamReader(new FileInputStream(providerConfig));
            char[] buffer = new char[1024];
            int count;
            while ((count = policyReader.read(buffer)) > 0) {
                fw.write(buffer, 0, count);
            }
            policyReader.close();

            // Services
            // Sort the service names to write the services alphabetically
            List<String> serviceNames = new ArrayList<>(validServiceNames);
            Collections.sort(serviceNames);

            // Write the service declarations
            for (String serviceName : serviceNames) {
                fw.write("\n");
                fw.write("    <service>\n");
                fw.write("        <role>" + serviceName + "</role>\n");

                // URLs
                List<String> urls = serviceURLs.get(serviceName);
                if (urls != null) {
                    for (String url : urls) {
                        fw.write("        <url>" + url + "</url>\n");
                    }
                }

                // Params
                Map<String, String> svcParams = serviceParams.get(serviceName);
                if (svcParams != null) {
                    for (String paramName : svcParams.keySet()) {
                        fw.write("        <param>\n");
                        fw.write("            <name>" + paramName + "</name>\n");
                        fw.write("            <value>" + svcParams.get(paramName) + "</value>\n");
                        fw.write("        </param>\n");
                    }
                }

                fw.write("    </service>\n");
            }

            // Applications
            List<SimpleDescriptor.Application> apps = desc.getApplications();
            if (apps != null) {
                for (SimpleDescriptor.Application app : apps) {
                    fw.write("    <application>\n");
                    fw.write("        <name>" + app.getName() + "</name>\n");

                    // URLs
                    List<String> urls = app.getURLs();
                    if (urls != null) {
                        for (String url : urls) {
                            fw.write("        <url>" + url + "</url>\n");
                        }
                    }

                    // Params
                    Map<String, String> appParams = app.getParams();
                    if (appParams != null) {
                        for (String paramName : appParams.keySet()) {
                            fw.write("        <param>\n");
                            fw.write("            <name>" + paramName + "</name>\n");
                            fw.write("            <value>" + appParams.get(paramName) + "</value>\n");
                            fw.write("        </param>\n");
                        }
                    }

                    fw.write("    </application>\n");
                }
            }

            fw.write("</topology>\n");

            fw.flush();
        } catch (IOException e) {
            log.failedToGenerateTopologyFromSimpleDescriptor(topologyDescriptor.getName(), e);
            topologyDescriptor.delete();
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        result.put("topology", topologyDescriptor);
        return result;
    }


    /**
     * KNOX-1136
     *
     * Provision the query string encryption password prior to it being randomly generated during the topology
     * deployment.
     *
     * @param topologyName The name of the topology for which the credential will be provisioned.
     *
     * @return true if the credential was successfully provisioned; otherwise, false.
     */
    private static boolean provisionQueryParamEncryptionCredential(String topologyName) {
        boolean result = false;

        try {
            GatewayServices services = GatewayServer.getGatewayServices();
            if (services != null) {
                MasterService ms = services.getService("MasterService");
                if (ms != null) {
                    KeystoreService ks = services.getService(GatewayServices.KEYSTORE_SERVICE);
                    if (ks != null) {
                        if (!ks.isCredentialStoreForClusterAvailable(topologyName)) {
                            ks.createCredentialStoreForCluster(topologyName);
                        }

                        // If the credential store existed, or it was just successfully created
                        if (ks.getCredentialStoreForCluster(topologyName) != null) {
                            AliasService aliasService = services.getService(GatewayServices.ALIAS_SERVICE);
                            if (aliasService != null) {
                                // Derive and set the query param encryption password
                                String queryEncryptionPass = new String(ms.getMasterSecret()) + topologyName;
                                aliasService.addAliasForCluster(topologyName, "encryptQueryString", queryEncryptionPass);
                                result = true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.exceptionCreatingPasswordForEncryption(topologyName, e);
        }

        return result;
    }


    private static boolean validateURL(String serviceName, String url) {
        boolean result = false;

        if (url != null && !url.isEmpty()) {
            try {
                new URI(url);
                result = true;
            } catch (URISyntaxException e) {
                log.serviceURLValidationFailed(serviceName, url, e);
            }
        }

        return result;
    }


    private static File resolveProviderConfigurationReference(String reference, File srcDirectory) {
        File providerConfig;

        // If the reference includes a path
        if (reference.contains(File.separator)) {
            // Check if it's an absolute path
            providerConfig = new File(reference);
            if (!providerConfig.exists()) {
                // If it's not an absolute path, try treating it as a relative path
                providerConfig = new File(srcDirectory, reference);
                if (!providerConfig.exists()) {
                    providerConfig = null;
                }
            }
        } else { // No file path, just a name
            // Check if it's co-located with the referencing descriptor
            providerConfig = new File(srcDirectory, reference);
            if (!providerConfig.exists()) {
                // Check the shared-providers config location
                File sharedProvidersDir = new File(srcDirectory, "../shared-providers");
                if (sharedProvidersDir.exists()) {
                    providerConfig = new File(sharedProvidersDir, reference);
                    if (!providerConfig.exists()) {
                        // Check if it's a valid name without the extension
                        providerConfig = new File(sharedProvidersDir, reference + ".xml");
                        if (!providerConfig.exists()) {
                            providerConfig = null;
                        }
                    }
                }
            }
        }

        return providerConfig;
    }

}
