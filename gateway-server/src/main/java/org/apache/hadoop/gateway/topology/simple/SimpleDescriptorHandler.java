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

import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.services.Service;
import org.apache.hadoop.gateway.topology.discovery.DefaultServiceDiscoveryConfig;
import org.apache.hadoop.gateway.topology.discovery.ServiceDiscovery;
import org.apache.hadoop.gateway.topology.discovery.ServiceDiscoveryFactory;

import java.io.*;
import java.util.*;


/**
 * Processes simple topology descriptors, producing full topology files, which can subsequently be deployed to the
 * gateway.
 */
public class SimpleDescriptorHandler {

    private static final Service[] NO_GATEWAY_SERVICES = new Service[]{};

    private static final SimpleDescriptorMessages log = MessagesFactory.get(SimpleDescriptorMessages.class);

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
        ServiceDiscovery sd = ServiceDiscoveryFactory.get(desc.getDiscoveryType(), gatewayServices);
        ServiceDiscovery.Cluster cluster = sd.discover(sdc, desc.getClusterName());

        Map<String, List<String>> serviceURLs = new HashMap<>();

        if (cluster != null) {
            for (SimpleDescriptor.Service descService : desc.getServices()) {
                String serviceName = descService.getName();

                List<String> descServiceURLs = descService.getURLs();
                if (descServiceURLs == null || descServiceURLs.isEmpty()) {
                    descServiceURLs = cluster.getServiceURLs(serviceName);
                }

                // If there is at least one URL associated with the service, then add it to the map
                if (descServiceURLs != null && !descServiceURLs.isEmpty()) {
                    serviceURLs.put(serviceName, descServiceURLs);
                } else {
                    log.failedToDiscoverClusterServiceURLs(serviceName, cluster.getName());
                    throw new IllegalStateException("ServiceDiscovery failed to resolve any URLs for " + serviceName +
                                                    ". Topology update aborted!");
                }
            }
        } else {
            log.failedToDiscoverClusterServices(desc.getClusterName());
        }

        topologyDescriptor = null;
        File providerConfig = null;
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
            FileWriter fw = new FileWriter(topologyDescriptor);

            fw.write("<topology>\n");

            // Copy the externalized provider configuration content into the topology descriptor in-line
            InputStreamReader policyReader = new InputStreamReader(new FileInputStream(providerConfig));
            char[] buffer = new char[1024];
            int count;
            while ((count = policyReader.read(buffer)) > 0) {
                fw.write(buffer, 0, count);
            }
            policyReader.close();

            // Write the service declarations
            for (String serviceName : serviceURLs.keySet()) {
                fw.write("    <service>\n");
                fw.write("        <role>" + serviceName + "</role>\n");
                for (String url : serviceURLs.get(serviceName)) {
                    fw.write("        <url>" + url + "</url>\n");
                }
                fw.write("    </service>\n");
            }

            fw.write("</topology>\n");

            fw.flush();
            fw.close();
        } catch (IOException e) {
            log.failedToGenerateTopologyFromSimpleDescriptor(topologyDescriptor.getName(), e);
            topologyDescriptor.delete();
        }

        result.put("topology", topologyDescriptor);
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
