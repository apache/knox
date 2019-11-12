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
package org.apache.knox.gateway.topology.discovery.ambari;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service URL pattern mapping configuration model.
 */
class ServiceURLPropertyConfig {

    private static final AmbariServiceDiscoveryMessages log = MessagesFactory.get(AmbariServiceDiscoveryMessages.class);

    private static final String ATTR_NAME = "name";

    private static XPathExpression SERVICE_URL_PATTERN_MAPPINGS;
    private static XPathExpression URL_PATTERN;
    private static XPathExpression PROPERTIES;
    static {
        XPathFactory xpathFactory = XPathFactory.newInstance();
        try {
            xpathFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        } catch (javax.xml.xpath.XPathFactoryConfigurationException ex) {
            // ignore
        }
        XPath xpath = xpathFactory.newXPath();
        try {
            SERVICE_URL_PATTERN_MAPPINGS = xpath.compile("/service-discovery-url-mappings/service");
            URL_PATTERN                  = xpath.compile("url-pattern/text()");
            PROPERTIES                   = xpath.compile("properties/property");
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
    }

    private static final String DEFAULT_SERVICE_URL_MAPPINGS = "ambari-service-discovery-url-mappings.xml";

    private Map<String, URLPattern> urlPatterns = new HashMap<>();

    private Map<String, Map<String, Property>> properties = new HashMap<>();


    /**
     * The default service URL pattern to property mapping configuration will be used.
     */
    ServiceURLPropertyConfig() {
        this(ServiceURLPropertyConfig.class.getClassLoader().getResourceAsStream(DEFAULT_SERVICE_URL_MAPPINGS));
    }

    /**
     * The default service URL pattern to property mapping configuration will be used.
     */
    ServiceURLPropertyConfig(File mappingConfigurationFile) throws Exception {
        this(Files.newInputStream(mappingConfigurationFile.toPath()));
    }

    /**
     *
     * @param source An InputStream for the XML content
     */
    ServiceURLPropertyConfig(InputStream source) {
        // Parse the XML, and build the model
        try {
            Document doc = XmlUtils.readXml(source);

            NodeList serviceNodes =
                    (NodeList) SERVICE_URL_PATTERN_MAPPINGS.evaluate(doc, XPathConstants.NODESET);
            for (int i=0; i < serviceNodes.getLength(); i++) {
                Node serviceNode = serviceNodes.item(i);
                String serviceName = serviceNode.getAttributes().getNamedItem(ATTR_NAME).getNodeValue();
                properties.put(serviceName, new HashMap<>());

                Node urlPatternNode = (Node) URL_PATTERN.evaluate(serviceNode, XPathConstants.NODE);
                if (urlPatternNode != null) {
                    urlPatterns.put(serviceName, new URLPattern(urlPatternNode.getNodeValue()));
                }

                NodeList propertiesNode = (NodeList) PROPERTIES.evaluate(serviceNode, XPathConstants.NODESET);
                if (propertiesNode != null) {
                    processProperties(serviceName, propertiesNode);
                }
            }
        } catch (Exception e) {
            log.failedToLoadServiceDiscoveryURLDefConfiguration(e);
        } finally {
            try {
                source.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    private void processProperties(String serviceName, NodeList propertyNodes) {
        for (int i = 0; i < propertyNodes.getLength(); i++) {
            Property p = Property.createProperty(serviceName, propertyNodes.item(i));
            properties.get(serviceName).put(p.getName(), p);
        }
    }

    URLPattern getURLPattern(String service) {
        return urlPatterns.get(service);
    }


    void setAll(ServiceURLPropertyConfig overrides) {
        if (overrides != null) {
            // URL patterns
            if (overrides.urlPatterns != null) {
                for (String service : overrides.urlPatterns.keySet()) {
                    URLPattern overridePattern = overrides.urlPatterns.get(service);
                    if (this.urlPatterns.containsKey(service)) {
                        this.urlPatterns.replace(service, overridePattern);
                    } else {
                        this.urlPatterns.put(service, overridePattern);
                    }
                }
            }

            // Properties
            for (String service : overrides.properties.keySet()) {
                Map<String, Property> serviceProperties = overrides.properties.get(service);
                if (serviceProperties != null) {
                    // Remove the original property set for this service
                    Map<String, Property> existingServiceProps = this.properties.get(service);
                    if (existingServiceProps != null) {
                        existingServiceProps.clear();
                    }

                    // Add the override properties
                    for (Map.Entry<String, Property> entry : serviceProperties.entrySet()) {
                        setConfigProperty(service, entry.getKey(), entry.getValue());
                    }
                }
            }
        }
    }

    void setConfigProperty(String service, String name, Property value) {
        Map<String, Property> serviceProperties = properties.computeIfAbsent(service, k -> new HashMap<>());
        serviceProperties.put(name, value);
    }

    Property getConfigProperty(String service, String property) {
        Property result = null;
        Map<String, Property> serviceProperties = properties.get(service);
        if (serviceProperties != null) {
            result = serviceProperties.get(property);
        }
        return result;
    }

    static class URLPattern {
        String pattern;
        List<String> placeholders = new ArrayList<>();

        URLPattern(String pattern) {
            this.pattern = pattern;

            final Pattern regex = Pattern.compile("\\{(.*?)}", Pattern.DOTALL);
            final Matcher matcher = regex.matcher(pattern);
            while( matcher.find() ){
                placeholders.add(matcher.group(1));
            }
        }

        String get() {return pattern; }
        List<String> getPlaceholders() {
            return placeholders;
        }
    }

    static class Property {
        static final String TYPE_SERVICE   = "SERVICE";
        static final String TYPE_COMPONENT = "COMPONENT";
        static final String TYPE_DERIVED   = "DERIVED";

        static final String PROP_COMP_HOSTNAME = "component.host.name";

        static final String ATTR_NAME     = "name";
        static final String ATTR_PROPERTY = "property";
        static final String ATTR_VALUE    = "value";

        static XPathExpression HOSTNAME;
        static XPathExpression SERVICE_CONFIG;
        static XPathExpression COMPONENT;
        static XPathExpression CONFIG_PROPERTY;
        static XPathExpression IF;
        static XPathExpression THEN;
        static XPathExpression ELSE;
        static XPathExpression TEXT;
        static {
            XPath xpath = XPathFactory.newInstance().newXPath();
            try {
                HOSTNAME        = xpath.compile("hostname");
                SERVICE_CONFIG  = xpath.compile("service-config");
                COMPONENT       = xpath.compile("component");
                CONFIG_PROPERTY = xpath.compile("config-property");
                IF              = xpath.compile("if");
                THEN            = xpath.compile("then");
                ELSE            = xpath.compile("else");
                TEXT            = xpath.compile("text()");
            } catch (XPathExpressionException e) {
                e.printStackTrace();
            }
        }


        String type;
        String name;
        String component;
        String service;
        String serviceConfig;
        String value;
        ConditionalValueHandler conditionHandler;

        private Property(String type,
                         String propertyName,
                         String component,
                         String service,
                         String configType,
                         String value,
                         ConditionalValueHandler pch) {
            this.type = type;
            this.name = propertyName;
            this.service = service;
            this.component = component;
            this.serviceConfig = configType;
            this.value = value;
            conditionHandler = pch;
        }

        static Property createProperty(String serviceName, Node propertyNode) {
            String propertyName = propertyNode.getAttributes().getNamedItem(ATTR_NAME).getNodeValue();
            String propertyType = null;
            String serviceType = null;
            String configType = null;
            String componentType = null;
            String value = null;
            ConditionalValueHandler pch = null;

            try {
                Node hostNameNode = (Node) HOSTNAME.evaluate(propertyNode, XPathConstants.NODE);
                if (hostNameNode != null) {
                    value = PROP_COMP_HOSTNAME;
                }

                // Check for a service-config node
                Node scNode = (Node) SERVICE_CONFIG.evaluate(propertyNode, XPathConstants.NODE);
                if (scNode != null) {
                    // Service config property
                    propertyType = Property.TYPE_SERVICE;
                    serviceType = scNode.getAttributes().getNamedItem(ATTR_NAME).getNodeValue();
                    Node scTextNode = (Node) TEXT.evaluate(scNode, XPathConstants.NODE);
                    configType = scTextNode.getNodeValue();
                } else { // If not service-config node, check for a component config node
                    Node cNode = (Node) COMPONENT.evaluate(propertyNode, XPathConstants.NODE);
                    if (cNode != null) {
                        // Component config property
                        propertyType = Property.TYPE_COMPONENT;
                        componentType = cNode.getFirstChild().getNodeValue();
                        Node cTextNode = (Node) TEXT.evaluate(cNode, XPathConstants.NODE);
                        configType = cTextNode.getNodeValue();
                        componentType = cTextNode.getNodeValue();
                    }
                }

                // Check for a config property node
                Node cpNode = (Node) CONFIG_PROPERTY.evaluate(propertyNode, XPathConstants.NODE);
                if (cpNode != null) {
                    // Check for a condition element
                    Node ifNode = (Node) IF.evaluate(cpNode, XPathConstants.NODE);
                    if (ifNode != null) {
                        propertyType = TYPE_DERIVED;
                        pch = getConditionHandler(serviceName, ifNode);
                    } else {
                        Node cpTextNode = (Node) TEXT.evaluate(cpNode, XPathConstants.NODE);
                        value = cpTextNode.getNodeValue();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Create and return the property representation
            return new Property(propertyType, propertyName, componentType, serviceType, configType, value, pch);
        }

        private static ConditionalValueHandler getConditionHandler(String serviceName, Node ifNode) throws Exception {
            ConditionalValueHandler result = null;

            if (ifNode != null) {
                NamedNodeMap attrs = ifNode.getAttributes();
                String comparisonPropName = attrs.getNamedItem(ATTR_PROPERTY).getNodeValue();

                String comparisonValue = null;
                Node valueNode = attrs.getNamedItem(ATTR_VALUE);
                if (valueNode != null) {
                    comparisonValue = attrs.getNamedItem(ATTR_VALUE).getNodeValue();
                }

                ConditionalValueHandler affirmativeResult = null;
                Node thenNode = (Node) THEN.evaluate(ifNode, XPathConstants.NODE);
                if (thenNode != null) {
                    Node subIfNode = (Node) IF.evaluate(thenNode, XPathConstants.NODE);
                    if (subIfNode != null) {
                        affirmativeResult = getConditionHandler(serviceName, subIfNode);
                    } else {
                        affirmativeResult = new SimpleValueHandler(thenNode.getFirstChild().getNodeValue());
                    }
                }

                ConditionalValueHandler negativeResult = null;
                Node elseNode = (Node) ELSE.evaluate(ifNode, XPathConstants.NODE);
                if (elseNode != null) {
                    Node subIfNode = (Node) IF.evaluate(elseNode, XPathConstants.NODE);
                    if (subIfNode != null) {
                        negativeResult = getConditionHandler(serviceName, subIfNode);
                    } else {
                        negativeResult = new SimpleValueHandler(elseNode.getFirstChild().getNodeValue());
                    }
                }

                result = new PropertyEqualsHandler(serviceName,
                        comparisonPropName,
                        comparisonValue,
                        affirmativeResult,
                        negativeResult);
            }

            return result;
        }

        String getType() { return type; }
        String getName() { return name; }
        String getComponent() { return component; }
        String getService() { return service; }
        String getServiceConfig() { return serviceConfig; }
        String getValue() {
            return value;
        }
        ConditionalValueHandler getConditionHandler() { return conditionHandler; }
    }
}
