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
package org.apache.knox.gateway.service.vault;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

@Path( "/vault/credentials" )
public class CredentialResource {
  @Context 
  private HttpServletRequest request;
  
  @GET
  @Path("{alias}")
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  public Response getCredential(@PathParam("alias") String alias) {
    if (alias != null && !alias.isEmpty()) {
      CredentialValue value = getCredentialValueForAlias(alias);
      if (value != null) {
          return ok(value).build();
      } else {
          return status(NOT_FOUND).build();
      }
    } else {
        return status(BAD_REQUEST).
            entity("Please provide a credential alias in the path").
              type(TEXT_PLAIN_TYPE).build();
    }
  }

  @GET
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  public Response getCredentials() {
    List<String> aliases = getCredentialsList();
    if (aliases != null) {
        return ok(aliases).build();
    } else {
        return status(NOT_FOUND).build();
    }
  }

  /**
   * @return
   */
  private List<String> getCredentialsList() {
    GatewayServices services = (GatewayServices)request.getServletContext().
        getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    String clusterName = (String) request.getServletContext().getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE);
    AliasService as = services.getService(GatewayServices.ALIAS_SERVICE);
    List<String> aliases = null;
    try {
      aliases = as.getAliasesForCluster(clusterName);
    } catch (AliasServiceException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return aliases;
  }

  /**
   * @param alias
   * @return
   */
  private CredentialValue getCredentialValueForAlias(String alias) {
    GatewayServices services = (GatewayServices)request.getServletContext().
        getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    String clusterName = (String) request.getServletContext().getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE);
    AliasService as = services.getService(GatewayServices.ALIAS_SERVICE);
    char[] credential = null;
    try {
      credential = as.getPasswordFromAliasForCluster(clusterName, alias);
    } catch (AliasServiceException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    if (credential != null) {
      return new CredentialValue(alias, new String(credential));
    }
    return null;
  }
  
  public static class CredentialValue {
    private String alias;
    private String credential;
    
    public CredentialValue(String alias, String credential) {
      super();
      this.alias = alias;
      this.credential = credential;
    }
    
    public String getAlias() {
      return alias;
    }
    public void setAlias(String alias) {
      this.alias = alias;
    }
    public String getCredential() {
      return credential;
    }
    public void setCredential(String credential) {
      this.credential = credential;
    }
  }
}
