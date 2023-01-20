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
package org.apache.knox.gateway.service.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.service.admin.beans.BeanConverter;
import org.apache.knox.gateway.service.admin.beans.Topology;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.topology.TopologyService;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.notModified;
import static javax.ws.rs.core.Response.status;

@Api(value = "topology",  description = "The Knox Admin API to interact with topologies.")
@Path("/api/v1")
public class TopologiesResource {

  private static final String XML_EXT  = ".xml";
  private static final String JSON_EXT = ".json";
  private static final String YAML_EXT = ".yml";

  private static final String TOPOLOGIES_API_PATH    = "topologies";
  private static final String SINGLE_TOPOLOGY_API_PATH = TOPOLOGIES_API_PATH + "/{id}";
  private static final String PROVIDERCONFIG_API_PATH = "providerconfig";
  private static final String SINGLE_PROVIDERCONFIG_API_PATH = PROVIDERCONFIG_API_PATH + "/{name}";
  private static final String DESCRIPTORS_API_PATH    = "descriptors";
  private static final String SINGLE_DESCRIPTOR_API_PATH = DESCRIPTORS_API_PATH + "/{name}";

  private static final int     RESOURCE_NAME_LENGTH_MAX = 100;
  private static final Pattern RESOURCE_NAME_PATTERN    = Pattern.compile("^[\\w-/.]+$");

  private static GatewaySpiMessages log = MessagesFactory.get(GatewaySpiMessages.class);

  private static final Map<MediaType, String> mediaTypeFileExtensions = new HashMap<>();
  static {
    mediaTypeFileExtensions.put(MediaType.APPLICATION_XML_TYPE, XML_EXT);
    mediaTypeFileExtensions.put(MediaType.APPLICATION_JSON_TYPE, JSON_EXT);
    mediaTypeFileExtensions.put(MediaType.TEXT_PLAIN_TYPE, YAML_EXT);
  }

  @Context
  private HttpServletRequest request;

