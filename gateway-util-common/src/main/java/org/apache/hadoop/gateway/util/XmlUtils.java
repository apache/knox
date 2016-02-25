/**
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
package org.apache.hadoop.gateway.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

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
import org.xml.sax.SAXException;

public class XmlUtils {

  public static Document readXml( File file ) throws ParserConfigurationException, IOException, SAXException {
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    DocumentBuilder b = f.newDocumentBuilder();
    Document d = b.parse( file );
    return d;
  }

  public static void writeXml( Document document, Writer writer ) throws TransformerException {
    TransformerFactory f = TransformerFactory.newInstance();
//    if( f.getClass().getPackage().getName().equals( "com.sun.org.apache.xalan.internal.xsltc.trax" ) ) {
      f.setAttribute( "indent-number", 4 );
//    }
    Transformer t = f.newTransformer();
    t.setOutputProperty( OutputKeys.INDENT, "yes" );
    t.setOutputProperty( "{xml.apache.org/xslt}indent-amount", "4" );
    DOMSource s = new DOMSource( document );
    StreamResult r = new StreamResult( writer );
    t.transform( s, r );
  }

  public static void writeXml( Document document, File file ) throws TransformerException, IOException {
    writeXml( document, new FileWriter( file ) );
  }


  public static Document createDocument() throws ParserConfigurationException {
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    DocumentBuilder b = f.newDocumentBuilder();
    Document d = b.newDocument();
    d.setXmlStandalone( true );
    return d;
  }

}
