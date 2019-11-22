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
package org.apache.knox.gateway.service.test;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

@Path( "/service-test" )
public class ServiceTestResource {
  @Context
  private HttpServletRequest request;


  @GET
  @Produces({APPLICATION_XML, APPLICATION_JSON})
  public ServiceTestWrapper serviceTest(@QueryParam("username") String username,
                                        @QueryParam("password") String password) {
    List<ServiceTest> tests = new ArrayList<>();
    List<String> messages = new ArrayList<>();
    String authString;
    GatewayConfig config = (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
    CloseableHttpClient client = null;
    String id = getTopologyName();

    Topology topology = getTopology(id);

//    Create Authorization String
    if( username != null && password != null) {
      String base64EncodedUserPass = Base64.getEncoder().encodeToString(
          (username + ":" + password).getBytes(StandardCharsets.UTF_8));
      authString = "Basic " + base64EncodedUserPass;
    } else if (request.getHeader("Authorization") != null) {
      authString = request.getHeader("Authorization");
    } else {
      authString = null;
    }

//    Initialize the HTTP client
    try {
      client = HttpClients.createDefault();

      if (topology != null) {
        for (Service s : topology.getServices()) {
          List<String> urls = getServiceTestURLs(config, s.getRole(), topology);

          //          Make sure we handle a case where no URLs are found.
          if (urls.isEmpty()) {
            ServiceTest test = new ServiceTest(s);
            test.setMessage("This service did not contain any test URLs");
          }

          for (String url : urls) {
            HttpGet req = new HttpGet();
            ServiceTest test = new ServiceTest(s, url);

            if (authString != null) {
              req.setHeader("Authorization", authString);
            } else {
              messages.add("No credentials provided. Expect HTTP 401 responses.");
            }

            try {
              req.setURI(new URIBuilder(url).build());
              try(CloseableHttpResponse res = client.execute(req)) {
                String contentLength = "Content-Length:" + res.getEntity().getContentLength();
                String contentType = (res.getEntity().getContentType() != null) ? res.getEntity().getContentType().toString() : "No-contenttype";
                test.setResponseContent(contentLength + "," + contentType);
                test.setHttpCode(res.getStatusLine().getStatusCode());
              }
            } catch (IOException e) {
              messages.add("Exception: " + e.getMessage());
              test.setMessage(e.getMessage());
            } catch (URISyntaxException e) {
              test.setMessage(e.getMessage());
            } catch (Exception e) {
              messages.add(e.getMessage());
              test.setMessage(e.getMessage());
            } finally {
              req.releaseConnection();
              tests.add(test);
            }
          }
        }
      } else {
        messages.add("Topology " + id + " not found");
      }
    } finally {
      if(client != null) {
        try {
          client.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    ServiceTestWrapper stw = new ServiceTestWrapper();
    stw.setTests(tests);
    stw.setMessages(messages);

    return stw;
  }

  private String getTopologyName() {
    String ctxPath = request.getContextPath();
    GatewayConfig config = (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
    String path = config.getGatewayPath();

    return ctxPath.replace(path, "").replace("/", "");
  }

  public Topology getTopology(@PathParam("id") String id) {
    GatewayServices services = (GatewayServices) request.getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    GatewayConfig config = (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);

    TopologyService ts = services.getService(ServiceType.TOPOLOGY_SERVICE);

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

  private List<String> getServiceTestURLs(GatewayConfig conf, String role, Topology topology) {
    GatewayServices services = (GatewayServices) request.getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    List<String> fullURLs = new ArrayList<>();
    if(services != null) {
      TopologyService ts = services.getService(ServiceType.TOPOLOGY_SERVICE);
      Map<String, List<String>> urls = ts.getServiceTestURLs(topology, conf);
      List<String> urlPaths = urls.get(role);

      if(urlPaths != null) {
        String base = buildURI(topology, conf, request);
        for (String u : urlPaths) {

          fullURLs.add(base + u);
        }
      }
    }
    return fullURLs;
  }

  private String buildXForwardBaseURL(HttpServletRequest req){
    final String X_Forwarded = "X-Forwarded-";
    final String X_Forwarded_Context = X_Forwarded + "Context";
    final String X_Forwarded_Proto = X_Forwarded + "Proto";
    final String X_Forwarded_Host = X_Forwarded + "Host";
    final String X_Forwarded_Port = X_Forwarded + "Port";
    final String X_Forwarded_Server = X_Forwarded + "Server";

    StringBuilder baseURL = new StringBuilder();

//    Get Protocol
    if(req.getHeader(X_Forwarded_Proto) != null){
      baseURL.append(req.getHeader(X_Forwarded_Proto)).append("://");
    } else {
      baseURL.append(req.getProtocol()).append("://");
    }

//    Handle Server/Host and Port Here
    if (req.getHeader(X_Forwarded_Host) != null && req.getHeader(X_Forwarded_Port) != null){
//        Double check to see if host has port
      if(req.getHeader(X_Forwarded_Host).contains(req.getHeader(X_Forwarded_Port))){
        baseURL.append(req.getHeader(X_Forwarded_Host));
      } else {
//        If there's no port, add the host and port together;
        baseURL.append(req.getHeader(X_Forwarded_Host)).append(':').append(req.getHeader(X_Forwarded_Port));
      }
    } else if(req.getHeader(X_Forwarded_Server) != null && req.getHeader(X_Forwarded_Port) != null){
//      Tack on the server and port if they're available. Try host if server not available
      baseURL.append(req.getHeader(X_Forwarded_Server)).append(':').append(req.getHeader(X_Forwarded_Port));
    } else if(req.getHeader(X_Forwarded_Port) != null) {
//      if we at least have a port, we can use it.
      baseURL.append(req.getServerName()).append(':').append(req.getHeader(X_Forwarded_Port));
    } else {
//      Resort to request members
      baseURL.append(req.getServerName()).append(':').append(req.getLocalPort());
    }

//    Handle Server context
    if( req.getHeader(X_Forwarded_Context) != null ) {
      baseURL.append(req.getHeader( X_Forwarded_Context ));
    } else {
      baseURL.append(req.getContextPath());
    }

    return baseURL.toString();
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
    return uri + "/" + gatewayPath + "/" + topology.getName();
  }

  @XmlAccessorType(XmlAccessType.NONE)
  public static class ServiceTest {

    @XmlElement
    private String serviceName;
    @XmlElement
    private String requestURL;
    @XmlElement
    private String responseContent;
    @XmlElement
    private int httpCode = -1;

    @XmlElement
    String message;

    public ServiceTest() { }

    public ServiceTest(Service s) {
      this.serviceName = s.getRole();
    }

    public ServiceTest( Service s, String requestURL) {
      this.serviceName = s.getRole();
      this.requestURL = requestURL;
    }

    public String getServiceName() {
      return serviceName;
    }

    public void setServiceName(String n) {
      serviceName = n;
    }

    public String getRequestURL() {
      return requestURL;
    }

    public void setRequestURL(String requestURL) {
      this.requestURL = requestURL;
    }

    public String getResponseContent() {
      return responseContent;
    }

    public void setResponseContent(String responseContent) {
      this.responseContent = responseContent;
    }

    public int getHttpCode() {
      return httpCode;
    }

    public void setHttpCode(int httpCode) {
      this.httpCode = httpCode;
      setMessage();
    }

    public void setMessage() {
      message = buildMessage(httpCode);
    }

    public void setMessage(String msg) {
      if(httpCode != -1) {
        message = buildMessage(httpCode);
      } else {
        message = msg;
      }
    }

    public String getMessage(){
      return message;
    }

    static String buildMessage(int code) {
      switch (code) {
        case 200:
          return "Request successful.";
        case 400:
          return "Could not properly interpret HTTP request.";
        case 401:
          return "User was not authorized. Try using credentials with access to all services. " +
                     "Ensure LDAP server is running.";
        case 403:
          return "Access to this resource is forbidden. It seems we might have made a bad request.";
        case 404:
          return "The page could not be found. Are the URLs for the topology services correct?";
        case 500:
          return "The server encountered an error. Are all of the cluster's services running? \n" +
              "Can a connection be established without Knox?";
        default:
          return "";
      }
    }
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlRootElement
  public static class ServiceTestWrapper{

    @XmlElement(name="ServiceTest")
    @XmlElementWrapper(name="Tests")
    private List<ServiceTest> tests = new ArrayList<>();

    @XmlElement(name="message")
    @XmlElementWrapper(name="messages")
    private List<String> messages = new ArrayList<>();

    public List<ServiceTest> getTests(){
      return tests;
    }

    public void setTests(List<ServiceTest> st){
      this.tests = st;
    }

    public List<String> getMessages() {
      return messages;
    }

    public void setMessages(List<String> messages){
      this.messages = messages;
    }
  }
}