  @GET
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  @Path(SINGLE_TOPOLOGY_API_PATH)
  public Topology getTopology(@PathParam("id") String id) {
    GatewayServices services =
              (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    if (services != null) {
      TopologyService ts = services.getService(ServiceType.TOPOLOGY_SERVICE);

      GatewayConfig config =
                (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
      for (org.apache.knox.gateway.topology.Topology t : ts.getTopologies()) {
        if (t.getName().equals(id)) {
          // we need to convert first so that the original topology does not get
          // overwritten in TopologyService (i.e. URI does not change from 'file://...' to
          // 'https://...'
          final Topology convertedTopology = BeanConverter.getTopology(t);
          try {
            convertedTopology.setUri(new URI( buildURI(t, config, request) ));
          } catch (URISyntaxException se) {
            convertedTopology.setUri(null);
          }

          // For any read-only override topology, mark it as generated to discourage modification.
          final List<String> managedTopologies = config.getReadOnlyOverrideTopologyNames();
          if (managedTopologies.contains(convertedTopology.getName())) {
            convertedTopology.setGenerated(true);
          }

          return convertedTopology;
        }
      }
    }
    return null;
  }

  @ApiOperation(value="Get ALL topologies", notes="Get ALL topologies that are available within this Knox Gateway", response=SimpleTopologyWrapper.class)
  @GET
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  @Path(TOPOLOGIES_API_PATH)
  public SimpleTopologyWrapper getTopologies() {
    GatewayServices services =
              (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    GatewayConfig config =
        (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);

    TopologyService ts = services.getService(ServiceType.TOPOLOGY_SERVICE);

    ArrayList<SimpleTopology> st = new ArrayList<>();
    for (org.apache.knox.gateway.topology.Topology t : ts.getTopologies()) {
      st.add(getSimpleTopology(t, config));
    }
    st.sort(new TopologyComparator());

    SimpleTopologyWrapper stw = new SimpleTopologyWrapper();
    stw.topologies.addAll(st);

    return stw;

  }

  @PUT
  @Consumes({APPLICATION_JSON, APPLICATION_XML})
  @Path(SINGLE_TOPOLOGY_API_PATH)
  public Topology uploadTopology(@PathParam("id") String id, Topology t) {
    Topology result = null;

    try {
      id = URLDecoder.decode(id, StandardCharsets.UTF_8.name());
    } catch (Exception e) {
      // Ignore
    }

    if (!isValidResourceName(id)) {
      log.invalidResourceName(id);
      throw new BadRequestException("Invalid topology name: " + id);
    }

    GatewayServices gs =
                (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    t.setName(id);
    TopologyService ts = gs.getService(ServiceType.TOPOLOGY_SERVICE);

    // Check for existing topology with the same name, to see if it had been generated
    boolean existingGenerated = false;
    for (org.apache.knox.gateway.topology.Topology existingTopology : ts.getTopologies()) {
      if(existingTopology.getName().equals(id)) {
        existingGenerated = existingTopology.isGenerated();
        break;
      }
    }

    // If a topology with the same ID exists, which had been generated, then DO NOT overwrite it because it will be
    // out of sync with the source descriptor. Otherwise, deploy the updated version.
    if (!existingGenerated) {
      ts.deployTopology(BeanConverter.getTopology(t));
      result = getTopology(id);
    } else {
      log.disallowedOverwritingGeneratedTopology(id);
    }

    return result;
  }

  @DELETE
  @Produces(APPLICATION_JSON)
  @Path(SINGLE_TOPOLOGY_API_PATH)
  public Response deleteTopology(@PathParam("id") String id) {
    boolean deleted = false;
    if(!"admin".equals(id)) {
      GatewayServices services = (GatewayServices) request.getServletContext()
          .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

      TopologyService ts = services.getService(ServiceType.TOPOLOGY_SERVICE);

      for (org.apache.knox.gateway.topology.Topology t : ts.getTopologies()) {
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

  @GET
  @Produces({APPLICATION_JSON})
  @Path(PROVIDERCONFIG_API_PATH)
  public HrefListing getProviderConfigurations() {
    HrefListing listing = new HrefListing();
    listing.setHref(buildHref(request));

    GatewayServices services =
            (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    List<HrefListItem> configs = new ArrayList<>();
    TopologyService ts = services.getService(ServiceType.TOPOLOGY_SERVICE);
    // Get all the simple descriptor file names
    for (File providerConfig : ts.getProviderConfigurations()){
      String id = FilenameUtils.getBaseName(providerConfig.getName());
      configs.add(new HrefListItem(buildHref(id, request), providerConfig.getName()));
    }

    listing.setItems(configs);
    return listing;
  }

  @GET
  @Produces({APPLICATION_XML, APPLICATION_JSON, TEXT_PLAIN})
  @Path(SINGLE_PROVIDERCONFIG_API_PATH)
  public Response getProviderConfiguration(@PathParam("name") String name) {
    Response response;

    GatewayServices services =
            (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    TopologyService ts = services.getService(ServiceType.TOPOLOGY_SERVICE);

    File providerConfigFile = null;

    for (File pc : ts.getProviderConfigurations()){
      // If the file name matches the specified id
      if (FilenameUtils.getBaseName(pc.getName()).equals(name)) {
        providerConfigFile = pc;
        break;
      }
    }

    if (providerConfigFile != null) {
      byte[] content;
      try {
        content = FileUtils.readFileToByteArray(providerConfigFile);
        response = ok().entity(content).build();
      } catch (IOException e) {
        log.failedToReadConfigurationFile(providerConfigFile.getAbsolutePath(), e);
        response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
      }

    } else {
      response = Response.status(Response.Status.NOT_FOUND).build();
    }
    return response;
  }

  @DELETE
  @Produces(APPLICATION_JSON)
  @Path(SINGLE_PROVIDERCONFIG_API_PATH)
  public Response deleteProviderConfiguration(@PathParam("name") String name, @QueryParam("force") String force) {
    Response response;
    GatewayServices services =
            (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    TopologyService ts = services.getService(ServiceType.TOPOLOGY_SERVICE);
    if (ts.deleteProviderConfiguration(name, Boolean.valueOf(force))) {
      response = ok().entity("{ \"deleted\" : \"provider config " + name + "\" }").build();
    } else {
      response = notModified().build();
    }
    return response;
  }


  @DELETE
  @Produces(APPLICATION_JSON)
  @Path(SINGLE_DESCRIPTOR_API_PATH)
  public Response deleteSimpleDescriptor(@PathParam("name") String name) {
    Response response = null;
    if(!"admin".equals(name)) {
      GatewayServices services =
              (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

      TopologyService ts = services.getService(ServiceType.TOPOLOGY_SERVICE);
      if (ts.deleteDescriptor(name)) {
        response = ok().entity("{ \"deleted\" : \"descriptor " + name + "\" }").build();
      }
    }

    if (response == null) {
      response = notModified().build();
    }

    return response;
  }


  @PUT
  @Consumes({APPLICATION_XML, APPLICATION_JSON, TEXT_PLAIN})
  @Path(SINGLE_PROVIDERCONFIG_API_PATH)
  public Response uploadProviderConfiguration(@PathParam("name") String name, @Context HttpHeaders headers, String content) {
    Response response = null;

    try {
      name = URLDecoder.decode(name, StandardCharsets.UTF_8.name());
    } catch (Exception e) {
      // Ignore
    }

    if (!isValidResourceName(name)) {
      log.invalidResourceName(name);
      throw new BadRequestException("Invalid provider configuration name: " + name);
    }

    GatewayServices gs =
            (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    TopologyService ts = gs.getService(ServiceType.TOPOLOGY_SERVICE);

    File existing = getExistingConfigFile(ts.getProviderConfigurations(), name);
    boolean isUpdate = (existing != null);

    // If it's an update, then use the matching existing filename; otherwise, use the media type to determine the file
    // extension.
    String filename = isUpdate ? existing.getName() : getFileNameForResource(name, headers);

    GatewayConfig config =
            (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
    if (providerExists(name, ts) &&
            config.getReadOnlyOverrideProviderNames().contains(FilenameUtils.getBaseName(name))) {
      log.disallowedOverwritingGeneratedProvider(name);
      return status(Response.Status.CONFLICT).entity("{ \"error\" : \"Cannot overwrite existing generated provider: " + name + "\" }").build();
    }

    if (ts.deployProviderConfiguration(filename, content)) {
      try {
        if (isUpdate) {
          response = Response.noContent().build();
        } else {
          response = created(new URI(buildHref(request))).build();
        }
      } catch (URISyntaxException e) {
        log.invalidResourceURI(e.getInput(), e.getReason(), e);
        response = status(Response.Status.BAD_REQUEST).entity("{ \"error\" : \"Failed to deploy provider configuration " + name + "\" }").build();
      }
    }

    return response;
  }

  @PUT
  @Consumes({APPLICATION_JSON, TEXT_PLAIN})
  @Path(SINGLE_DESCRIPTOR_API_PATH)
  public Response uploadSimpleDescriptor(@PathParam("name") String name,
                                         @Context HttpHeaders headers,
                                         String content) {
    Response response = null;

    try {
      name = URLDecoder.decode(name, StandardCharsets.UTF_8.name());
    } catch (Exception e) {
      // Ignore
    }

    if (!isValidResourceName(name)) {
      log.invalidResourceName(name);
      throw new BadRequestException("Invalid descriptor name: " + name);
    }

    GatewayServices gs =
            (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    TopologyService ts = gs.getService(ServiceType.TOPOLOGY_SERVICE);
    GatewayConfig config =
            (GatewayConfig) request.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
    if ((descriptorExists(name, ts) || topologyExists(name, ts))
            && config.getReadOnlyOverrideTopologyNames().contains(FilenameUtils.getBaseName(name))) {
      log.disallowedOverwritingGeneratedDescriptor(name);
      return status(Response.Status.CONFLICT).entity("{ \"error\" : \"Cannot overwrite existing generated topology: " + name + "\" }").build();
    }

    File existing = getExistingConfigFile(ts.getDescriptors(), name);
    boolean isUpdate = (existing != null);

    // If it's an update, then use the matching existing filename; otherwise, use the media type to determine the file
    // extension.
    String filename = isUpdate ? existing.getName() : getFileNameForResource(name, headers);

    if (ts.deployDescriptor(filename, content)) {
      try {
        if (isUpdate) {
          response = Response.noContent().build();
        } else {
          response = created(new URI(buildHref(request))).build();
        }
      } catch (URISyntaxException e) {
        log.invalidResourceURI(e.getInput(), e.getReason(), e);
        response = status(Response.Status.BAD_REQUEST).entity("{ \"error\" : \"Failed to deploy descriptor " + name + "\" }").build();
      }
    }

    return response;
  }

  public boolean topologyExists(String fileName, TopologyService topologyService) {
    return topologyService.getTopologies().stream()
            .anyMatch(topology -> topology.getName().equals(FilenameUtils.getBaseName(fileName)));
  }

  public boolean descriptorExists(String fileName, TopologyService topologyService) {
    return topologyService.getDescriptors().stream()
            .anyMatch(file -> FilenameUtils.getBaseName(file.getName()).equals(FilenameUtils.getBaseName(fileName)));
  }

  public boolean providerExists(String fileName, TopologyService topologyService) {
    return topologyService.getProviderConfigurations().stream()
            .anyMatch(file -> FilenameUtils.getBaseName(file.getName()).equals(FilenameUtils.getBaseName(fileName)));
  }

  @GET
  @Produces({APPLICATION_JSON})
  @Path(DESCRIPTORS_API_PATH)
  public HrefListing getSimpleDescriptors() {
    HrefListing listing = new HrefListing();
    listing.setHref(buildHref(request));

    GatewayServices services =
            (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    List<HrefListItem> descriptors = new ArrayList<>();
    TopologyService ts = services.getService(ServiceType.TOPOLOGY_SERVICE);
    for (File descriptor : ts.getDescriptors()){
      String id = FilenameUtils.getBaseName(descriptor.getName());
      descriptors.add(new HrefListItem(buildHref(id, request), descriptor.getName()));
    }

    listing.setItems(descriptors);
    return listing;
  }


  @GET
  @Produces({APPLICATION_JSON, TEXT_PLAIN})
  @Path(SINGLE_DESCRIPTOR_API_PATH)
  public Response getSimpleDescriptor(@PathParam("name") String name) {
    Response response;

    GatewayServices services =
            (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    TopologyService ts = services.getService(ServiceType.TOPOLOGY_SERVICE);

    File descriptorFile = null;

    for (File sd : ts.getDescriptors()){
      // If the file name matches the specified id
      if (FilenameUtils.getBaseName(sd.getName()).equals(name)) {
        descriptorFile = sd;
        break;
      }
    }

    if (descriptorFile != null) {
      String mediaType = APPLICATION_JSON;

      byte[] content;
      try {
        if ("yml".equals(FilenameUtils.getExtension(descriptorFile.getName()))) {
          mediaType = TEXT_PLAIN;
        }
        content = FileUtils.readFileToByteArray(descriptorFile);
        response = ok().type(mediaType).entity(content).build();
      } catch (IOException e) {
        log.failedToReadConfigurationFile(descriptorFile.getAbsolutePath(), e);
        response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
      }
    } else {
      response = Response.status(Response.Status.NOT_FOUND).build();
    }

    return response;
  }


  private String getFileNameForResource(String resourceName, HttpHeaders headers) {
    String filename;
    String extension = FilenameUtils.getExtension(resourceName);
    if (extension != null && !extension.isEmpty()) {
      filename = resourceName;
    } else {
      extension = getExtensionForMediaType(headers.getMediaType());
      filename = (extension != null) ? (resourceName + extension) : (resourceName + JSON_EXT);
    }
    return filename;
  }

  private String getExtensionForMediaType(MediaType type) {
    String extension = null;

    for (Map.Entry<MediaType, String> entry : mediaTypeFileExtensions.entrySet()) {
      if (type.isCompatible(entry.getKey())) {
        extension = entry.getValue();
        break;
      }
    }

    return extension;
  }

  private File getExistingConfigFile(Collection<File> existing, String candidateName) {
    File result = null;
    for (File exists : existing) {
      if (FilenameUtils.getBaseName(exists.getName()).equals(candidateName)) {
        result = exists;
        break;
      }
    }
    return result;
  }

  private static boolean isValidResourceName(final String name) {
    return name != null && name.length() <= RESOURCE_NAME_LENGTH_MAX &&
        RESOURCE_NAME_PATTERN.matcher(name).matches();
  }


  private static class TopologyComparator implements Comparator<SimpleTopology> {
    @Override
    public int compare(SimpleTopology t1, SimpleTopology t2) {
      return t1.getName().compareTo(t2.getName());
    }
  }


  String buildURI(org.apache.knox.gateway.topology.Topology topology, GatewayConfig config, HttpServletRequest req){
    String uri = buildXForwardBaseURL(req);

    // Strip extra context
    uri = uri.replace(req.getContextPath(), "");

    // Add the gateway path
    String gatewayPath;
    if(config.getGatewayPath() != null){
      gatewayPath = config.getGatewayPath();
    }else{
      gatewayPath = "gateway";
    }
    return uri + "/" + gatewayPath + "/" + topology.getName();
  }

  String buildHref(HttpServletRequest req) {
    return buildHref((String)null, req);
  }

  String buildHref(String id, HttpServletRequest req) {
    StringBuilder href = new StringBuilder(buildXForwardBaseURL(req));
    // Make sure that the pathInfo doesn't have any '/' chars at the end.
    String pathInfo = req.getPathInfo();
    while(pathInfo.endsWith("/")) {
      pathInfo = pathInfo.substring(0, pathInfo.length() - 1);
    }

    href.append(pathInfo);

    if (id != null) {
      href.append('/').append(id);
    }

    return href.toString();
  }

   String buildHref(org.apache.knox.gateway.topology.Topology t, HttpServletRequest req) {
     return buildHref(t.getName(), req);
  }

  private SimpleTopology getSimpleTopology(org.apache.knox.gateway.topology.Topology t, GatewayConfig config) {
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

    StringBuilder baseURL = new StringBuilder();

    // Get Protocol
    if(req.getHeader(X_Forwarded_Proto) != null){
      baseURL.append(req.getHeader(X_Forwarded_Proto)).append("://");
    } else {
      baseURL.append(req.getProtocol()).append("://");
    }

    // Handle Server/Host and Port Here
    if (req.getHeader(X_Forwarded_Host) != null && req.getHeader(X_Forwarded_Port) != null){
      // Double check to see if host has port
      if(req.getHeader(X_Forwarded_Host).contains(req.getHeader(X_Forwarded_Port))){
        baseURL.append(req.getHeader(X_Forwarded_Host));
      } else {
        // If there's no port, add the host and port together;
        baseURL.append(req.getHeader(X_Forwarded_Host)).append(':').append(req.getHeader(X_Forwarded_Port));
      }
    } else if(req.getHeader(X_Forwarded_Server) != null && req.getHeader(X_Forwarded_Port) != null){
      // Tack on the server and port if they're available. Try host if server not available
      baseURL.append(req.getHeader(X_Forwarded_Server)).append(':').append(req.getHeader(X_Forwarded_Port));
    } else if(req.getHeader(X_Forwarded_Port) != null) {
      // if we at least have a port, we can use it.
      baseURL.append(req.getServerName()).append(':').append(req.getHeader(X_Forwarded_Port));
    } else {
      // Resort to request members
      baseURL.append(req.getServerName()).append(':').append(req.getLocalPort());
    }

    // Handle Server context
    if( req.getHeader(X_Forwarded_Context) != null ) {
      baseURL.append(req.getHeader( X_Forwarded_Context ));
    } else {
      baseURL.append(req.getContextPath());
    }

    return baseURL.toString();
  }


  static class HrefListing {
    @JsonProperty
    String href;

    @JsonProperty
    List<HrefListItem> items;

    HrefListing() {}

    public void setHref(String href) {
      this.href = href;
    }

    public String getHref() {
      return href;
    }

    public void setItems(List<HrefListItem> items) {
      this.items = items;
    }

    public List<HrefListItem> getItems() {
      return items;
    }
  }

  static class HrefListItem {
    @JsonProperty
    String href;

    @JsonProperty
    String name;

    HrefListItem() {}

    HrefListItem(String href, String name) {
      this.href = href;
      this.name = name;
    }

    public void setHref(String href) {
      this.href = href;
    }

    public String getHref() {
      return href;
    }

    public void setName(String name) {
      this.name = name;
    }
    public String getName() {
      return name;
    }
  }


  @XmlAccessorType(XmlAccessType.NONE)
  public static class SimpleTopology {

    @XmlElement
    private String name;
    @XmlElement
    private String timestamp;
    @XmlElement
    private String defaultServicePath;
    @XmlElement
    private String uri;
    @XmlElement
    private String href;

    public SimpleTopology() {}

    public SimpleTopology(org.apache.knox.gateway.topology.Topology t, String uri, String href) {
      this.name = t.getName();
      this.timestamp = Long.toString(t.getTimestamp());
      this.defaultServicePath = t.getDefaultServicePath();
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

    public void setDefaultService(String defaultServicePath) {
      this.defaultServicePath = defaultServicePath;
    }

    public String getDefaultService() {
      return defaultServicePath;
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
    private List<SimpleTopology> topologies = new ArrayList<>();

    public List<SimpleTopology> getTopologies(){
      return topologies;
    }

    public void setTopologies(List<SimpleTopology> ts){
      this.topologies = ts;
    }

  }
}

