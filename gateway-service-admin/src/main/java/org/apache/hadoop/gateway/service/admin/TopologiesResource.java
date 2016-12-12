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
import org.apache.hadoop.gateway.config.GatewayConfig;
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
    GatewayConfig config = (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);

    TopologyService ts = services.getService(GatewayServices.TOPOLOGY_SERVICE);

    for (Topology t : ts.getTopologies()) {
      if(t.getName().equals(id)) {
        try {
          t.setUri(new URI( buildURI(t, config, request) ));
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
    GatewayConfig conf = (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);

    for (Topology t : ts.getTopologies()) {
      st.add(getSimpleTopology(t, conf));
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

   String buildURI(Topology topology, GatewayConfig config, HttpServletRequest req){
    String uri = buildXForwardBaseURL(req);

//    Strip extra context
    uri = uri.replace(req.getContextPath(), "");

//    Add the gateway path
    String gatewayPath;
    if(config.getGatewayPath() != null){
      gatewayPath = config.getGatewayPath();
    }else{
      gatewayPath = "gateway";
    }
    uri += "/" + gatewayPath;

    uri += "/" + topology.getName();
    return uri;
  }

   String buildHref(Topology t, HttpServletRequest req) {
    String href = buildXForwardBaseURL(req);
//    Make sure that the pathInfo doesn't have any '/' chars at the end.
    String pathInfo = req.getPathInfo();
    if(pathInfo.endsWith("/")) {
      while(pathInfo.endsWith("/")) {
        pathInfo = pathInfo.substring(0, pathInfo.length() - 1);
      }
    }

    href += pathInfo + "/" + t.getName();
    return href;
  }

  private SimpleTopology getSimpleTopology(Topology t, GatewayConfig config) {
    String uri = buildURI(t, config, request);
    String href = buildHref(t, request);
    return new SimpleTopology(t, uri, href);
  }

  private String buildXForwardBaseURL(HttpServletRequest req){
    final String X_Forwarded = "X-Forwarded-";
    final String X_Forwarded_Context = X_Forwarded + "Context";
    final String X_Forwarded_Proto = X_Forwarded + "Proto";
    final String X_Forwarded_Host = X_Forwarded + "Host";
    final String X_Forwarded_Port = X_Forwarded + "Port";
    final String X_Forwarded_Server = X_Forwarded + "Server";

    String baseURL = "";

//    Get Protocol
    if(req.getHeader(X_Forwarded_Proto) != null){
      baseURL += req.getHeader(X_Forwarded_Proto) + "://";
    } else {
      baseURL += req.getProtocol() + "://";
    }

//    Handle Server/Host and Port Here
    if (req.getHeader(X_Forwarded_Host) != null && req.getHeader(X_Forwarded_Port) != null){
//        Double check to see if host has port
      if(req.getHeader(X_Forwarded_Host).contains(req.getHeader(X_Forwarded_Port))){
        baseURL += req.getHeader(X_Forwarded_Host);
      } else {
//        If there's no port, add the host and port together;
        baseURL += req.getHeader(X_Forwarded_Host) + ":" + req.getHeader(X_Forwarded_Port);
      }
    } else if(req.getHeader(X_Forwarded_Server) != null && req.getHeader(X_Forwarded_Port) != null){
//      Tack on the server and port if they're available. Try host if server not available
      baseURL += req.getHeader(X_Forwarded_Server) + ":" + req.getHeader(X_Forwarded_Port);
    } else if(req.getHeader(X_Forwarded_Port) != null) {
//      if we at least have a port, we can use it.
      baseURL += req.getServerName() + ":" + req.getHeader(X_Forwarded_Port);
    } else {
//      Resort to request members
      baseURL += req.getServerName() + ":" + req.getLocalPort();
    }

//    Handle Server context
    if( req.getHeader(X_Forwarded_Context) != null ) {
      baseURL += req.getHeader( X_Forwarded_Context );
    } else {
      baseURL += req.getContextPath();
    }

    return baseURL;
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

    public SimpleTopology(Topology t, String uri, String href) {
      this.name = t.getName();
      this.timestamp = Long.toString(t.getTimestamp());
      this.uri = uri;
      this.href = href;
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

