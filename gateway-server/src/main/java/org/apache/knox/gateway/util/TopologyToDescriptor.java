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
package org.apache.knox.gateway.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.model.DescriptorConfiguration;
import org.apache.knox.gateway.model.ProviderConfiguration;
import org.apache.knox.gateway.model.Topology;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * A helper class to convert topology xml file to descriptor and provider
 * model.
 */
public class TopologyToDescriptor {

  private static final GatewayMessages LOG = MessagesFactory
      .get(GatewayMessages.class);
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final String SCHEMA_FILE = "/conf/topology-v1.xsd";

  static {
    /* skip printing out null fields */
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
  }

  private String topologyPath;
  private String providerName;
  private String descriptorName;
  private String discoveryUrl;
  private String discoveryUser;
  private String discoveryPasswordAlias;
  private String discoveryType;
  private String cluster;
  private String providerConfigDir;
  private String descriptorConfigDir;
  private boolean force;

  public TopologyToDescriptor() {
    super();
  }

  /**
   * A function that validates the given topology
   */
  private void validateTopology(final String xsd, final String topologyFile)
      throws IOException, SAXException {
    try (InputStream topologyFileStream = Files
        .newInputStream(Paths.get(topologyFile))) {
      final Source xmlSource = new StreamSource(topologyFileStream);
      final Schema schema = getSchema(xsd);
      final Validator validator = schema.newValidator();
      validator.validate(xmlSource);
    } catch (IOException | SAXException e) {
      LOG.errorValidatingTopology(topologyFile);
      throw e;
    }
  }

  private Topology parseTopology(final String xsd, final String topologyFile)
      throws JAXBException, SAXException, IOException {
    try (InputStream topologyFileStream = Files
        .newInputStream(Paths.get(topologyFile))) {
      final Schema schema = getSchema(xsd);
      final JAXBContext jc = JAXBContext.newInstance(Topology.class);
      final Unmarshaller unmarshaller = jc.createUnmarshaller();
      unmarshaller.setSchema(schema);
      return (Topology) unmarshaller.unmarshal(topologyFileStream);
    } catch (SAXException | JAXBException | IOException e) {
      LOG.errorParsingTopology(topologyFile);
      throw e;
    }
  }

  private Schema getSchema(final String xsd) throws SAXException {
    final SchemaFactory schemaFactory = SchemaFactory
        .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    final URL schemaUrl = TopologyToDescriptor.class.getResource(xsd);
    return schemaFactory.newSchema(schemaUrl);
  }

  /**
   * Validate topology
   *
   * @throws IOException Exception on validating file.
   * @throws SAXException Exception on parsing XML.
   */
  public void validate() throws IOException, SAXException {
    validateTopology(SCHEMA_FILE, topologyPath);
  }

  /**
   * Convert topology to provider and descriptor
   *
   * @throws IOException Exception on validating file.
   * @throws JAXBException Exception on parsing XML.
   * @throws SAXException Exception on parsing XML.
   */
  public void convert() throws IOException, JAXBException, SAXException {
    final Topology topology = parseTopology(SCHEMA_FILE, topologyPath);
    saveProvider(topology);
    saveDescriptor(topology, providerName);
  }

  private void saveProvider(final Topology topology) throws IOException {
    try {
      final ProviderConfiguration provider = new ProviderConfiguration();
      if (topology.getProviders() != null) {
        provider.setProviders(topology.getProviders());
      }

      final File providerFile = new File(
          providerConfigDir + File.separator + providerName);

      fileCheck(providerFile);

      mapper.writerWithDefaultPrettyPrinter()
          .writeValue(providerFile, provider);
    } catch (final IOException e) {
      LOG.errorSavingProviderConfiguration(providerName, topologyPath,
          e.toString());
      throw e;
    }
  }

  private void saveDescriptor(final Topology topology,
      final String providerName) throws IOException {
    final DescriptorConfiguration descriptorConfiguration = new DescriptorConfiguration();

    descriptorConfiguration
        .setProviderConfig(FilenameUtils.removeExtension(providerName));

    if (!StringUtils.isBlank(discoveryUrl)) {
      descriptorConfiguration.setDiscoveryAddress(discoveryUrl);
    }

    if (!StringUtils.isBlank(discoveryUser)) {
      descriptorConfiguration.setDiscoveryUser(discoveryUser);
    }

    if (!StringUtils.isBlank(discoveryPasswordAlias)) {
      descriptorConfiguration.setDiscoveryPasswordAlias(discoveryPasswordAlias);
    }

    if (!StringUtils.isBlank(discoveryType)) {
      descriptorConfiguration.setDiscoveryType(discoveryType);
    }

    if (!StringUtils.isBlank(cluster)) {
      descriptorConfiguration.setCluster(cluster);
    }

    if (topology.getName() != null) {
      descriptorConfiguration.setName(topology.getName());
    }

    if (topology.getApplications() != null) {
      descriptorConfiguration.setApplications(topology.getApplications());
    }

    if (topology.getServices() != null) {
      descriptorConfiguration.setServices(topology.getServices());
    }

    final File descriptorFile = new File(
        descriptorConfigDir + File.separator + descriptorName);

    fileCheck(descriptorFile);

    try {
      mapper.writerWithDefaultPrettyPrinter()
          .writeValue(descriptorFile, descriptorConfiguration);
    } catch (IOException e) {
      LOG.errorSavingDescriptorConfiguration(descriptorName, topologyPath,
          e.toString());
      throw e;
    }
  }

  /**
   * Check whether the file exists and can be overwritten.
   *
   * @param file
   * @throws IOException
   */
  private void fileCheck(final File file) throws IOException {
    if (!force && file.exists()) {
      throw new IOException(String
          .format(Locale.ROOT, "File %s already exist, use --force option to overwrite.",
              file.getAbsolutePath()));
    }
    /* make sure file and directories are in place */
    if (!file.getParentFile().exists()) {
      Files.createDirectories(file.toPath().getParent());
    }
    file.createNewFile();
  }

  public void setDiscoveryUrl(String discoveryUrl) {
    this.discoveryUrl = discoveryUrl;
  }

  public void setDiscoveryUser(String discoveryUser) {
    this.discoveryUser = discoveryUser;
  }

  public void setDiscoveryPasswordAlias(String discoveryPasswordAlias) {
    this.discoveryPasswordAlias = discoveryPasswordAlias;
  }

  public void setDiscoveryType(String discoveryType) {
    this.discoveryType = discoveryType;
  }

  public void setCluster(String cluster) {
    this.cluster = cluster;
  }

  public void setTopologyPath(String topologyPath) {
    this.topologyPath = topologyPath;
  }

  public void setProviderName(String providerName) {
    this.providerName = providerName;
  }

  public void setDescriptorName(String descriptorName) {
    this.descriptorName = descriptorName;
  }

  public void setProviderConfigDir(String providerConfigDir) {
    this.providerConfigDir = providerConfigDir;
  }

  public void setDescriptorConfigDir(String descriptorConfigDir) {
    this.descriptorConfigDir = descriptorConfigDir;
  }

  public void setForce(boolean force) {
    this.force = force;
  }
}
