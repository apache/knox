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

import java.io.IOException;
import java.nio.file.Paths;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServerInfoService;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.util.X509CertificateUtil;
import org.apache.knox.homepage.service.model.ServiceModel;
import org.apache.knox.homepage.service.model.ServiceModelFactory;

@Path("/v1")
public class HomePageResource {
  private java.nio.file.Path pemFilePath;
  private java.nio.file.Path jksFilePath;

  @Context
  private HttpServletRequest request;

  @GET
  @Produces({ APPLICATION_JSON })
  @Path("generalProxyInformation")
  public GeneralProxyInformation getGeneralProxyInformation() {
    final GeneralProxyInformation proxyInfo = new GeneralProxyInformation();
    try {
      final GatewayServices gatewayServices = (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
      if (gatewayServices != null) {
        final ServerInfoService serviceInfoService = gatewayServices.getService(ServiceType.SERVER_INFO_SERVICE);
        final String versionInfo = serviceInfoService.getBuildVersion() + " (hash=" + serviceInfoService.getBuildHash() +")";
        proxyInfo.setVersion(versionInfo);
        final KeystoreService keystoreService = gatewayServices.getService(ServiceType.KEYSTORE_SERVICE);
        final GatewayConfig config = (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
        final Certificate certificate = keystoreService.getKeystoreForGateway().getCertificate(config.getIdentityKeyAlias());
        generateCertificatePem(certificate, config);
        proxyInfo.setPublicCertPemPath("assets/gateway-client-trust.pem");
        generateCertificateJks(certificate, config);
        proxyInfo.setPublicCertJksPath("assets/gateway-client-trust.jks");
        proxyInfo.setAdminUiUrl(getBaseGatewayUrl(config) + "/manager/admin-ui/");
      }
    } catch (Exception e) {
    }

    return proxyInfo;
  }

  private void generateCertificatePem(Certificate certificate, GatewayConfig gatewayConfig) throws CertificateEncodingException, IOException {
    if (pemFilePath == null || !pemFilePath.toFile().exists()) {
      pemFilePath = Paths.get(gatewayConfig.getGatewayDeploymentDir(), "homepage", "%2Fhome", "assets", "gateway-client-trust.pem");
      X509CertificateUtil.writeCertificateToFile(certificate, pemFilePath.toFile());
    }
  }

  private void generateCertificateJks(Certificate certificate, GatewayConfig gatewayConfig) throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
    if (jksFilePath == null || !jksFilePath.toFile().exists()) {
      jksFilePath = Paths.get(gatewayConfig.getGatewayDeploymentDir(), "homepage", "%2Fhome", "assets", "gateway-client-trust.jks");
      X509CertificateUtil.writeCertificateToJks(certificate, jksFilePath.toFile());
    }
  }

  private String getBaseGatewayUrl(GatewayConfig config) {
    return request.getRequestURL().substring(0, request.getRequestURL().length() - request.getRequestURI().length()) + "/" + config.getGatewayPath();
  }

  @GET
  @Produces({ APPLICATION_JSON })
  @Path("topologies")
  public TopologyInformationWrapper getTopologies() {
    final TopologyInformationWrapper topologies = new TopologyInformationWrapper();
    final GatewayServices gatewayServices = (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    final GatewayConfig config = (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
    final Set<String> hiddenTopologies = config.getHiddenTopologiesOnHomepage();
    if (gatewayServices != null) {
      final TopologyService topologyService = gatewayServices.getService(ServiceType.TOPOLOGY_SERVICE);
      for (Topology topology : topologyService.getTopologies()) {
        if (!hiddenTopologies.contains(topology.getName())) {
          List<ServiceModel> serviceModels = new ArrayList<>();
          for (Service service : topology.getServices()) {
            service.getUrls().forEach(serviceUrl -> serviceModels.add(ServiceModelFactory.getServiceModel(request, config.getGatewayPath(), topology.getName(), service)));
          }
          topologies.addTopology(topology.getName(), serviceModels);
        }
      }
    }
    return topologies;
  }

}
