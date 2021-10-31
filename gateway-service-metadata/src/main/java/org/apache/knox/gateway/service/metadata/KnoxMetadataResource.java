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
package org.apache.knox.gateway.service.metadata;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.dto.HomePageProfile;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.service.definition.Metadata;
import org.apache.knox.gateway.service.definition.ServiceDefinitionPair;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServerInfoService;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.registry.ServiceDefinitionRegistry;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.token.impl.TokenMAC;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.util.JsonUtils;
import org.apache.knox.gateway.util.X509CertificateUtil;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api(value = "metadata",  description = "RESTful API to interact with metadata.")
@Singleton
@Path("/api/v1/metadata")
public class KnoxMetadataResource {
  private static final MetadataServiceMessages LOG = MessagesFactory.get(MetadataServiceMessages.class);
  private static final String SNAPSHOT_VERSION_POSTFIX = "-SNAPSHOT";
  private static final Set<String> UNREAL_SERVICES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList("NAMENODE", "JOBTRACKER", "RESOURCEMANAGERAPI")));

  private Set<String> pinnedTopologies;
  private java.nio.file.Path pemFilePath;
  private java.nio.file.Path jksFilePath;

  @Context
  private HttpServletRequest request;

  @ApiOperation(value="Get general proxy information", notes="Get general proxy information such as TLS Public Certificate, Knox Admin UI Url, etc...", response=GeneralProxyInformation.class)
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
      proxyInfo.setAdminUiUrl(getBaseGatewayUrl(config) + "/manager/admin-ui/");

      setTokenManagementEnabledFlag(proxyInfo, gatewayServices);
    }

    return proxyInfo;
  }

  private void setTokenManagementEnabledFlag(final GeneralProxyInformation proxyInfo, final GatewayServices gatewayServices) {
    try {
      final AliasService aliasService = gatewayServices.getService(ServiceType.ALIAS_SERVICE);
      final List<String> aliases = aliasService.getAliasesForCluster(AliasService.NO_CLUSTER_NAME);
      final boolean tokenManagementEnabled = aliases.contains(TokenMAC.KNOX_TOKEN_HASH_KEY_ALIAS_NAME);
      proxyInfo.setEnableTokenManagement(Boolean.toString(tokenManagementEnabled));
      if (!tokenManagementEnabled) {
        LOG.tokenManagementDisabled();
      }
    } catch (AliasServiceException e) {
      LOG.failedToFetchGatewayAliasList(e.getMessage(), e);
    }
  }

  private String getAdminApiBookVersion(String buildVersion) {
    return buildVersion.replaceAll(SNAPSHOT_VERSION_POSTFIX, "").replaceAll("\\.", "-");
  }

  @GET
  @Produces(APPLICATION_OCTET_STREAM)
  @Path("publicCert")
  public Response getPublicCertification(@QueryParam("type") @DefaultValue("pem") String certType) {
    final GatewayServices gatewayServices = (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    if (gatewayServices != null) {
      final GatewayConfig config = (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
      final Certificate certificate = getPublicCertificate(gatewayServices, config);
      if (certificate != null) {
        if ("pem".equals(certType)) {
          generateCertificatePem(certificate, config);
          return generateSuccessFileDownloadResponse(pemFilePath);
        } else if ("jks".equals(certType)) {
          generateCertificateJks(certificate, config);
          return generateSuccessFileDownloadResponse(jksFilePath);
        } else {
          return generateFailureFileDownloadResponse(Status.BAD_REQUEST, "Invalid certification type provided!");
        }
      }
    }
    return generateFailureFileDownloadResponse(Status.SERVICE_UNAVAILABLE, "Could not generate public certificate");
  }

  private Response generateSuccessFileDownloadResponse(java.nio.file.Path publicCertFilePath) {
    final ResponseBuilder responseBuilder = Response.ok(publicCertFilePath.toFile());
    responseBuilder.header("Content-Disposition", "attachment;filename=" + publicCertFilePath.getFileName().toString());
    return responseBuilder.build();
  }

  private Response generateFailureFileDownloadResponse(Status status, String errorMessage) {
    final ResponseBuilder responseBuilder = Response.status(status);
    responseBuilder.entity(errorMessage);
    return responseBuilder.build();
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
        pemFilePath = Paths.get(gatewayConfig.getGatewaySecurityDir(), "gateway-client-trust.pem");
        X509CertificateUtil.writeCertificateToFile(certificate, pemFilePath.toFile());
      }
    } catch (CertificateEncodingException | IOException e) {
      LOG.failedToGeneratePublicCert("PEM", e.getMessage(), e);
    }
  }

  private void generateCertificateJks(Certificate certificate, GatewayConfig gatewayConfig) {
    try {
      if (jksFilePath == null || !jksFilePath.toFile().exists()) {
        jksFilePath = Paths.get(gatewayConfig.getGatewaySecurityDir(), "gateway-client-trust.jks");
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
  @Produces({ APPLICATION_XML, APPLICATION_JSON })
  @Path("topologies")
  public TopologyInformationWrapper getTopologies() {
    return getTopologies(null);
  }

  @GET
  @Produces({ APPLICATION_XML, APPLICATION_JSON })
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
          Set<ServiceModel> apiServices = new HashSet<>();
          Set<ServiceModel> uiServices = new HashSet<>();
          topology.getServices().stream().filter(service -> !UNREAL_SERVICES.contains(service.getRole())).forEach(service -> {
            service.getUrls().forEach(serviceUrl -> {
              ServiceModel serviceModel = getServiceModel(request, config.getGatewayPath(), topology.getName(), service, getServiceMetadata(serviceDefinitionRegistry, service),
                  serviceUrl);
              if (ServiceModel.Type.UI == serviceModel.getType()) {
                uiServices.add(serviceModel);
              } else if (ServiceModel.Type.API_AND_UI == serviceModel.getType()) {
                uiServices.add(serviceModel);
                apiServices.add(serviceModel);
              } else {
                apiServices.add(serviceModel);
              }
            });
          });
          topologies.addTopology(topology.getName(), isPinnedTopology(topology.getName(), config), new TreeSet<>(apiServices), new TreeSet<>(uiServices));
        }
      }
    }
    return topologies;
  }

  boolean isPinnedTopology(String topologyName, GatewayConfig config) {
    if (pinnedTopologies == null) {
      pinnedTopologies = config.getPinnedTopologiesOnHomepage();
    }
    return pinnedTopologies.contains(topologyName);
  }

  private Metadata getServiceMetadata(ServiceDefinitionRegistry serviceDefinitionRegistry, Service service) {
    final Optional<ServiceDefinitionPair> serviceDefinition = serviceDefinitionRegistry.getServiceDefinitions().stream()
        .filter(serviceDefinitionPair -> serviceDefinitionPair.getService().getRole().equalsIgnoreCase(service.getRole()))
        .filter(serviceDefinitionPair -> service.getVersion() == null || service.getVersion().toString().equalsIgnoreCase(serviceDefinitionPair.getService().getVersion()))
        .findFirst();
    return serviceDefinition.isPresent() ? serviceDefinition.get().getService().getMetadata() : null;
  }

  private ServiceModel getServiceModel(HttpServletRequest request, String gatewayPath, String topologyName, Service service, Metadata serviceMetadata, String serviceUrl) {
    final ServiceModel serviceModel = new ServiceModel();
    serviceModel.setRequest(request);
    serviceModel.setGatewayPath(gatewayPath);
    serviceModel.setTopologyName(topologyName);
    serviceModel.setService(service);
    serviceModel.setServiceMetadata(serviceMetadata);
    serviceModel.setServiceUrl(serviceUrl);
    return serviceModel;
  }

  @GET
  @Produces({ APPLICATION_JSON })
  @Path("profiles/{profile}")
  public String getProfile(@PathParam("profile") String profileName) {
    final GatewayConfig config = (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
    final Map<String, Collection<String>> configuredProfiles = config.getHomePageProfiles();
    if (configuredProfiles.containsKey(profileName.toLowerCase(Locale.getDefault()))) {
      final HomePageProfile profile = new HomePageProfile(configuredProfiles.get(profileName));
      return JsonUtils.renderAsJsonString(profile.getProfileElements());
    } else {
      return JsonUtils.renderAsJsonString(Collections.emptyMap());
    }
  }

}
