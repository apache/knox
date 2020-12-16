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

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.knox.gateway.services.ServiceType;

import io.swagger.annotations.Api;

import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServerInfoService;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.Response.ok;

@Api(value = "version",  description = "The Knox Admin API to interact with versioning information.")
@Path( "/api/v1" )
public class VersionResource {
  @Context
  private HttpServletRequest request;

  @GET
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  @Path( "version" )
  public Response getVersion() {
    ServerVersion version = getServerVersion();
    return ok(version).build();
  }

  private ServerVersion getServerVersion() {
    GatewayServices services = (GatewayServices)request.getServletContext().
        getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    ServerInfoService sis = services.getService(ServiceType.SERVER_INFO_SERVICE);

    return new ServerVersion(sis.getBuildVersion(), sis.getBuildHash());
  }

  @XmlRootElement(name="ServerVersion")
  public static class ServerVersion {

    @XmlElement(name="version")
    private String version;
    @XmlElement(name="hash")
    private String hash;

    public ServerVersion(String version, String hash) {
      super();
      this.version = version;
      this.hash = hash;
    }

    public ServerVersion() { }

    public String getVersion() {
      return version;
    }
    public void setVersion(String version) {
      this.version = version;
    }
    public String getHash() {
      return hash;
    }
    public void setHash(String hash) {
      this.hash = hash;
    }
  }
}
