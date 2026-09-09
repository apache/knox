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

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_XML;
import static jakarta.ws.rs.core.Response.created;
import static jakarta.ws.rs.core.Response.ok;
import static jakarta.ws.rs.core.Response.serverError;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;

import org.apache.knox.gateway.service.definition.ServiceDefinitionPair;
import org.apache.knox.gateway.service.definition.ServiceDefinitionPairComparator;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.registry.ServiceDefinitionRegistry;
import org.apache.knox.gateway.services.registry.ServiceDefinitionRegistryException;

import io.swagger.annotations.Api;

@Api(value = "serviceDefinition",  description = "The Knox Admin API to interact with service definition information.")
@Path("/api/v1")
@Singleton
public class ServiceDefinitionsResource {

  private static final String ERROR_CODE_CREATION = "CREATION_ERROR";
  private static final String ERROR_CODE_CREATION_OR_UPDATE = "CREATION_OR_UPDATE_ERROR";
  private static final String ERROR_CODE_DELETION = "DELETION_ERROR";

  @Context
  private HttpServletRequest request;

  private ServiceDefinitionRegistry serviceDefinitionRegistry;

  @GET
  @Produces({ APPLICATION_JSON, APPLICATION_XML })
  @Path("servicedefinitions")
  public ServiceDefinitionsWrapper getServiceDefinitions(@QueryParam("serviceOnly") @DefaultValue("false") boolean serviceOnly) {
    return getServiceDefinitions(null, serviceOnly);
  }

  @GET
  @Produces({ APPLICATION_JSON, APPLICATION_XML })
  @Path("servicedefinitions/{name}")
  public ServiceDefinitionsWrapper getServiceDefinition(@PathParam("name") String name, @QueryParam("serviceOnly") @DefaultValue("false") boolean serviceOnly) {
    return getServiceDefinitions(serviceDefinitionPair -> serviceDefinitionPair.getService().getName().equalsIgnoreCase(name), serviceOnly);
  }

  @GET
  @Produces({ APPLICATION_JSON, APPLICATION_XML })
  @Path("servicedefinitions/{name}/{role}")
  public ServiceDefinitionsWrapper getServiceDefinition(@PathParam("name") String name, @PathParam("role") String role,
      @QueryParam("serviceOnly") @DefaultValue("false") boolean serviceOnly) {
    return getServiceDefinitions(
        serviceDefinitionPair -> serviceDefinitionPair.getService().getName().equalsIgnoreCase(name) && serviceDefinitionPair.getService().getRole().equalsIgnoreCase(role),
        serviceOnly);
  }

  @GET
  @Produces({ APPLICATION_JSON, APPLICATION_XML })
  @Path("servicedefinitions/{name}/{role}/{version}")
  public ServiceDefinitionsWrapper getServiceDefinition(@PathParam("name") String name, @PathParam("role") String role, @PathParam("version") String version,
      @QueryParam("serviceOnly") @DefaultValue("false") boolean serviceOnly) {
    return getServiceDefinitions(serviceDefinitionPair -> serviceDefinitionPair.getService().getName().equalsIgnoreCase(name)
        && serviceDefinitionPair.getService().getRole().equalsIgnoreCase(role) && serviceDefinitionPair.getService().getVersion().equalsIgnoreCase(version), serviceOnly);
  }

  @POST
  @Consumes({ APPLICATION_XML })
  @Produces({ APPLICATION_JSON, APPLICATION_XML })
  @Path("servicedefinitions")
  public Response saveServiceDefinition(ServiceDefinitionPair serviceDefinition) {
    try {
      getServiceDefinitionRegistry().saveServiceDefinition(serviceDefinition);
      return created(toUri(serviceDefinition)).build();
    } catch (URISyntaxException | ServiceDefinitionRegistryException e) {
      return serverError().entity("{ \"" + ERROR_CODE_CREATION + "\": \"" + e.getMessage() + "\" }").build();
    }
  }

