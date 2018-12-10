/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.ambari;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class HDFSURLCreatorBase implements ServiceURLCreator {

  static final String CONFIG_SERVICE_NAMENODE = "NAMENODE";
  static final String CONFIG_SERVICE_HDFS     = "HDFS";
  static final String CONFIG_TYPE_HDFS_SITE   = "hdfs-site";
  static final String CONFIG_TYPE_CORE_SITE   = "core-site";

  static final String HTTP_POLICY_PROPERTY = "dfs.http.policy";

  static final String HTTP_ONLY_POLICY  = "HTTP_ONLY";
  static final String HTTPS_ONLY_POLICY = "HTTPS_ONLY";

  static final String SCHEME_HTTP  = "http";
  static final String SCHEME_HTTPS = "https";

  static final String HTTP_ADDRESS_PROPERTY  = "dfs.namenode.http-address";
  static final String HTTPS_ADDRESS_PROPERTY = "dfs.namenode.https-address";

  static final String NAMESERVICE_PARAM = "discovery-nameservice";

  protected AmbariServiceDiscoveryMessages log = MessagesFactory.get(AmbariServiceDiscoveryMessages.class);

  private AmbariCluster cluster;


  @Override
  public void init(AmbariCluster cluster) {
    this.cluster = cluster;
  }

  @Override
  public List<String> create(String service, Map<String, String> serviceParams) {
    List<String> urls = new ArrayList<>();

    if (getTargetService().equals(service)) {
      AmbariCluster.ServiceConfiguration sc =
                                      cluster.getServiceConfiguration(CONFIG_SERVICE_HDFS, CONFIG_TYPE_HDFS_SITE);
      if (sc != null) {
        // First, check if it's HA config
        String nameServices = sc.getProperties().get("dfs.nameservices");
        if (nameServices != null && !nameServices.isEmpty()) {
          String ns = null;

          // Parse the nameservices value
          String[] namespaces = nameServices.split(",");

          String nsParam = (serviceParams != null) ? serviceParams.get(NAMESERVICE_PARAM) : null;

          if (namespaces.length > 1 || nsParam != null) {
            if (nsParam != null) {
              if (!validateDeclaredNameService(namespaces, nsParam)) {
                log.undefinedHDFSNameService(nsParam);
              }
              ns = nsParam;
            } else {
              // core-site.xml : dfs.defaultFS property (e.g., hdfs://ns1)
              AmbariCluster.ServiceConfiguration coreSite =
                                        cluster.getServiceConfiguration(CONFIG_SERVICE_HDFS, CONFIG_TYPE_CORE_SITE);
              if (coreSite != null) {
                String defaultFS = coreSite.getProperties().get("fs.defaultFS");
                if (defaultFS != null) {
                  ns = defaultFS.substring(defaultFS.lastIndexOf('/') + 1);
                }
              }
            }
          }

          // If only a single namespace, or no namespace specified and no default configured, use the first in the "list"
          if (ns == null) {
            ns = namespaces[0];
          }

          // If it is an HA configuration
          Map<String, String> props = sc.getProperties();

          // More recent HDFS configurations support a property enumerating the node names associated with a
          // nameservice. If this property is present, use its value to create the correct URLs.
          String nameServiceNodes = props.get("dfs.ha.namenodes." + ns);
          if (nameServiceNodes != null) {
            String addressPropertyPrefix = getAddressPropertyPrefix();
            String[] nodes = nameServiceNodes.split(",");
            for (String node : nodes) {
              String propertyValue = getHANameNodeHttpAddress(addressPropertyPrefix, props, ns, node);
              if (propertyValue != null) {
                urls.add(createURL(propertyValue));
              }
            }
          } else {
            // Name node HTTP[S] addresses are defined as properties of the form:
            //      dfs.namenode.http[s]-address.<NAMESERVICE>.nn<INDEX>
            // So, this iterates over the nn<INDEX> properties until there is no such property (since it cannot be
            // known how many are defined by any other means).
            String addressPropertyPrefix = getAddressPropertyPrefix();
            int i = 1;
            String propertyValue = getHANameNodeHttpAddress(addressPropertyPrefix, props, ns, i++);
            while (propertyValue != null) {
              urls.add(createURL(propertyValue));
              propertyValue = getHANameNodeHttpAddress(addressPropertyPrefix, props, ns, i++);
            }
          }

        } else { // If it's not an HA configuration, get the single name node HTTP[S] address
          urls.add(createURL(sc.getProperties().get(getAddressPropertyPrefix())));
        }
      }
    }

    return urls;
  }


  // Verify whether the declared nameservice is among the configured nameservices in the cluster
  private static boolean validateDeclaredNameService(String[] namespaces, String declaredNameService) {
    boolean isValid = false;
    if (namespaces != null) {
      for (String ns : namespaces) {
        if (ns.equals(declaredNameService)) {
          isValid = true;
          break;
        }
      }
    }
    return isValid;
  }


  private static String getHANameNodeHttpAddress(String              addressPropertyPrefix,
                                                 Map<String, String> props,
                                                 String              nameService,
                                                 int                 index) {
    return props.get(addressPropertyPrefix + "." + nameService + ".nn" + index);
  }


  private static String getHANameNodeHttpAddress(String              addressPropertyPrefix,
                                                 Map<String, String> props,
                                                 String              nameService,
                                                 String              node) {
    return props.get(addressPropertyPrefix + "." + nameService + "." + node);
  }

  /**
   * @return The HTTP or HTTPS address property name prefix, depending on the value of the dfs.http.policy property
   */
  private String getAddressPropertyPrefix() {
    return HTTPS_ONLY_POLICY.equals(getHttpPolicy()) ? HTTPS_ADDRESS_PROPERTY : HTTP_ADDRESS_PROPERTY;
  }

  private String getHttpPolicy() {
    String httpPolicy = HTTP_ONLY_POLICY;

    AmbariCluster.ServiceConfiguration sc = cluster.getServiceConfiguration(CONFIG_SERVICE_HDFS, CONFIG_TYPE_HDFS_SITE);
    if (sc != null) {
      String propertyValue = sc.getProperties().get(HTTP_POLICY_PROPERTY);
      if (propertyValue != null && !propertyValue.isEmpty()) {
        httpPolicy = propertyValue;
      }
    }
    return httpPolicy;
  }

  protected abstract String createURL(String address);


  protected String getURLScheme() {
    return HTTPS_ONLY_POLICY.equals(getHttpPolicy()) ? SCHEME_HTTPS : SCHEME_HTTP;
  }


}
