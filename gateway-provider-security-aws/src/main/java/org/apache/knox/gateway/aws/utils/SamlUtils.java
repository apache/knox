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
package org.apache.knox.gateway.aws.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.codec.binary.Base64;
import org.apache.knox.gateway.aws.AwsConstants;
import org.apache.knox.gateway.aws.model.AwsRolePrincipalSamlPair;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.Response;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Utility methods for SAML.
 */
public class SamlUtils {

  public static final String SAML_RESPONSE = "SAMLResponse";
  private static final String DISALLOW_DOCTYPE_DECL_ = "http://apache.org/xml/features/disallow-doctype-decl";
  private static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
  private static final DocumentBuilderFactory factory;
  private static UnmarshallerFactory unmarshallerFactory;

  static {
    try {
      InitializationService.initialize();
      unmarshallerFactory = XMLObjectProviderRegistrySupport.getUnmarshallerFactory();
      factory = DocumentBuilderFactory.newInstance();
      // Set attributes to defeat external entity injection
      // Ref - https://www.owasp.org/index.php/XML_External_Entity_%28XXE)_Prevention_Cheat_Sheet#JAXP_DocumentBuilderFactory.2C_SAXParserFactory_and_DOM4J
      factory.setNamespaceAware(true);
      factory.setFeature(DISALLOW_DOCTYPE_DECL_, true);
      factory.setFeature(LOAD_EXTERNAL_DTD, false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
    } catch (InitializationException | ParserConfigurationException e) {
      throw new RuntimeException("Could not initialize SAML utils setup", e);
    }
  }

  /**
   * Parses a SAML response for attributes.
   *
   * @param samlResponse the SAML response sent by Identity provider
   * @param attributes the attributes to fetch from assertion
   * @return {@link Map} containing the mapped keys in {@code attributes} with a {@link List} of
   * values found for the attribute
   * @throws ServletException if the parsing fails for the {@code samlResponse}
   */
  public static Map<String, List<Object>> parseAttributes(String samlResponse,
      Set<String> attributes) throws ServletException {
    Map<String, List<Object>> parsedResult = new HashMap<>();
    // TODO - Using a library for managing collections
    if (attributes == null || attributes.isEmpty()) {
      return parsedResult;
    }
    byte[] samlAssertionDecoded = new byte[0];
    try {
      samlAssertionDecoded = Base64.decodeBase64(samlResponse.getBytes("UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Unsupported encoding to decode Saml Response");
    }
    ByteArrayInputStream is = new ByteArrayInputStream(samlAssertionDecoded);
    Assertion assertion;
    try {
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = builder.parse(is);
      Element element = document.getDocumentElement();

      Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(element);
      if (unmarshaller == null) {
        throw new RuntimeException("Could not find an unmarshaller for XML parsing");
      }
      XMLObject responseXmlObj = unmarshaller.unmarshall(element);
      Response response = (Response) responseXmlObj;
      if (response.getAssertions() == null || response.getAssertions().isEmpty()) {
        throw new ServletException("No assertions found in SAML response");
      }
      assertion = response.getAssertions().get(0);
    } catch (UnmarshallingException | ParserConfigurationException | SAXException | IOException e) {
      throw new ServletException("Could not parse the SAML assertion", e);
    }

    for (AttributeStatement attributeStatement : assertion.getAttributeStatements()) {
      for (Attribute attribute : attributeStatement.getAttributes()) {
        if (attributes.contains(attribute.getName())) {
          List<Object> values = new ArrayList<>(attribute.getAttributeValues());
          parsedResult.put(attribute.getName(), values);
        }
      }
    }
    return parsedResult;
  }

  /**
   * Fetches the AWS Role attribute from a SAML response.
   * <p>
   * Valid AWS Role assertion contain a {@link List} of {@link AwsRolePrincipalSamlPair}.
   *
   * @param samlResponse the SAML response generated by Identity provider
   * @return List of {@link AwsRolePrincipalSamlPair} found in the {@code samlResponse}
   * @throws ServletException if the {@code samlResponse} cannot be parsed for a valid assertion
   */
  public static List<AwsRolePrincipalSamlPair> getSamlAwsRoleAttributeValues(String samlResponse)
      throws ServletException {
    List<AwsRolePrincipalSamlPair> pairs = new ArrayList<>();

    Map<String, List<Object>> parsedAttributes = parseAttributes(samlResponse,
        Collections.singleton(AwsConstants.AWS_SAML_ATTRIBUTE_NAME));
    List<Object> roles = parsedAttributes.get(AwsConstants.AWS_SAML_ATTRIBUTE_NAME);
    roles.forEach(val -> {
      String rolePrincipalPair = "";
      if (val instanceof  XSString) {
        rolePrincipalPair = ((XSString) val).getValue();
      }  else if (val instanceof XSAny) {
        rolePrincipalPair = ((XSAny) val).getTextContent();
      } else {
        // This will fail as we can't construct AwsRolePrincipalSamlPair with empty string
      }

      pairs.add(new AwsRolePrincipalSamlPair(rolePrincipalPair));
    });
    return pairs;
  }
}