  @PUT
  @Consumes({ APPLICATION_XML })
  @Produces({ APPLICATION_JSON, APPLICATION_XML })
  @Path("servicedefinitions")
  public Response saveOrUpdateServiceDefinition(ServiceDefinitionPair serviceDefinition) {
    try {
      getServiceDefinitionRegistry().saveOrUpdateServiceDefinition(serviceDefinition);
      return created(toUri(serviceDefinition)).build();
    } catch (URISyntaxException | ServiceDefinitionRegistryException e) {
      return serverError().entity("{ \"" + ERROR_CODE_CREATION_OR_UPDATE + "\": \"" + e.getMessage() + "\" }").build();
    }
  }

  @DELETE
  @Produces({ APPLICATION_JSON, APPLICATION_XML })
  @Path("servicedefinitions/{name}/{role}/{version}")
  public Response deleteServiceDefinition(@PathParam("name") String name, @PathParam("role") String role, @PathParam("version") String version) {
    try {
      getServiceDefinitionRegistry().deleteServiceDefinition(name, role, version);
      return ok().location(toUri(name, role, version)).build();
    } catch (URISyntaxException | ServiceDefinitionRegistryException e) {
      return serverError().entity("{ \"" + ERROR_CODE_DELETION + "\": \"" + e.getMessage() + "\" }").build();
    }
  }

  private URI toUri(ServiceDefinitionPair serviceDefinition) throws URISyntaxException {
    return toUri(serviceDefinition.getService().getName(), serviceDefinition.getService().getRole(), serviceDefinition.getService().getVersion());
  }

  private URI toUri(String name, String role, String version) throws URISyntaxException {
    return new URI("api/v1/servicedefinitions/" + name + "/" + role + "/" + version);
  }

  private ServiceDefinitionsWrapper getServiceDefinitions(Predicate<? super ServiceDefinitionPair> predicate, boolean serviceOnly) {
    final Set<ServiceDefinitionPair> serviceDefinitions = getServiceDefinitions(predicate);

    final ServiceDefinitionsWrapper serviceDefinitionsWrapper = new ServiceDefinitionsWrapper();
    if (serviceOnly) {
      serviceDefinitions.stream()
      .forEach(serviceDefinition -> serviceDefinitionsWrapper.getServiceDefinitions().add(new ServiceDefinitionPair(serviceDefinition.getService(), null)));
    } else {
      serviceDefinitionsWrapper.setServiceDefinitions(serviceDefinitions);
    }
    return serviceDefinitionsWrapper;
  }

  private Set<ServiceDefinitionPair> getServiceDefinitions(Predicate<? super ServiceDefinitionPair> predicate) {
    final Set<ServiceDefinitionPair> serviceDefinitions = getServiceDefinitionRegistry().getServiceDefinitions();
    return predicate == null ? serviceDefinitions : serviceDefinitions.stream().filter(predicate).collect(Collectors.toSet());
  }

  private ServiceDefinitionRegistry getServiceDefinitionRegistry() {
    if (serviceDefinitionRegistry == null) {
      final GatewayServices gatewayServices = (GatewayServices) request.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
      serviceDefinitionRegistry = gatewayServices.getService(ServiceType.SERVICE_DEFINITION_REGISTRY);
    }
    return serviceDefinitionRegistry;
  }

  @XmlAccessorType(XmlAccessType.NONE)
  public static class ServiceDefinitionsWrapper {

    @XmlElement(name = "serviceDefinition")
    @XmlElementWrapper(name = "serviceDefinitions")
    private Set<ServiceDefinitionPair> serviceDefinitions = new TreeSet<>(new ServiceDefinitionPairComparator());

    public Set<ServiceDefinitionPair> getServiceDefinitions() {
      return serviceDefinitions;
    }

    public void setServiceDefinitions(Set<ServiceDefinitionPair> serviceDefinitions) {
      this.serviceDefinitions = serviceDefinitions;
    }
  }
}
