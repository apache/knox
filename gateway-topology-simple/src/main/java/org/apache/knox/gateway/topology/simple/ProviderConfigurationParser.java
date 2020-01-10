/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.simple;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.FilenameUtils;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProviderConfigurationParser {
  private static final JAXBContext jaxbContext = getJAXBContext();

  private static final String EXT_XML  = "xml";
  private static final String EXT_JSON = "json";
  private static final String EXT_YML  = "yml";
  private static final String EXT_YAML = "yaml";

  public static final List<String> SUPPORTED_EXTENSIONS = Collections.unmodifiableList(Arrays.asList(EXT_XML, EXT_JSON, EXT_YML, EXT_YAML));

  private static JAXBContext getJAXBContext() {
    try {
      return JAXBContext.newInstance(XMLProviderConfiguration.class);
    } catch (JAXBException e) {
      throw new IllegalStateException(e);
    }
  }

  public static ProviderConfiguration parse(String path) throws Exception {
    return parse(new File(path));
  }

  public static ProviderConfiguration parse(File file) throws Exception {
    ProviderConfiguration providerConfig = null;

    String extension = FilenameUtils.getExtension(file.getName());
    if (SUPPORTED_EXTENSIONS.contains(extension)) {

      if (isXML(extension)) {
        providerConfig = parseXML(file);
      } else if (isJSON(extension)) {
        providerConfig = parseJSON(file);
      } else if (isYAML(extension)) {
        providerConfig = parseYAML(file);
      }
    } else {
      throw new IllegalArgumentException("Unsupported provider configuration format: " + extension);
    }

    return providerConfig;
  }

  private static boolean isXML(String extension) {
    return EXT_XML.equals(extension);
  }

  private static boolean isJSON(String extension) {
    return EXT_JSON.equals(extension);
  }

  private static boolean isYAML(String extension) {
    return EXT_YAML.equals(extension) || EXT_YML.equals(extension);
  }


  static ProviderConfiguration parseXML(File file) throws Exception {
    return parseXML(Files.newInputStream(file.toPath()));
  }


  static ProviderConfiguration parseXML(InputStream in) throws Exception {
    XMLProviderConfiguration providerConfig;
    Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
    providerConfig = (XMLProviderConfiguration) jaxbUnmarshaller.unmarshal(in);

    return providerConfig;
  }


  static ProviderConfiguration parseJSON(File file) throws IOException {
    return parseJSON(Files.newInputStream(file.toPath()));
  }


  static ProviderConfiguration parseJSON(InputStream in) throws IOException {
    final ObjectMapper mapper = new ObjectMapper();
    return mapper.readValue(in, JSONProviderConfiguration.class);
  }


  static ProviderConfiguration parseYAML(File file) throws IOException {
    return parseYAML(Files.newInputStream(file.toPath()));
  }

  static ProviderConfiguration parseYAML(InputStream in) throws IOException {
    final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    return mapper.readValue(in, JSONProviderConfiguration.class);
  }


  // XML Provider Configuration Model
  @XmlRootElement(name="gateway")
  private static class XMLProviderConfiguration implements ProviderConfiguration {

    @XmlElement(name="provider")
    private List<XMLProvider> providers;

    @Override
    public List<Provider> getProviders() {
      List<Provider> plist = new ArrayList<>();
      if (providers != null) {
        plist.addAll(providers);
      }
      return plist;
    }

    @XmlAccessorType(XmlAccessType.NONE)
    private static class XMLProvider implements ProviderConfiguration.Provider {
      @XmlElement
      private String role;

      @XmlElement
      private String name;

      @XmlElement
      private boolean enabled;

      @XmlElement(name = "param")
      private List<XMLParam> params;

      @Override
      public String getRole() {
        return role;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public boolean isEnabled() {
        return enabled;
      }

      @Override
      public Map<String, String> getParams() {
        Map<String, String> result = new LinkedHashMap<>();
        if (params != null) {
          for (XMLParam p : params) {
            result.put(p.name, p.value);
          }
        }
        return result;
      }

      @XmlAccessorType(XmlAccessType.NONE)
      private static class XMLParam {
        @XmlElement
        private String name;

        @XmlElement
        private String value;

        String getName() {
          return name;
        }

        String getValue() {
          return value;
        }
      }
    }

  }


  // JSON/YAML Provider Configuration Model
  private static class JSONProviderConfiguration implements ProviderConfiguration {

    @JsonProperty("providers")
    private List<JSONProvider> providers;

    @Override
    public List<Provider> getProviders() {
      List<Provider> plist = new ArrayList<>();
      if (providers != null) {
        plist.addAll(providers);
      }
      return plist;
    }

    private static class JSONProvider implements ProviderConfiguration.Provider {

      @JsonProperty("role")
      private String role;

      @JsonProperty("name")
      private String name;

      @JsonProperty("enabled")
      private boolean enabled;

      @JsonProperty("params")
      private Map<String, String> params;

      @Override
      public String getRole() {
        return role;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public boolean isEnabled() {
        return enabled;
      }

      @Override
      public Map<String, String> getParams() {
        Map<String, String> result = new LinkedHashMap<>();
        if (params != null) {
          result.putAll(params);
        }
        return result;
      }
    }

  }


}
