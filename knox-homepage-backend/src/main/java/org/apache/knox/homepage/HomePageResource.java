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
package org.apache.knox.homepage;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.service.definition.Metadata;
import org.apache.knox.gateway.service.definition.ServiceDefinitionPair;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServerInfoService;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.registry.ServiceDefinitionRegistry;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.util.X509CertificateUtil;

@Path("/v1")
public class HomePageResource {
  private static final KnoxHomepageMessages LOG = MessagesFactory.get(KnoxHomepageMessages.class);
  private static final String SNAPSHOT_VERSION_POSTFIX = "-SNAPSHOT";
  private static final Set<String> UNREAL_SERVICES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("NAMENODE", "JOBTRACKER")));

  private java.nio.file.Path pemFilePath;
  private java.nio.file.Path jksFilePath;

  @Context
  private HttpServletRequest request;

  @GET
  @Produces({ APPLICATION_JSON, APPLICATION_XML })
  @Path("info")
  public GeneralProxyInformation getGeneralProxyInformation() {
    final GeneralProxyInformation proxyInfo = new GeneralProxyInformation();
    final GatewayServices gatewayServices = (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    if (gatewayServices != null) {
      final ServerInfoService serviceInfoService = gatewayServices.getService(ServiceType.SERVER_INFO_SERVICE);
      final String versionInfo = serviceInfoService.getBuildVersion() + " (hash=" + serviceInfoService.getBuildHash() + ")";
      proxyInfo.setVersion(versionInfo);
      proxyInfo.setAdminApiBookUrl(
          String.format(Locale.ROOT, "https://knox.apache.org/books/knox-%s/user-guide.html#Admin+API", getAdminApiBookVersion(serviceInfoService.getBuildVersion())));
      final GatewayConfig config = (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
      final Certificate certificate = getPublicCertificate(gatewayServices, config);
      if (certificate != null) {
        generateCertificatePem(certificate, config);
        proxyInfo.setPublicCertPemPath("assets/gateway-client-trust.pem");
        generateCertificateJks(certificate, config);
        proxyInfo.setPublicCertJksPath("assets/gateway-client-trust.jks");
      } else {
        proxyInfo.setPublicCertPemPath("Could not generate gateway-client-trust.pem");
        proxyInfo.setPublicCertJksPath("Could not generate gateway-client-trust.jks");
      }
      proxyInfo.setAdminUiUrl(getBaseGatewayUrl(config) + "/manager/admin-ui/");
    }

    return proxyInfo;
  }

  private String getAdminApiBookVersion(String buildVersion) {
    return buildVersion.replaceAll(SNAPSHOT_VERSION_POSTFIX, "").replaceAll("\\.", "-");
  }

  private Certificate getPublicCertificate(GatewayServices gatewayServices, GatewayConfig config) {
    try {
      final KeystoreService keystoreService = gatewayServices.getService(ServiceType.KEYSTORE_SERVICE);
      return keystoreService.getKeystoreForGateway().getCertificate(config.getIdentityKeyAlias());
    } catch (KeyStoreException | KeystoreServiceException e) {
      LOG.failedToFetchPublicCert(e.getMessage(), e);
      return null;
    }
  }

  private void generateCertificatePem(Certificate certificate, GatewayConfig gatewayConfig) {
    try {
      if (pemFilePath == null || !pemFilePath.toFile().exists()) {
        pemFilePath = Paths.get(gatewayConfig.getGatewayDeploymentDir(), "homepage", "%2Fhome", "assets", "gateway-client-trust.pem");
        X509CertificateUtil.writeCertificateToFile(certificate, pemFilePath.toFile());
      }
    } catch (CertificateEncodingException | IOException e) {
      LOG.failedToGeneratePublicCert("PEM", e.getMessage(), e);
    }
  }

  private void generateCertificateJks(Certificate certificate, GatewayConfig gatewayConfig) {
    try {
      if (jksFilePath == null || !jksFilePath.toFile().exists()) {
        jksFilePath = Paths.get(gatewayConfig.getGatewayDeploymentDir(), "homepage", "%2Fhome", "assets", "gateway-client-trust.jks");
        X509CertificateUtil.writeCertificateToJks(certificate, jksFilePath.toFile());
      }
    } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
      LOG.failedToGeneratePublicCert("JKS", e.getMessage(), e);
    }
  }

  private String getBaseGatewayUrl(GatewayConfig config) {
    return request.getRequestURL().substring(0, request.getRequestURL().length() - request.getRequestURI().length()) + "/" + config.getGatewayPath();
  }

  @GET
  @Produces({ APPLICATION_JSON, APPLICATION_XML })
  @Path("topologies")
  public TopologyInformationWrapper getTopologies() {
    return getTopologies(null);
  }

  @GET
  @Produces({ APPLICATION_JSON, APPLICATION_XML })
  @Path("topologies/{name}")
  public TopologyInformationWrapper getTopology(@PathParam("name") String topologyName) {
    return getTopologies(topologyName);
  }

  private TopologyInformationWrapper getTopologies(String topologyName) {
    final TopologyInformationWrapper topologies = new TopologyInformationWrapper();
    final GatewayServices gatewayServices = (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    final GatewayConfig config = (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
    final ServiceDefinitionRegistry serviceDefinitionRegistry = gatewayServices.getService(ServiceType.SERVICE_DEFINITION_REGISTRY);
    final Set<String> hiddenTopologies = config.getHiddenTopologiesOnHomepage();
    if (gatewayServices != null) {
      final TopologyService topologyService = gatewayServices.getService(ServiceType.TOPOLOGY_SERVICE);
      for (Topology topology : topologyService.getTopologies()) {
        if (!hiddenTopologies.contains(topology.getName()) && (topologyName == null || topology.getName().equalsIgnoreCase(topologyName))) {
          List<ServiceModel> apiServices = new ArrayList<>();
          List<ServiceModel> uiServices = new ArrayList<>();
          topology.getServices().stream().filter(service -> !UNREAL_SERVICES.contains(service.getRole())).forEach(service -> {
            service.getUrls().forEach(serviceUrl -> {
              ServiceModel serviceModel = getServiceModel(request, config.getGatewayPath(), topology.getName(), service, getServiceMetadata(serviceDefinitionRegistry, service));
              if (ServiceModel.Type.UI == serviceModel.getType()) {
                uiServices.add(serviceModel);
              } else {
                apiServices.add(serviceModel);
              }
            });
          });
          topologies.addTopology(topology.getName(), apiServices, uiServices);
        }
      }
    }
    return topologies;
  }

  private Metadata getServiceMetadata(ServiceDefinitionRegistry serviceDefinitionRegistry, Service service) {
    final Optional<ServiceDefinitionPair> serviceDefinition = serviceDefinitionRegistry.getServiceDefinitions().stream()
        .filter(serviceDefinitionPair -> serviceDefinitionPair.getService().getRole().equalsIgnoreCase(service.getRole())).findFirst();
    return serviceDefinition.isPresent() ? serviceDefinition.get().getService().getMetadata() : null;
  }

  private ServiceModel getServiceModel(HttpServletRequest request, String gatewayPath, String topologyName, Service service, Metadata serviceMetadata) {
    final ServiceModel serviceModel = new ServiceModel();
    serviceModel.setRequest(request);
    serviceModel.setGatewayPath(gatewayPath);
    serviceModel.setTopologyName(topologyName);
    serviceModel.setService(service);
    serviceModel.setServiceMetadata(serviceMetadata);
    return serviceModel;
  }

}
