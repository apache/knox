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

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class XmlUtils {

  public static Document readXml( File file ) throws ParserConfigurationException, IOException, SAXException {
    return readXml(Files.newInputStream(file.toPath()));
  }

  public static Document readXml( InputStream input ) throws ParserConfigurationException, IOException, SAXException {
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    DocumentBuilder b = f.newDocumentBuilder();
    return b.parse( input );
  }

  public static Document readXml( InputSource source ) throws ParserConfigurationException, IOException, SAXException {
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    DocumentBuilder b = f.newDocumentBuilder();
    return b.parse( source );
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
    TransformerFactory f = TransformerFactory.newInstance();
    f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
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
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    DocumentBuilder b = f.newDocumentBuilder();
    Document d = b.newDocument();
    d.setXmlStandalone( standalone );
    return d;
  }

}
