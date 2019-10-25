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
package org.apache.knox.gateway.service.definition;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.filter.rewrite.impl.xml.XmlUrlRewriteRulesExporter;
import org.apache.knox.gateway.filter.rewrite.impl.xml.XmlUrlRewriteRulesImporter;
import org.w3c.dom.Node;

/**
 * Serves as a gateway between JAXB marshal/unmarshal and DOM
 * serializing/digesting.
 */
public class UrlRewriteRulesDescriptorAdapter extends XmlAdapter<Object, UrlRewriteRulesDescriptor> {

  private final XmlUrlRewriteRulesExporter xmlRewriteRulesExporter = new XmlUrlRewriteRulesExporter();
  private final XmlUrlRewriteRulesImporter xmlRewriteRulesImporter = new XmlUrlRewriteRulesImporter();

  @Override
  public UrlRewriteRulesDescriptor unmarshal(Object value) throws Exception {
    try (InputStream is = nodeToInputStream((Node) value); InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
      return xmlRewriteRulesImporter.load(isr);
    }
  }

  private static InputStream nodeToInputStream(Node node) throws Exception {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      TransformerFactory.newInstance().newTransformer().transform(new DOMSource(node), new StreamResult(outputStream));
      return new ByteArrayInputStream(outputStream.toByteArray());
    }
  }

  @Override
  public Object marshal(UrlRewriteRulesDescriptor value) throws Exception {
    try (Writer writer = new StringWriter()) {
      return (Node) xmlRewriteRulesExporter.store(value, writer, true);
    }
  }
}
