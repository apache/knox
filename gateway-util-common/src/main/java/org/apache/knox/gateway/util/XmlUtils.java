/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.xml.secure.SecureDocumentBuilderFactory;
import org.apache.commons.xml.secure.SecureTransformerFactory;
import org.apache.commons.xml.secure.SecureXMLInputFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * XML parsing and serialization helpers.
 *
 * <p>Factories come from <a href="https://commons.apache.org/proper/commons-secure-xml/">Apache Commons Secure XML</a>,
 * which ignores external resources (DTDs, entities, XInclude, stylesheets).</p>
 */
public class XmlUtils {

  public static Document readXml( File file ) throws ParserConfigurationException, IOException, SAXException {
    try (InputStream input = Files.newInputStream(file.toPath())) {
      return readXml(input);
    }
  }

  public static Document readXml( InputStream input ) throws ParserConfigurationException, IOException, SAXException {
    return newDocumentBuilder().parse( input );
  }

  public static Document readXml( InputSource source ) throws ParserConfigurationException, IOException, SAXException {
    return newDocumentBuilder().parse( source );
  }

  /**
   * Unmarshals the XML content of a file.
   *
   * @param unmarshaller the JAXB unmarshaller to use
   * @param type         the expected type of the root object
   * @param file         the file to read
   * @param <T>          the expected type of the root object
   * @return the root object of the content tree
   * @throws IOException   if the file cannot be read
   * @throws JAXBException if the content cannot be parsed or unmarshalled
   */
  public static <T> T unmarshal(Unmarshaller unmarshaller, Class<T> type, File file) throws IOException, JAXBException {
    try (InputStream input = Files.newInputStream(file.toPath())) {
      return unmarshal(unmarshaller, type, file.toURI().toString(), input);
    }
  }

  /**
   * Unmarshals XML content from a stream, parsed by Apache Commons Secure XML.
   *
   * @param unmarshaller the JAXB unmarshaller to use
   * @param type         the expected type of the root object
   * @param systemId     the system identifier of the content, used as base URI; may be {@code null}
   * @param input        the stream to read, not closed by this method
   * @param <T>          the expected type of the root object
   * @return the root object of the content tree
   * @throws JAXBException if the content cannot be parsed or unmarshalled
   */
  public static <T> T unmarshal(Unmarshaller unmarshaller, Class<T> type, String systemId, InputStream input) throws JAXBException {
    try {
      return type.cast(unmarshaller.unmarshal(SecureXMLInputFactory.newFactory().createXMLEventReader(systemId, input)));
    } catch (XMLStreamException e) {
      throw new JAXBException(e);
    }
  }

  public static void writeXml( Document document, Writer writer ) throws TransformerException {
    writeXml( document, writer, false );
  }

  public static void writeXml( Document document, Writer writer, boolean omitXmlHeader ) throws TransformerException {
    Transformer t = XmlUtils.getTransformer( false, true, 4, omitXmlHeader );
    writeXml( document, writer, t );
  }

  public static void writeXml( Document document, Writer writer, Transformer transformer ) throws TransformerException {
    DOMSource s = new DOMSource( document );
    StreamResult r = new StreamResult( writer );
    transformer.transform( s, r );
  }

  public static Transformer getTransformer( boolean standalone, boolean indent, int indentNumber,
                                            boolean omitXmlDeclaration) throws TransformerException {
    TransformerFactory f = SecureTransformerFactory.newInstance();
    if ( indent ) {
      f.setAttribute( "indent-number", indentNumber );
    }

    Transformer t = f.newTransformer();
    if ( standalone ) {
      t.setOutputProperty( OutputKeys.STANDALONE, "yes" );
    }
    if ( indent ) {
      t.setOutputProperty( OutputKeys.INDENT, "yes" );
      t.setOutputProperty( "{xml.apache.org/xslt}indent-amount", String.valueOf(indentNumber) );
    }
    if ( omitXmlDeclaration ) {
      t.setOutputProperty( OutputKeys.OMIT_XML_DECLARATION, "yes" );
    }

    return t;
  }

  public static Document createDocument() throws ParserConfigurationException {
    return createDocument(true);
  }

  public static Document createDocument(boolean standalone) throws ParserConfigurationException {
    Document d = newDocumentBuilder().newDocument();
    d.setXmlStandalone( standalone );
    return d;
  }

  private static DocumentBuilder newDocumentBuilder() {
    try {
      return SecureDocumentBuilderFactory.newInstance().newDocumentBuilder();
    } catch (final ParserConfigurationException e) {
      // JAXP implementations fail while the factory is configured, not when a builder is created,
      // so this is not expected to happen.
      throw new IllegalStateException("Failed to instantiate a DocumentBuilder.", e);
    }
  }

}
