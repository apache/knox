/**
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
package org.apache.hadoop.gateway.service.admin;

import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.topology.TopologyService;
import org.apache.hadoop.gateway.topology.Topology;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/api/v1")
public class TopologiesResource {
  @Context
  private HttpServletRequest request;

  @GET
  @Produces({APPLICATION_JSON})
  @Path("topologies/{id}")
  public Topology getTopology(@PathParam("id") String id) {
    GatewayServices services = (GatewayServices) request.getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    TopologyService ts = services.getService(GatewayServices.TOPOLOGY_SERVICE);

    for (Topology t : ts.getTopologies()) {
      if(t.getName().equals(id)) {
       try{
         t.setUri(new URI(request.getRequestURL().substring(0, request.getRequestURL().indexOf("gateway")) + "gateway/" + t.getName()));
       }catch (URISyntaxException se){
          t.setUri(null);
       }
        return t;
      }
    }
    return null;
  }

  @GET
  @Produces({APPLICATION_JSON})
  @Path("topologies")
  public Collection<SimpleTopology> getTopologies() {
    GatewayServices services = (GatewayServices) request.getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);


    TopologyService ts = services.getService(GatewayServices.TOPOLOGY_SERVICE);

    ArrayList<SimpleTopology> st = new ArrayList<SimpleTopology>();

    for(Topology t : ts.getTopologies()){
      st.add(getSimpleTopology(  t, request.getRequestURL().toString()  ));
    }


    Collections.sort(st,new TopologyComparator());

    return Collections.unmodifiableCollection(st);
  }

  private class TopologyComparator implements Comparator<SimpleTopology> {
    @Override
    public int compare(SimpleTopology t1, SimpleTopology t2){
          return t1.getName().compareTo(t2.getName());
        }
  }

  private SimpleTopology getSimpleTopology(Topology t, String rURL)
  {
    return new SimpleTopology(t, rURL);
  }

  public static class SimpleTopology {

    private String name;
    private String timestamp;
    private String uri;
    private String href;

    public SimpleTopology(Topology t, String reqURL ) {
      this.name = t.getName();
      this.timestamp = Long.toString(t.getTimestamp());
      this.uri = reqURL.substring(0, reqURL.indexOf("gateway")) + "gateway/" + this.name;
      this.href = reqURL + "/" + this.name;
    }

    public String getName() {
      return name;
    }

    public void setName(String n) {
      name = n;
    }

    public String getTimestamp() {
      return timestamp;
    }

    public void setTimestamp(String timestamp) {
      this.timestamp = timestamp;
    }

    public String getUri() {
      return uri;
    }

    public void setUri(String uri) {
      this.uri = uri;
    }

    public String getHref() {
      return href;
    }

    public void setHref(String href) {
      this.href = href;
    }
  }


}

