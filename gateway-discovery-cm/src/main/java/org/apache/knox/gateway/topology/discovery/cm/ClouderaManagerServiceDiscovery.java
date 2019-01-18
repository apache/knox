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
package org.apache.knox.gateway.topology.discovery.cm;

import com.cloudera.api.swagger.ClustersResourceApi;
import com.cloudera.api.swagger.RolesResourceApi;
import com.cloudera.api.swagger.ServicesResourceApi;
import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.model.ApiCluster;
import com.cloudera.api.swagger.model.ApiClusterList;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiRoleList;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import com.cloudera.api.swagger.model.ApiServiceList;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.topology.discovery.GatewayService;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;

import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URL;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;


/**
 * ClouderaManager-based service discovery implementation.
 */
public class ClouderaManagerServiceDiscovery implements ServiceDiscovery {

  static final String TYPE = "ClouderaManager";

  private static final ClouderaManagerServiceDiscoveryMessages log =
                                        MessagesFactory.get(ClouderaManagerServiceDiscoveryMessages.class);

  private static final String JGSS_LOGIN_MODULE = "com.sun.security.jgss.initiate";

  static final String API_PATH = "api/v32";

  private static final String CLUSTER_TYPE_ANY = "any";
  private static final String VIEW_SUMMARY     = "summary";
  private static final String VIEW_FULL        = "full";

  static final String DEFAULT_USER_ALIAS = "cm.discovery.user";
  static final String DEFAULT_PWD_ALIAS  = "cm.discovery.password";

  private boolean debug;

  @GatewayService
  private AliasService aliasService;


  ClouderaManagerServiceDiscovery() {
    this(false);
  }

  ClouderaManagerServiceDiscovery(boolean debug) {
    this.debug = debug;
  }

  @Override
  public String getType() {
    return TYPE;
  }

  private DiscoveryApiClient getClient(ServiceDiscoveryConfig discoveryConfig) {
    String discoveryAddress = discoveryConfig.getAddress();
    if (discoveryAddress == null || discoveryAddress.isEmpty()) {
      log.missingDiscoveryAddress();
      throw new IllegalArgumentException("Missing or invalid discovery address.");
    }

    DiscoveryApiClient client = new DiscoveryApiClient(discoveryConfig, aliasService);
    client.setDebugging(debug);
    client.setVerifyingSsl(false);
    return client;
  }

  @Override
  public Map<String, Cluster> discover(GatewayConfig gatewayConfig, ServiceDiscoveryConfig discoveryConfig) {
    Map<String, Cluster> clusters = new HashMap<>();

    DiscoveryApiClient client = getClient(discoveryConfig);
    List<ApiCluster> apiClusters = getClusters(client);
    for (ApiCluster apiCluster : apiClusters) {
      String clusterName = apiCluster.getName();
      log.discoveredCluster(clusterName, apiCluster.getFullVersion());

      Cluster cluster = discover(gatewayConfig, discoveryConfig, clusterName, client);
      clusters.put(clusterName, cluster);
    }

    return clusters;
  }

  @Override
  public Cluster discover(GatewayConfig gatewayConfig, ServiceDiscoveryConfig discoveryConfig, String clusterName) {
    return discover(gatewayConfig, discoveryConfig, clusterName, getClient(discoveryConfig));
  }

  protected Cluster discover(GatewayConfig          gatewayConfig,
                             ServiceDiscoveryConfig discoveryConfig,
                             String                 clusterName,
                             DiscoveryApiClient     client) {
    ServiceDiscovery.Cluster cluster = null;

    if (clusterName == null || clusterName.isEmpty()) {
      log.missingDiscoveryCluster();
      throw new IllegalArgumentException("The cluster configuration is missing from, or invalid in, the discovery configuration.");
    }

    try {
      cluster = discoverCluster(client, clusterName);
    } catch (ApiException e) {
      log.clusterDiscoveryError(clusterName, e);
    }

    return cluster;
  }

