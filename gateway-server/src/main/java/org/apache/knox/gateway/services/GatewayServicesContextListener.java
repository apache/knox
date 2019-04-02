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
package org.apache.knox.gateway.services;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.knox.gateway.GatewayServer;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Topology;

public class GatewayServicesContextListener implements ServletContextListener {

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    GatewayServices gs = GatewayServer.getGatewayServices();
    sce.getServletContext().setAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE, gs);
    String topologyName = (String) sce.getServletContext().getAttribute("org.apache.knox.gateway.gateway.cluster");
    TopologyService ts = gs.getService(ServiceType.TOPOLOGY_SERVICE);
    Topology topology = getTopology(ts, topologyName);
    sce.getServletContext().setAttribute("org.apache.knox.gateway.topology", topology);
  }

  private Topology getTopology(TopologyService ts, String topologyName) {
    Topology t = null;
    for (Topology topology : ts.getTopologies()) {
      if (topology.getName().equals(topologyName)) {
        t = topology;
        break;
      }
    }
    return t;
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
  }

}
