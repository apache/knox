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
package org.apache.knox.gateway.topology.simple;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.knox.gateway.GatewayServer;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.topology.discovery.DefaultServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryFactory;


/**
 * Processes simple topology descriptors, producing full topology files, which can subsequently be deployed to the
 * gateway.
 */
public class SimpleDescriptorHandler {

    /**
     * The name of the property in the result Map for the topology file.
     */
    public static final String RESULT_TOPOLOGY  = "topology";

    /**
     * The name of the property in the result Map for the provider configuration file applied to the generated topology.
     */
    public static final String RESULT_REFERENCE = "reference";

    private static final String DEFAULT_DISCOVERY_TYPE = "AMBARI";

    private static final String[] PROVIDER_CONFIG_FILE_EXTENSIONS;
    static {

        PROVIDER_CONFIG_FILE_EXTENSIONS = new String[ProviderConfigurationParser.SUPPORTED_EXTENSIONS.size()];
        int i = 0;
        for (String ext : ProviderConfigurationParser.SUPPORTED_EXTENSIONS) {
            PROVIDER_CONFIG_FILE_EXTENSIONS[i++] = "." + ext;
        }
    }

    private static final Service[] NO_GATEWAY_SERVICES = new Service[]{};

    private static final SimpleDescriptorMessages log = MessagesFactory.get(SimpleDescriptorMessages.class);

    private static final String DISCOVERY_PARAM_PREFIX = "discovery-";

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

        List<String>                     validServiceNames = new ArrayList<>();
        Map<String, Map<String, String>> serviceParams     = new HashMap<>();
        Map<String, List<String>>        serviceURLs       = new HashMap<>();

