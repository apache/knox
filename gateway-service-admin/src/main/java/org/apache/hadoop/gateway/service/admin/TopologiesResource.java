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
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.Response.ok;

@Path("/api/v1")
public class TopologiesResource {
  @Context
  private HttpServletRequest request;

  @GET
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  @Path("topologies/{id}")
  public Topology getTopology(@PathParam("id") String id) {
    GatewayServices services = (GatewayServices) request.getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    TopologyService ts = services.getService(GatewayServices.TOPOLOGY_SERVICE);

    for (Topology t : ts.getTopologies()) {
      if(t.getName().equals(id)) {
        try {
          t.setUri(new URI(request.getRequestURL().substring(0, request.getRequestURL().indexOf("gateway")) + "gateway/" + t.getName()));
        } catch (URISyntaxException se) {
          t.setUri(null);
        }
        return t;
      }
    }
    return null;
  }

  @GET
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  @Path("topologies")
  public SimpleTopologyWrapper getTopologies() {
    GatewayServices services = (GatewayServices) request.getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);


    TopologyService ts = services.getService(GatewayServices.TOPOLOGY_SERVICE);

    ArrayList<SimpleTopology> st = new ArrayList<SimpleTopology>();

    for (Topology t : ts.getTopologies()) {
      st.add(getSimpleTopology(t, request.getRequestURL().toString()));
    }

    Collections.sort(st, new TopologyComparator());
    SimpleTopologyWrapper stw = new SimpleTopologyWrapper();

    for(SimpleTopology t : st){
      stw.topologies.add(t);
    }

    return stw;

  }

  @PUT
  @Consumes({APPLICATION_JSON, APPLICATION_XML})
  @Path("topologies/{id}")
  public Topology uploadTopology(@PathParam("id") String id, Topology t) {

    GatewayServices gs = (GatewayServices) request.getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    t.setName(id);
    TopologyService ts = gs.getService(GatewayServices.TOPOLOGY_SERVICE);

    ts.deployTopology(t);

    return getTopology(id);
  }

  @DELETE
  @Produces(APPLICATION_JSON)
  @Path("topologies/{id}")
  public Response deleteTopology(@PathParam("id") String id) {
    boolean deleted = false;
    if(!id.equals("admin")) {
      GatewayServices services = (GatewayServices) request.getServletContext()
          .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

      TopologyService ts = services.getService(GatewayServices.TOPOLOGY_SERVICE);

      for (Topology t : ts.getTopologies()) {
        if(t.getName().equals(id)) {
          ts.deleteTopology(t);
          deleted = true;
        }
      }
    }else{
      deleted = false;
    }
    return ok().entity("{ \"deleted\" : " + deleted + " }").build();
  }


  private class TopologyComparator implements Comparator<SimpleTopology> {
    @Override
    public int compare(SimpleTopology t1, SimpleTopology t2) {
      return t1.getName().compareTo(t2.getName());
    }
  }

  private SimpleTopology getSimpleTopology(Topology t, String rURL) {
    return new SimpleTopology(t, rURL);
  }


  @XmlAccessorType(XmlAccessType.NONE)
  public static class SimpleTopology {

    @XmlElement
    private String name;
    @XmlElement
    private String timestamp;
    @XmlElement
    private String uri;
    @XmlElement
    private String href;

    public SimpleTopology() {}

    public SimpleTopology(Topology t, String reqURL) {
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

  @XmlAccessorType(XmlAccessType.FIELD)
  public static class SimpleTopologyWrapper{

    @XmlElement(name="topology")
    @XmlElementWrapper(name="topologies")
    private List<SimpleTopology> topologies = new ArrayList<SimpleTopology>();

    public List<SimpleTopology> getTopologies(){
      return topologies;
    }

    public void setTopologies(List<SimpleTopology> ts){
      this.topologies = ts;
    }

  }




}

