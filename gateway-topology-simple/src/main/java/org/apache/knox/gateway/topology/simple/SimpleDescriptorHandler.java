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
package org.apache.knox.gateway.topology.simple;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.topology.discovery.DefaultServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryFactory;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private static final Map<String, ServiceDiscovery> discoveryInstances = new HashMap<>();

    private static final Set<String> ALLOWED_SERVICES_WITHOUT_URLS_AND_PARAMS = Collections.unmodifiableSet(Stream.of("KNOX", "KNOX-METADATA", "KNOXSSOUT", "KNOX-SESSION").collect(Collectors.toSet()));

    public static Map<String, File> handle(GatewayConfig config, File desc, File destDirectory, Service...gatewayServices) throws IOException {
        return handle(config, SimpleDescriptorFactory.parse(desc.getAbsolutePath()), desc.getParentFile(), destDirectory, gatewayServices);
    }

    public static Map<String, File> handle(GatewayConfig config, SimpleDescriptor desc, File srcDirectory, File destDirectory) {
        return handle(config, desc, srcDirectory, destDirectory, NO_GATEWAY_SERVICES);
    }

    public static Map<String, File> handle(GatewayConfig config, SimpleDescriptor desc, File srcDirectory, File destDirectory, Service...gatewayServices) {

        List<String> declaredServiceNames = new ArrayList<>();
        Set<String> validServiceNames = new TreeSet<>();
        Map<String, String> serviceVersions = new HashMap<>();
        Map<String, Map<String, String>> serviceParams = new HashMap<>();
        Map<String, List<String>> serviceURLs = new HashMap<>();

        ServiceDiscovery.Cluster cluster = null;
        if (shouldPerformDiscovery(desc)) {
            cluster = performDiscovery(config, desc, gatewayServices);
            if (cluster == null) {
                throw new DiscoveryException(desc.getCluster(), desc.getName());
            }
        } else {
            log.discoveryNotConfiguredForDescriptor(desc.getName());
        }

        for (SimpleDescriptor.Service descService : desc.getServices()) {
            String serviceName = descService.getName();
            declaredServiceNames.add(serviceName);

            String serviceVer = descService.getVersion();
            if (serviceVer != null) {
                serviceVersions.put(serviceName, serviceVer);
            }

            List<String> descServiceURLs = descService.getURLs();
            if (descServiceURLs == null || descServiceURLs.isEmpty()) {
                if (cluster != null) {
                    descServiceURLs = cluster.getServiceURLs(serviceName, descService.getParams());
                }
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
                log.failedToDiscoverClusterServiceURLs(serviceName, (cluster != null ? cluster.getName() : ""));
            }

            // Service params
            Map<String, String> descriptorServiceParams = descService.getParams();
            if (descriptorServiceParams != null && !descriptorServiceParams.isEmpty()) {
                boolean hasNonDiscoveryParams = false;
                // Determine if there are any params which are not discovery-only
                for (String paramName : descriptorServiceParams.keySet()) {
                    if (!paramName.startsWith(SimpleDescriptor.DISCOVERY_PARAM_PREFIX)) {
                        hasNonDiscoveryParams = true;
                        break;
                    }
                }
                // Don't add the service if the only params are discovery-only params
                if (hasNonDiscoveryParams) {
                    serviceParams.put(serviceName, descService.getParams());
                    validServiceNames.add(serviceName);
                }
            }

            if (ALLOWED_SERVICES_WITHOUT_URLS_AND_PARAMS.contains(serviceName)) {
              validServiceNames.add(serviceName);
            }
        }

        GatewayServices gws = getGatewayServices(gatewayServices);

        // Provision the query param encryption password here, rather than relying on the random password generated
        // when the topology is deployed. This is to support Knox HA deployments, where multiple Knox instances are
        // generating topologies based on a shared remote descriptor, and they must all be able to encrypt/decrypt
        // query params with the same credentials. (KNOX-1136)
        if (!provisionQueryParamEncryptionCredential(desc.getName(), gws)) {
            log.unableCreatePasswordForEncryption(desc.getName());
        }

        // Generate the topology file
        return generateTopology(desc,
                                srcDirectory,
                                destDirectory,
                                cluster,
                                declaredServiceNames,
                                validServiceNames,
                                serviceVersions,
                                serviceURLs,
                                serviceParams,
                                gws);
    }

    /**
     * Determine whether discovery should be performed for the specified descriptor.
     *
     * @param desc A SimpleDescriptor
     * @return true, if discovery should be performed for the descriptor; Otherwise, false.
     */
    private static boolean shouldPerformDiscovery(final SimpleDescriptor desc) {
        // If there is a discovery type specified, then discovery should be performed
        final String discoveryType = desc.getDiscoveryType();
        if (discoveryType != null && !discoveryType.isEmpty()) {
            return true;
        }
        log.missingDiscoveryTypeInDescriptor(desc.getName());
        return false;
    }

    private static GatewayServices getGatewayServices(Service... services) {
      for (Service service : services) {
        if (service instanceof GatewayServices) {
          return (GatewayServices) service;
        }
      }
      return null;
    }

    private static ServiceDiscovery.Cluster performDiscovery(GatewayConfig config, SimpleDescriptor desc, Service...gatewayServices) {
        DefaultServiceDiscoveryConfig sdc = new DefaultServiceDiscoveryConfig(desc.getDiscoveryAddress());
        sdc.setCluster(desc.getCluster());
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
            if (sd == null) {
                throw new IllegalArgumentException("Unsupported service discovery type: " + discoveryType);
            }
            discoveryInstances.put(discoveryType, sd);
        }

        return sd.discover(config, sdc, desc.getCluster());
    }


    private static ProviderConfiguration handleProviderConfiguration(SimpleDescriptor desc, File providerConfig) {
        // Verify that the referenced provider configuration exists before attempting to read it
        if (providerConfig == null || !providerConfig.exists()) {
            log.failedToResolveProviderConfigRef(desc.getProviderConfig());
            throw new IllegalArgumentException("Unresolved provider configuration reference: " + desc.getProviderConfig());
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
    private static boolean provisionQueryParamEncryptionCredential(final String topologyName, final GatewayServices services) {
        boolean result = false;

        try {
            if (services != null) {
                MasterService ms = services.getService(ServiceType.MASTER_SERVICE);
                if (ms != null) {
                    KeystoreService ks = services.getService(ServiceType.KEYSTORE_SERVICE);
                    if (ks != null) {
                        if (!ks.isCredentialStoreForClusterAvailable(topologyName)) {
                            ks.createCredentialStoreForCluster(topologyName);
                        }

                        // If the credential store existed, or it was just successfully created
                        if (ks.getCredentialStoreForCluster(topologyName) != null) {
                            AliasService aliasService = services.getService(ServiceType.ALIAS_SERVICE);
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
     * @param serviceVersions   The versions of the services; optional attribute.
     * @param serviceURLs       The URLs associated with the valid service names.
     * @param serviceParams     The params associated with the valid service names.
     *
     * @return A Map with the generated topology file and the referenced provider configuration.
     */
    private static Map<String, File> generateTopology(final SimpleDescriptor desc,
                                                      final File srcDirectory,
                                                      final File destDirectory,
                                                      final ServiceDiscovery.Cluster cluster,
                                                      final List<String> declaredServiceNames,
                                                      final Set<String> validServiceNames,
                                                      final Map<String, String> serviceVersions,
                                                      final Map<String, List<String>> serviceURLs,
                                                      final Map<String, Map<String, String>> serviceParams,
                                                      final GatewayServices gwServices) {
        Map<String, File> result = new HashMap<>();
        File topologyDescriptor = null;
        try (StringWriter sw = new StringWriter()) {
            // Handle the referenced provider configuration
            File providerConfigFile = null;
            ProviderConfiguration providerConfiguration = null;
            String providerConfigReference = desc.getProviderConfig();
            if (providerConfigReference != null) {
                // Resolve and parse the referenced provider configuration
                providerConfigFile = resolveProviderConfigurationReference(providerConfigReference, srcDirectory);
                providerConfiguration = handleProviderConfiguration(desc, providerConfigFile);
            }
            if (providerConfiguration == null) {
                throw new IllegalArgumentException("Invalid provider configuration: " + providerConfigReference);
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
            Set<String> serviceNames = new TreeSet<>(validServiceNames);

            // Add any declared services, which were not validated, but which have ZK-based HA provider config
            for (String haServiceName : haServiceParams.keySet()) {
                // If the service configured for HA was declared in the descriptor, then add it to the services to be
                // serialized (if it's not already included)
                if (declaredServiceNames.contains(haServiceName)) {
                    if (!serviceNames.contains(haServiceName)) {
                        serviceNames.add(haServiceName);
                    }
                }
            }

            // Write the service declarations
            for (String serviceName : serviceNames) {
                sw.write("\n");
                sw.write("    <service>\n");
                sw.write("        <role>" + serviceName + "</role>\n");

                if (serviceVersions.containsKey(serviceName)) {
                    sw.write("        <version>" + serviceVersions.get(serviceName) + "</version>\n");
                }

                // Add the service params to the map for serialization
                Map<String,String> params = serviceParams.computeIfAbsent(serviceName, k -> new HashMap<>());

                ServiceDiscovery.Cluster.ZooKeeperConfig zkConf = haServiceParams.get(serviceName);

                // Determine whether to persist the haEnabled param, and to what value
                boolean isServiceHaEnabledAuto = false;
                boolean isServiceHaEnabled = false;

                if (haProvider != null) {
                    Map<String, String> haParams = haProvider.getParams();
                    if (haParams != null && haParams.containsKey(serviceName)) {
                        String serviceHaParams = haParams.get(serviceName);
                        Map<String,String> parsedServiceHaParams = parseHaProviderParam(serviceHaParams);
                        String enabledValue = parsedServiceHaParams.get("enabled");
                        if (enabledValue != null) {
                            if ("auto".equalsIgnoreCase(enabledValue)) {
                                isServiceHaEnabledAuto = true;
                                isServiceHaEnabled = (zkConf != null && zkConf.isEnabled());
                            } else {
                                isServiceHaEnabled = "true".equalsIgnoreCase(enabledValue);
                            }
                        }
                    }
                }

                // If the HA provider configuration for this service indicates an enabled value of "auto", then
                // persist the derived enabled value.
                if (isServiceHaEnabledAuto) {
                    params.put(org.apache.knox.gateway.topology.Service.HA_ENABLED_PARAM,
                               String.valueOf(isServiceHaEnabled));
                }

                // If the service is configured for ZooKeeper-based HA
                if (zkConf != null && zkConf.getEnsemble() != null && isServiceHaEnabled) {
                    String ensemble = zkConf.getEnsemble();
                    if (ensemble != null && !ensemble.isEmpty()) {
                        params.put("zookeeperEnsemble", ensemble);
                    }

                    String namespace = zkConf.getNamespace();
                    if (namespace != null && !namespace.isEmpty() ) {
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
                    for (Entry<String, String> svcParam : svcParams.entrySet()) {
                        if (!(svcParam.getKey().toLowerCase(Locale.ROOT)).startsWith(SimpleDescriptor.DISCOVERY_PARAM_PREFIX)) {
                            sw.write("        <param>\n");
                            sw.write("            <name>" + svcParam.getKey() + "</name>\n");
                            sw.write("            <value>" + svcParam.getValue() + "</value>\n");
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
                        for (Entry<String, String> entry : appParams.entrySet()) {
                            sw.write("        <param>\n");
                            sw.write("            <name>" + entry.getKey() + "</name>\n");
                            sw.write("            <value>" + entry.getValue() + "</value>\n");
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
                topologyFilename = desc.getCluster();
            }

            topologyDescriptor = new File(destDirectory, topologyFilename + ".xml");

            if (shouldPersistGeneratedTopology(topologyFilename, topologyDescriptor, sw.toString(), gwServices)) {
                log.persistingGeneratedTopology(topologyFilename);
                try (OutputStream outputStream = Files.newOutputStream(topologyDescriptor.toPath());
                     OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                     BufferedWriter fw = new BufferedWriter(outputStreamWriter)) {
                    fw.write(sw.toString());
                    fw.flush();
                }
            } else {
                log.skippingDeploymentOfGeneratedTopology(topologyFilename); // KNOX-2302
            }
        } catch (IOException e) {
            log.failedToGenerateTopologyFromSimpleDescriptor(topologyDescriptor.getName(), e);
            topologyDescriptor.delete();
        }

        result.put(RESULT_TOPOLOGY, topologyDescriptor);

        return result;
    }

    /**
     * Determine whether or not the generated content of the specified topology should be persisted.
     *
     * @param topologyName     The name of the topology
     * @param topologyFile     A File reference to the location of the persisted topology
     * @param generatedContent The generated topology content
     * @param gwServices       A GatewayServices reference
     *
     * @return true, if the generated topology should be persisted; Otherwise, false.
     */
    private static boolean shouldPersistGeneratedTopology(final String          topologyName,
                                                          final File            topologyFile,
                                                          final String          generatedContent,
                                                          final GatewayServices gwServices) {
        boolean result = false;

        // Determine if the topology already exists.
        if (topologyFile.exists()) {
            // If it does exist, only overwrite it if it has changed (KNOX-2302)
            // Compare the generated topology with the in-memory topology
            Topology existing = null;
            TopologyService topologyService = null;
            if (gwServices != null) {
                topologyService = gwServices.getService(ServiceType.TOPOLOGY_SERVICE);
                for (Topology t : topologyService.getTopologies()) {
                    if (topologyName.equals(t.getName())) {
                        existing = t;
                        break;
                    }
                }
            }

            if (existing != null) {
                try (InputStream in = new ByteArrayInputStream(generatedContent.getBytes(StandardCharsets.UTF_8))) {
                    Topology generatedTopology = topologyService.parse(in);
                    generatedTopology.setName(topologyName);
                    // If the generated topology is different from the existing, then it should be persisted
                    result = !existing.equals(generatedTopology);
                } catch (Exception e) {
                    log.errorComparingGeneratedTopology(topologyName, e);
                }
            } else {
                result = true; // If the existing Topology could not be accessed, treat it as a new one
            }
        } else {
            result = true; // It's a new topology, so persist it
        }
        return result;
    }

    private static Map<String, String> parseHaProviderParam(String paramValue) {
        Map<String, String> result = new HashMap<>();

        String[] elements = paramValue.split(";");
        if (elements.length > 0) {
            for (String element : elements) {
                String[] kv = element.split("=");
                if (kv.length == 2) {
                    result.put(kv[0], kv[1]);
                }
            }
        }

        return result;
    }

}
