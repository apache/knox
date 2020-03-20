/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.util;

import org.apache.commons.digester3.binder.DigesterLoader;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.topology.builder.TopologyBuilder;
import org.apache.knox.gateway.topology.xml.AmbariFormatXmlTopologyRules;
import org.apache.knox.gateway.topology.xml.KnoxFormatXmlTopologyRules;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

import static org.apache.commons.digester3.binder.DigesterLoader.newLoader;

public final class TopologyUtils {

  private static final DigesterLoader digesterLoader = newLoader(new KnoxFormatXmlTopologyRules(),
                                                                 new AmbariFormatXmlTopologyRules());


  public static Topology parse(final String content) throws IOException, SAXException {
    Topology result;

    TopologyBuilder builder = digesterLoader.newDigester().parse(new StringReader(content));
    result = builder.build();

    return result;
  }

  public static Topology parse(final InputStream content) throws IOException, SAXException {
    Topology result;

    TopologyBuilder builder = digesterLoader.newDigester().parse(content);
    result = builder.build();

    return result;
  }

}
