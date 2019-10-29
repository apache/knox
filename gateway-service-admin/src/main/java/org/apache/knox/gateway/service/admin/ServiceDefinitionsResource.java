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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.serverError;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.apache.knox.gateway.service.definition.ServiceDefinitionPair;
import org.apache.knox.gateway.service.definition.ServiceDefinitionPairComparator;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.registry.ServiceDefinitionRegistry;
import org.apache.knox.gateway.services.registry.ServiceDefinitionRegistryException;

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
    return getServiceDefinitions((Predicate<? super ServiceDefinitionPair>) null, serviceOnly);
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