        // Discover the cluster details required by the descriptor
        ServiceDiscovery.Cluster cluster = performDiscovery(desc, gatewayServices);
        if (cluster != null) {
            for (SimpleDescriptor.Service descService : desc.getServices()) {
                String serviceName = descService.getName();

                List<String> descServiceURLs = descService.getURLs();
                if (descServiceURLs == null || descServiceURLs.isEmpty()) {
                    descServiceURLs = cluster.getServiceURLs(serviceName, descService.getParams());
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

        // Generate the topology file
        return generateTopology(desc, srcDirectory, destDirectory, cluster, validServiceNames, serviceURLs, serviceParams);
    }


    private static ServiceDiscovery.Cluster performDiscovery(SimpleDescriptor desc, Service...gatewayServices) {
        DefaultServiceDiscoveryConfig sdc = new DefaultServiceDiscoveryConfig(desc.getDiscoveryAddress());
        sdc.setUser(desc.getDiscoveryUser());
        sdc.setPasswordAlias(desc.getDiscoveryPasswordAlias());

        // Use the discovery type from the descriptor. If it's unspecified, employ the default type.
        String discoveryType = desc.getDiscoveryType();
        if (discoveryType == null) {
            discoveryType = DEFAULT_DISCOVERY_TYPE;
        }

        // Use the cached discovery object for the required type, if it has already been loaded
        ServiceDiscovery sd = discoveryInstances.get(discoveryType);
        if (sd == null) {
            sd = ServiceDiscoveryFactory.get(discoveryType, gatewayServices);
            discoveryInstances.put(discoveryType, sd);
        }

        return sd.discover(sdc, desc.getClusterName());
    }


    private static ProviderConfiguration handleProviderConfiguration(SimpleDescriptor desc, File providerConfig) {
        // Verify that the referenced provider configuration exists before attempting to read it
        if (providerConfig == null) {
            log.failedToResolveProviderConfigRef(desc.getProviderConfig());
            throw new IllegalArgumentException("Unresolved provider configuration reference: " +
                                               desc.getProviderConfig() + " ; Topology update aborted!");
        }

        // Parse the contents of the referenced provider config
        ProviderConfiguration parsedConfig = null;

        try {
            parsedConfig = ProviderConfigurationParser.parse(providerConfig);
        } catch (Exception e) {
            log.failedToParseProviderConfig(providerConfig.getAbsolutePath(), e);
        }

        return parsedConfig;
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
    private static boolean provisionQueryParamEncryptionCredential(final String topologyName) {
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


    private static boolean validateURL(final String serviceName, final String url) {
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


    private static File resolveProviderConfigurationReference(final String reference, final File srcDirectory) {
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
                    // Check if it's a valid name without the extension
                    providerConfig = new File(sharedProvidersDir, reference);
                    if (!providerConfig.exists()) {
                        // Check the supported file extensions to see if the reference can be resolved
                        for (String ext : PROVIDER_CONFIG_FILE_EXTENSIONS) {
                            providerConfig = new File(sharedProvidersDir, reference + ext);
                            if (providerConfig.exists()) {
                                break;
                            }
                            providerConfig = null;
                        }
                    }
                }
            }
        }

        return providerConfig;
    }


    /**
     * Generate a topology file, driven by the specified simple descriptor.
     *
     * @param desc              The simple descriptor driving the topology generation.
     * @param srcDirectory      The source directory of the simple descriptor.
     * @param destDirectory     The destination directory for the generated topology file.
     * @param cluster           The discovery details for the referenced cluster.
     * @param validServiceNames The validated service names.
     * @param serviceURLs       The URLs associated with the valid service names.
     * @param serviceParams     The params associated with the valid service names.
     *
     * @return A Map with the generated topology file and the referenced provider configuration.
     */
    private static Map<String, File> generateTopology(final SimpleDescriptor desc,
                                                      final File srcDirectory,
                                                      final File destDirectory,
                                                      final ServiceDiscovery.Cluster cluster,
                                                      final List<String> validServiceNames,
                                                      final Map<String, List<String>> serviceURLs,
                                                      final Map<String, Map<String, String>> serviceParams) {
        Map<String, File> result = new HashMap<>();

        BufferedWriter fw = null;
        File topologyDescriptor = null;
        try {
            // Resolve and parse the referenced provider configuration
            File providerConfigFile = resolveProviderConfigurationReference(desc.getProviderConfig(), srcDirectory);
            ProviderConfiguration providerConfiguration = handleProviderConfiguration(desc, providerConfigFile);
            if (providerConfiguration == null) {
                throw new IllegalArgumentException("Invalid provider configuration.");
            }
            result.put(RESULT_REFERENCE, providerConfigFile);

            ProviderConfiguration.Provider haProvider = null;
            for (ProviderConfiguration.Provider provider : providerConfiguration.getProviders()) {
                if ("ha".equals(provider.getRole())) {
                    haProvider = provider;
                    break;
                }
            }

            // Collect HA-related service parameters
            Map<String, ServiceDiscovery.Cluster.ZooKeeperConfig> haServiceParams = new HashMap<>();
            if (cluster != null) {
                if (haProvider != null) {
                    // Collect tne roles declared by the HaProvider
                    Map<String, String> haProviderParams = haProvider.getParams();
                    if (haProviderParams != null) {
                        Set<String> haProviderRoles = haProviderParams.keySet();
                        for (String haProviderRole : haProviderRoles) {
                            // For each role declared by the HaProvider, which supports ZooKeeper, try to get
                            // the ZK ensemble and namespace from the cluster.
                            ServiceDiscovery.Cluster.ZooKeeperConfig zkConfig =
                                cluster.getZooKeeperConfiguration(haProviderRole);
                            if (zkConfig != null) {
                                haServiceParams.put(haProviderRole, zkConfig);
                            }
                        }
                    }
                }
            }

            // Generate the topology content
            StringWriter sw = new StringWriter();

            sw.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            sw.write("<!--==============================================-->\n");
            sw.write("<!-- DO NOT EDIT. This is an auto-generated file. -->\n");
            sw.write("<!--==============================================-->\n");

            sw.write("<topology>\n");

            // KNOX-1105 Indicate that this topology was auto-generated
            sw.write("    <generated>true</generated>\n");

            // Incorporate the externalized provider configuration content into the topology descriptor
            sw.write("    <gateway>\n");
            for (ProviderConfiguration.Provider provider : providerConfiguration.getProviders()) {
                sw.write("        <provider>\n");
                sw.write("            <role>" + provider.getRole() + "</role>\n");
                sw.write("            <name>" + provider.getName() + "</name>\n");
                sw.write("            <enabled>" + provider.isEnabled() + "</enabled>\n");

                for (Map.Entry<String, String> param : provider.getParams().entrySet()) {
                    sw.write("            <param>\n");
                    sw.write("                <name>" + param.getKey() + "</name>\n");
                    sw.write("                <value>" + param.getValue() + "</value>\n");
                    sw.write("            </param>\n");
                }

                sw.write("        </provider>\n");
            }
            sw.write("    </gateway>\n");

            // Services
            // Sort the service names to write the services alphabetically
            List<String> serviceNames = new ArrayList<>(validServiceNames);
            Collections.sort(serviceNames);

            // Write the service declarations
            for (String serviceName : serviceNames) {
                sw.write("\n");
                sw.write("    <service>\n");
                sw.write("        <role>" + serviceName + "</role>\n");

                // If the service is configured for ZooKeeper-based HA
                ServiceDiscovery.Cluster.ZooKeeperConfig zkConf = haServiceParams.get(serviceName);
                if (zkConf != null && zkConf.isEnabled() && zkConf.getEnsemble() != null) {
                    // Add the zookeeper params to the map for serialization
                    Map<String,String> params = serviceParams.computeIfAbsent(serviceName, k -> new HashMap<>());

                    String ensemble = zkConf.getEnsemble();
                    if (ensemble != null) {
                        params.put("zookeeperEnsemble", ensemble);
                    }

                    String namespace = zkConf.getNamespace();
                    if (namespace != null) {
                        params.put("zookeeperNamespace", namespace);
                    }
                } else {
                    // Serialize the service URLs
                    List<String> urls = serviceURLs.get(serviceName);
                    if (urls != null) {
                        for (String url : urls) {
                            sw.write("        <url>" + url + "</url>\n");
                        }
                    }
                }

                // Params
                Map<String, String> svcParams = serviceParams.get(serviceName);
                if (svcParams != null) {
                    for (String paramName : svcParams.keySet()) {
                        if (!(paramName.toLowerCase()).startsWith(DISCOVERY_PARAM_PREFIX)) {
                            sw.write("        <param>\n");
                            sw.write("            <name>" + paramName + "</name>\n");
                            sw.write("            <value>" + svcParams.get(paramName) + "</value>\n");
                            sw.write("        </param>\n");
                        }
                    }
                }

                sw.write("    </service>\n");
            }

            // Applications
            List<SimpleDescriptor.Application> apps = desc.getApplications();
            if (apps != null) {
                for (SimpleDescriptor.Application app : apps) {
                    sw.write("    <application>\n");
                    sw.write("        <name>" + app.getName() + "</name>\n");

                    // URLs
                    List<String> urls = app.getURLs();
                    if (urls != null) {
                        for (String url : urls) {
                            sw.write("        <url>" + url + "</url>\n");
                        }
                    }

                    // Params
                    Map<String, String> appParams = app.getParams();
                    if (appParams != null) {
                        for (String paramName : appParams.keySet()) {
                            sw.write("        <param>\n");
                            sw.write("            <name>" + paramName + "</name>\n");
                            sw.write("            <value>" + appParams.get(paramName) + "</value>\n");
                            sw.write("        </param>\n");
                        }
                    }

                    sw.write("    </application>\n");
                }
            }

            sw.write("</topology>\n");

            // Write the generated content to a file
            String topologyFilename = desc.getName();
            if (topologyFilename == null) {
                topologyFilename = desc.getClusterName();
            }
            topologyDescriptor = new File(destDirectory, topologyFilename + ".xml");

            fw = new BufferedWriter(new FileWriter(topologyDescriptor));
            fw.write(sw.toString());
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

        result.put(RESULT_TOPOLOGY, topologyDescriptor);

        return result;
    }

}
