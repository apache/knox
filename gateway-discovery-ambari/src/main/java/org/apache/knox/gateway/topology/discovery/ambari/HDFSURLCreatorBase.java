/**
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

  private static final String SCHEME_HTTP  = "http";
  private static final String SCHEME_HTTPS = "https";

  private static final String NAMESERVICE_PARAM = "discovery-nameservice";

  protected AmbariServiceDiscoveryMessages log = MessagesFactory.get(AmbariServiceDiscoveryMessages.class);

  private AmbariCluster cluster = null;


  HDFSURLCreatorBase(AmbariCluster cluster) {
    this.cluster = cluster;
  }

  public List<String> create(String service, Map<String, String> serviceParams) {
    List<String> urls = new ArrayList<>();

    if (getTargetService().equals(service)) {
      AmbariCluster.ServiceConfiguration sc = cluster.getServiceConfiguration("HDFS", "hdfs-site");
      if (sc != null) {
        // First, check if it's HA config
        String nameServices = null;
        AmbariComponent nameNodeComp = cluster.getComponent("NAMENODE");
        if (nameNodeComp != null) {
          nameServices = nameNodeComp.getConfigProperty("dfs.nameservices");
        }

        if (nameServices != null && !nameServices.isEmpty()) {
          String ns = null;

          // Parse the nameservices value
          String[] namespaces = nameServices.split(",");

          if (namespaces.length > 1) {
            String nsParam = (serviceParams != null) ? serviceParams.get(NAMESERVICE_PARAM) : null;
            if (nsParam != null) {
              if (!validateDeclaredNameService(sc, nsParam)) {
                log.undefinedHDFSNameService(nsParam);
              }
              ns = nsParam;
            } else {
              // core-site.xml : dfs.defaultFS property (e.g., hdfs://ns1)
              AmbariCluster.ServiceConfiguration coreSite = cluster.getServiceConfiguration("HDFS", "core-site");
              if (coreSite != null) {
                String defaultFS = coreSite.getProperties().get("fs.defaultFS");
                if (defaultFS != null) {
                  ns = defaultFS.substring(defaultFS.lastIndexOf("/") + 1);
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
            String[] nodes = nameServiceNodes.split(",");
            for (String node : nodes) {
              String propertyValue = getHANameNodeHttpAddress(props, ns, node);
              if (propertyValue != null) {
                urls.add(createURL(propertyValue));
              }
            }
          } else {
            // Name node HTTP addresses are defined as properties of the form:
            //      dfs.namenode.http-address.<NAMESERVICE>.nn<INDEX>
            // So, this iterates over the nn<INDEX> properties until there is no such property (since it cannot be known how
            // many are defined by any other means).
            int i = 1;
            String propertyValue = getHANameNodeHttpAddress(props, ns, i++);
            while (propertyValue != null) {
              urls.add(createURL(propertyValue));
              propertyValue = getHANameNodeHttpAddress(props, ns, i++);
            }
          }

        } else { // If it's not an HA configuration, get the single name node HTTP address
          urls.add(createURL(sc.getProperties().get("dfs.namenode.http-address")));
        }
      }
    }

    return urls;
  }


  // Verify whether the declared nameservice is among the configured nameservices in the cluster
  private static boolean validateDeclaredNameService(AmbariCluster.ServiceConfiguration hdfsSite, String declaredNameService) {
    boolean isValid = false;
    String nameservices = hdfsSite.getProperties().get("dfs.nameservices");
    if (nameservices != null) {
      String[] namespaces = nameservices.split(",");
      for (String ns : namespaces) {
        if (ns.equals(declaredNameService)) {
          isValid = true;
          break;
        }
      }
    }
    return isValid;
  }


  private static String getHANameNodeHttpAddress(Map<String, String> props, String nameService, int index) {
    return props.get("dfs.namenode.http-address." + nameService + ".nn" + index);
  }


  private static String getHANameNodeHttpAddress(Map<String, String> props, String nameService, String node) {
    return props.get("dfs.namenode.http-address." + nameService + "." + node);
  }


  protected abstract String getTargetService();


  protected abstract String createURL(String address);


  protected String getURLScheme() {
    String scheme = SCHEME_HTTP;

    AmbariCluster.ServiceConfiguration sc = cluster.getServiceConfiguration("HDFS", "hdfs-site");
    if (sc != null) {
      String httpPolicy = sc.getProperties().get("dfs.http.policy");
      if (httpPolicy != null) {
        scheme = httpPolicy.equals("HTTPS_ONLY") ? SCHEME_HTTPS : SCHEME_HTTP;
      }
    }

    return scheme;
  }


}