  private static List<ApiCluster> getClusters(DiscoveryApiClient client) {
    List<ApiCluster> clusters = new ArrayList<>();
    try {
      ApiClusterList clusterList = null;

      ClustersResourceApi clustersResourceApi = new ClustersResourceApi(client);
      if (client.isKerberos()) {
        clusterList =
            Subject.doAs(getSubject(), (PrivilegedAction<ApiClusterList>) () -> {
              try {
                return clustersResourceApi.readClusters(CLUSTER_TYPE_ANY, VIEW_SUMMARY);
              } catch (Exception e) {
                log.clusterDiscoveryError(CLUSTER_TYPE_ANY, e);
              }
              return null;
            });
      } else {
          clusterList = clustersResourceApi.readClusters(CLUSTER_TYPE_ANY, VIEW_SUMMARY);
      }

      if (clusterList != null) {
        clusters.addAll(clusterList.getItems());
      }
    } catch (Exception e) {
      log.clusterDiscoveryError(CLUSTER_TYPE_ANY, e); // TODO: PJZ: Better error message here?
    }

    return clusters;
  }


  private static Cluster discoverCluster(DiscoveryApiClient client, String clusterName) throws ApiException {
    ClouderaManagerCluster cluster = null;

    ServicesResourceApi servicesResourceApi = new ServicesResourceApi(client);
    RolesResourceApi rolesResourceApi = new RolesResourceApi(client);

    log.discoveringCluster(clusterName);

    cluster = new ClouderaManagerCluster(clusterName);

    Set<ServiceModel> serviceModels = new HashSet<>();
    ServiceLoader<ServiceModelGenerator> loader = ServiceLoader.load(ServiceModelGenerator.class);

    ApiServiceList serviceList = getClusterServices(servicesResourceApi, clusterName, client.isKerberos());
    if (serviceList != null) {
      for (ApiService service : serviceList.getItems()) {
        String serviceName = service.getName();
        log.discoveredService(serviceName, service.getType());
        ApiServiceConfig serviceConfig =
            getServiceConfig(servicesResourceApi, clusterName, serviceName, client.isKerberos());
        ApiRoleList roleList = getRoles(rolesResourceApi, clusterName, serviceName, client.isKerberos());
        if (roleList != null) {
          for (ApiRole role : roleList.getItems()) {
            String roleName = role.getName();
            log.discoveredServiceRole(roleName, role.getType());
            ApiConfigList roleConfig =
                getRoleConfig(rolesResourceApi, clusterName, serviceName, roleName, client.isKerberos());

            for (ServiceModelGenerator serviceModelGenerator : loader) {
              if (serviceModelGenerator.handles(service, serviceConfig, role, roleConfig)) {
                serviceModelGenerator.setApiClient(client);
                ServiceModel serviceModel = serviceModelGenerator.generateService(service, serviceConfig, role, roleConfig);
                serviceModels.add(serviceModel);
              }
            }
          }
        }
      }
    }

    cluster.addServiceModels(serviceModels);

    return cluster;
  }

  private static ApiServiceList getClusterServices(final ServicesResourceApi servicesResourceApi,
                                                   final String              clusterName,
                                                   final boolean             isKerberos) {
    ApiServiceList serviceList = null;
    if (isKerberos) {
      serviceList =
          Subject.doAs(getSubject(), (PrivilegedAction<ApiServiceList>) () -> {
            try {
              return servicesResourceApi.readServices(clusterName, VIEW_SUMMARY);
            } catch (Exception e) {
              log.failedToAccessServiceConfigs(clusterName, e);
            }
            return null;
          });
    } else {
      try {
        serviceList = servicesResourceApi.readServices(clusterName, VIEW_SUMMARY);
      } catch (ApiException e) {
        log.failedToAccessServiceConfigs(clusterName, e);
      }
    }
    return serviceList;
  }

  private static ApiServiceConfig getServiceConfig(final ServicesResourceApi servicesResourceApi,
                                                   final String clusterName,
                                                   final String serviceName,
                                                   final boolean isKerberos) {
    ApiServiceConfig serviceConfig = null;
    if (isKerberos) {
      serviceConfig =
          Subject.doAs(getSubject(), (PrivilegedAction<ApiServiceConfig>) () -> {
            try {
              return servicesResourceApi.readServiceConfig(clusterName, serviceName, VIEW_FULL);
            } catch (Exception e) {
              log.failedToAccessServiceConfigs(clusterName, e);
            }
            return null;
          });
    } else {
      try {
        serviceConfig = servicesResourceApi.readServiceConfig(clusterName, serviceName, VIEW_FULL);
      } catch (Exception e) {
        log.failedToAccessServiceConfigs(clusterName, e);
      }
    }
    return serviceConfig;
  }

