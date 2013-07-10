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
package org.apache.hadoop.gateway.services.registry.impl;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.services.Service;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.services.registry.ServiceRegistry;
import org.apache.hadoop.gateway.services.security.CryptoService;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DefaultServiceRegistryService implements ServiceRegistry, Service {
  private static GatewayMessages LOG = MessagesFactory.get( GatewayMessages.class );
  
  protected char[] chars = { 'a', 'b', 'c', 'd', 'e', 'f', 'g',
  'h', 'j', 'k', 'm', 'n', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w',
  'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K',
  'M', 'N', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
  '2', '3', '4', '5', '6', '7', '8', '9',};

  private CryptoService crypto;
  private Registry registry = new Registry();

  private String registryFileName;
  
  public DefaultServiceRegistryService() {
  }
  
  public void setCryptoService(CryptoService crypto) {
    this.crypto = crypto;
  }
  
  public String getRegistrationCode(String clusterName) {
    String code = generateRegCode(16);
    byte[] signature = crypto.sign("SHA256withRSA","gateway-identity",code);
    String encodedSig = Base64.encodeBase64URLSafeString(signature);
    
    return code + "::" + encodedSig;
  }
  
  private String generateRegCode(int length) {
    StringBuffer sb = new StringBuffer();
    Random r = new Random();
    for (int i = 0; i < length; i++) {
      sb.append(chars[r.nextInt(chars.length)]);
    }
    return sb.toString();
  }
  
  public void removeClusterServices(String clusterName) {
    registry.remove(clusterName);
  }

  public boolean registerService(String regCode, String clusterName, String serviceName, String url) {
    boolean rc = false;
    // verify the signature of the regCode
    if (regCode == null) {
      throw new IllegalArgumentException("Registration Code must not be null.");
    }
    String[] parts = regCode.split("::");
    
    // part one is the code and part two is the signature
    boolean verified = crypto.verify("SHA256withRSA", "gateway-identity", parts[0], Base64.decodeBase64(parts[1]));
    if (verified) {
      HashMap<String,RegEntry> clusterServices = registry.get(clusterName);
      if (clusterServices == null) {
        synchronized(this) {
          clusterServices = new HashMap<String,RegEntry>();
          registry.put(clusterName, clusterServices);
        }
      }
      RegEntry regEntry = new RegEntry();
      regEntry.setClusterName(clusterName);
      regEntry.setServiceName(serviceName);
      regEntry.setUrl(url);
      clusterServices.put(serviceName , regEntry);
      String json = renderAsJsonString(registry);
      try {
        FileUtils.write(new File(registryFileName), json);
        rc = true;
      } catch (IOException e) {
        // log appropriately
        e.printStackTrace();
      }
    }
    
    return rc;
  }
  
  private String renderAsJsonString(HashMap<String,HashMap<String,RegEntry>> registry) {
    String json = null;
    ObjectMapper mapper = new ObjectMapper();
    
    try {
      // write JSON to a file
      json = mapper.writeValueAsString((Object)registry);
    
    } catch ( JsonProcessingException e ) {
      e.printStackTrace();
    }
    return json;
  }
  
  public String lookupServiceURL(String clusterName, String serviceName) {
    RegEntry entry = null;
    HashMap clusterServices = registry.get(clusterName);
    if (clusterServices != null) {
      entry = (RegEntry) clusterServices.get(serviceName);
    }
    return entry.url;
  }
  
  private HashMap<String, HashMap<String,RegEntry>> getMapFromJsonString(String json) {
    Registry map = null;
    JsonFactory factory = new JsonFactory(); 
    ObjectMapper mapper = new ObjectMapper(factory); 
    TypeReference<Registry> typeRef 
          = new TypeReference<Registry>() {}; 
    try {
      map = mapper.readValue(json, typeRef);
    } catch (JsonParseException e) {
      LOG.failedToGetMapFromJsonString( json, e );
    } catch (JsonMappingException e) {
      LOG.failedToGetMapFromJsonString( json, e );
    } catch (IOException e) {
      LOG.failedToGetMapFromJsonString( json, e );
    } 
    return map;
  }   

  @Override
  public void init(GatewayConfig config, Map<String, String> options)
      throws ServiceLifecycleException {
    String securityDir = config.getGatewayHomeDir() + File.separator + "conf" + File.separator + "security";
    String filename = "registry";
    setupRegistryFile(securityDir, filename);
  }

  protected void setupRegistryFile(String securityDir, String filename) throws ServiceLifecycleException {
    File registryFile = new File(securityDir, filename);
    if (registryFile.exists()) {
      try {
        String json = FileUtils.readFileToString(registryFile);
        registry = (Registry) getMapFromJsonString(json);
      } catch (Exception e) {
        throw new ServiceLifecycleException("Unable to load the persisted registry.", e);
      }
    }
    registryFileName = registryFile.getAbsolutePath();
  }

  @Override
  public void start() throws ServiceLifecycleException {
  }

  @Override
  public void stop() throws ServiceLifecycleException {
  }

}
