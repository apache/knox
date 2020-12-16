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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.annotations.Api;

import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.status;

/**
 * Rest API for Knox Alias Service
 * @since 1.3.0
 */
@Api(value = "alias",  description = "The Knox Admin API to interact with aliases.")
@Path("/api/v1")
public class AliasResource {
  private static final String ALIASES_API_PATH = "aliases";
  private static final String ALIASES_TOPOLOGY_API_PATH =
      ALIASES_API_PATH + "/{topology}";
  private static final String ALIAS_API_PATH =
      ALIASES_TOPOLOGY_API_PATH + "/{alias}";

  @Context
  private HttpServletRequest request;

  /**
   * Create alias with PUT
   * @param topology topology/cluster name
   * @param alias alias to be created
   * @param value value of the alias
   * @return json response
   */
  @PUT
  @Produces({ APPLICATION_JSON })
  @Consumes({ APPLICATION_XML, APPLICATION_JSON, TEXT_PLAIN })
  @Path(ALIAS_API_PATH)
  public Response putAlias(@PathParam("topology") final String topology,
      @PathParam("alias") final String alias, final String value) {
    return postAlias(topology, alias, value);
  }

  /**
   * Create alias with POST
   * @param topology topology/cluster name
   * @param alias alias to be created
   * @param value value of the alias
   * @return json response
   */
  @POST
  @Produces({ APPLICATION_JSON })
  @Consumes(APPLICATION_FORM_URLENCODED)
  @Path(ALIAS_API_PATH)
  public Response postAlias(@PathParam("topology") final String topology,
      @PathParam("alias") final String alias,
      @FormParam("value") final String value) {

    if (value == null || value.isEmpty()) {
      return status(BAD_REQUEST).
          entity("Alias value cannot be null or blank").
          type(MediaType.APPLICATION_JSON_TYPE).build();
    }

    final AliasService as = getAliasService();

    try {
      as.addAliasForCluster(topology, alias, value);
      return status(Response.Status.CREATED).
          entity("{ \"created\" : { \"topology\": \"" + topology
              + "\", \"alias\": \"" + alias + "\" } }").
          type(MediaType.APPLICATION_JSON_TYPE).
          build();
    } catch (AliasServiceException e) {
      return status(INTERNAL_SERVER_ERROR).
          entity("Error adding alias " + alias + " for cluster " + topology
              + ", reason: " + e.toString()).
          type(MediaType.APPLICATION_JSON_TYPE).build();
    }
  }

  /**
   * Delete alias
   *
   * @param topology topology/cluster name
   * @param alias alias to be deleted
   * @return json response
   */
  @DELETE
  @Produces({ APPLICATION_JSON })
  @Path(ALIAS_API_PATH)
  public Response deleteAlias(@PathParam("topology") final String topology,
      @PathParam("alias") final String alias) {

    final AliasService as = getAliasService();

    try {
      as.removeAliasForCluster(topology, alias);
      return status(Response.Status.OK).
          entity("{ \"deleted\" : { \"topology\": \"" + topology
              + "\", \"alias\": \"" + alias + "\" } }").
          type(MediaType.APPLICATION_JSON_TYPE).
          build();
    } catch (AliasServiceException e) {
      return status(INTERNAL_SERVER_ERROR).
          entity("Error deleting alias " + alias + " for cluster " + topology
              + ", reason: " + e.toString()).
          type(MediaType.APPLICATION_JSON_TYPE).build();
    }
  }

  /**
   * Given topology name get all the defined aliases.
   *
   * @param topology topology/cluster name
   * @return list of aliases
   * @since 1.3.0
   */
  @GET
  @Produces({ APPLICATION_JSON })
  @Path(ALIASES_TOPOLOGY_API_PATH)
  public Response getAliases(@PathParam("topology") final String topology) {

    final ObjectMapper mapper = new ObjectMapper();
    final AliasService as = getAliasService();

    List<String> aliases;
    try {
      aliases = as.getAliasesForCluster(topology);
      return status(Response.Status.OK).
          entity(
              mapper.writeValueAsString(new TopologyAliases(topology, aliases)))
          .
              type(MediaType.APPLICATION_JSON_TYPE).
              build();
    } catch (final Exception e) {
      return status(INTERNAL_SERVER_ERROR).
          entity(
              "Error getting aliases for cluster " + topology + ", reason: " + e
                  .toString()).
          type(MediaType.APPLICATION_JSON_TYPE).build();
    }
  }

  private AliasService getAliasService() {
    final GatewayServices services = (GatewayServices) request
        .getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    return services.getService(ServiceType.ALIAS_SERVICE);
  }

  public static class TopologyAliases {
    private String topology;
    private List<String> aliases;

    public TopologyAliases(final String topology, final List<String> aliases) {
      super();
      this.topology = topology;
      this.aliases = aliases;
    }

    public String getTopology() {
      return topology;
    }

    public void setTopology(String topology) {
      this.topology = topology;
    }

    public List<String> getAliases() {
      return aliases;
    }

    public void setAliases(List<String> aliases) {
      this.aliases = aliases;
    }
  }
}