  private static ApiRoleList getRoles(RolesResourceApi rolesResourceApi,
                                      String clusterName,
                                      String serviceName,
                                      boolean isKerberos) {
    ApiRoleList roleList = null;

    if (isKerberos) {
      roleList =
          Subject.doAs(getSubject(), (PrivilegedAction<ApiRoleList>) () -> {
            try {
              return rolesResourceApi.readRoles(clusterName, serviceName, "", VIEW_SUMMARY);
            } catch (Exception e) {
              log.failedToAccessServiceRoleConfigs(clusterName, e);
            }
            return null;
          });
    } else {
      try {
        roleList = rolesResourceApi.readRoles(clusterName, serviceName, "", VIEW_SUMMARY);
      } catch (ApiException e) {
        log.failedToAccessServiceRoleConfigs(clusterName, e);
      }
    }

    return roleList;
  }

  private static ApiConfigList getRoleConfig(RolesResourceApi rolesResourceApi,
                                             String           clusterName,
                                             String           serviceName,
                                             String           roleName,
                                             boolean          isKerberos) {
    ApiConfigList roleConfig = null;
    if (isKerberos) {
      roleConfig =
          Subject.doAs(getSubject(), (PrivilegedAction<ApiConfigList>) () -> {
            try {
              return rolesResourceApi.readRoleConfig(clusterName, roleName, serviceName, VIEW_FULL);
            } catch (Exception e) {
              log.failedToAccessServiceRoleConfigs(clusterName, e);
            }
            return null;
          });
    } else {
      try {
        roleConfig = rolesResourceApi.readRoleConfig(clusterName, roleName, serviceName, VIEW_FULL);
      } catch (ApiException e) {
        log.failedToAccessServiceRoleConfigs(clusterName, e);
      }
    }
    return roleConfig;
  }

  private static Subject getSubject() {
    Subject subject = SubjectUtils.getCurrentSubject();
    if (subject == null) {
      subject = login();
    }
    return subject;
  }

  private static Subject login() {
    Subject subject = null;
    String kerberosLoginConfig = getKerberosLoginConfig();
    if (kerberosLoginConfig != null) {
      try {
        Configuration jaasConf = new JAASClientConfig((new File(kerberosLoginConfig)).toURI().toURL());
        LoginContext lc = new LoginContext(JGSS_LOGIN_MODULE,
                                           null,
                                           null,
                                           jaasConf);
        lc.login();
        subject = lc.getSubject();
      } catch (Exception e) {
        log.failedKerberosLogin(kerberosLoginConfig, JGSS_LOGIN_MODULE, e);
      }
    }

    return subject;
  }

  private static final class JAASClientConfig extends Configuration {

    private static final Configuration baseConfig = Configuration.getConfiguration();

    private Configuration configFile;

    JAASClientConfig(URL configFileURL) throws Exception {
      if (configFileURL != null) {
        this.configFile = ConfigurationFactory.create(configFileURL.toURI());
      }
    }

    @Override
    public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
      AppConfigurationEntry[] result = null;

      // Try the config file if it exists
      if (configFile != null) {
        result = configFile.getAppConfigurationEntry(name);
      }

      // If the entry isn't there, delegate to the base configuration
      if (result == null) {
        result = baseConfig.getAppConfigurationEntry(name);
      }

      return result;
    }
  }

  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  private static class ConfigurationFactory {

    private static final Class implClazz;
    static {
      // Oracle and OpenJDK use the Sun implementation
      String implName = System.getProperty("java.vendor").contains("IBM") ?
          "com.ibm.security.auth.login.ConfigFile" : "com.sun.security.auth.login.ConfigFile";

      log.usingJAASConfigurationFileImplementation(implName);
      Class clazz = null;
      try {
        clazz = Class.forName(implName, false, Thread.currentThread().getContextClassLoader());
      } catch (ClassNotFoundException e) {
        log.failedToLoadJAASConfigurationFileImplementation(implName, e);
      }

      implClazz = clazz;
    }

    static Configuration create(URI uri) {
      Configuration config = null;

      if (implClazz != null) {
        try {
          Constructor ctor = implClazz.getDeclaredConstructor(URI.class);
          config = (Configuration) ctor.newInstance(uri);
        } catch (Exception e) {
          log.failedToInstantiateJAASConfigurationFileImplementation(implClazz.getCanonicalName(), e);
        }
      } else {
        log.noJAASConfigurationFileImplementation();
      }

      return config;
    }
  }

  private static String getKerberosLoginConfig() {
    return System.getProperty(GatewayConfig.KRB5_LOGIN_CONFIG, "");
  }

}
