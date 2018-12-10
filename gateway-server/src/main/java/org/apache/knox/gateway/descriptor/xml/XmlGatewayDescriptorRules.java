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
package org.apache.knox.gateway.descriptor.xml;

import org.apache.commons.beanutils.MethodUtils;
import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.FactoryCreateRule;
import org.apache.commons.digester3.Rule;
import org.apache.commons.digester3.binder.AbstractRulesModule;
import org.xml.sax.Attributes;

public class XmlGatewayDescriptorRules extends AbstractRulesModule implements XmlGatewayDescriptorTags {
  private static final Object[] NO_PARAMS = new Object[0];

  @Override
  protected void configure() {
    forPattern( GATEWAY ).addRule( new FactoryCreateRule( XmlGatewayDescriptorFactory.class ) );
    forPattern( GATEWAY + "/" + RESOURCE ).addRule( new AddNextRule( "addResource" ) );
    forPattern( GATEWAY + "/" + RESOURCE + "/" + RESOURCE_ROLE ).callMethod( "role" ).usingElementBodyAsArgument();
    forPattern( GATEWAY + "/" + RESOURCE + "/" + RESOURCE_PATTERN ).callMethod( "pattern" ).usingElementBodyAsArgument();
    forPattern( GATEWAY + "/" + RESOURCE + "/" + FILTER ).addRule( new AddNextRule( "addFilter" ) );
    forPattern( GATEWAY + "/" + RESOURCE + "/" + FILTER + "/" + FILTER_ROLE ).callMethod( "name" ).usingElementBodyAsArgument();
    forPattern( GATEWAY + "/" + RESOURCE + "/" + FILTER + "/" + FILTER_ROLE ).callMethod( "role" ).usingElementBodyAsArgument();
    forPattern( GATEWAY + "/" + RESOURCE + "/" + FILTER + "/" + FILTER_IMPL ).callMethod( "impl" ).usingElementBodyAsArgument();
    forPattern( GATEWAY + "/" + RESOURCE + "/" + FILTER + "/" + FILTER_PARAM ).addRule( new AddNextRule( "param") );
    forPattern( GATEWAY + "/" + RESOURCE + "/" + FILTER + "/" + FILTER_PARAM + "/" + FILTER_PARAM_NAME ).callMethod( "name" ).usingElementBodyAsArgument();
    forPattern( GATEWAY + "/" + RESOURCE + "/" + FILTER + "/" + FILTER_PARAM + "/" + FILTER_PARAM_VALUE ).callMethod( "value" ).usingElementBodyAsArgument();
  }

  private static class AddNextRule extends Rule {
    private String method;

    AddNextRule( String method ) {
      this.method = method;
    }

    @Override
    public void begin( String namespace, String name, Attributes attributes ) throws Exception {
      Digester digester = getDigester();
      digester.push( MethodUtils.invokeMethod( digester.peek(), method, NO_PARAMS ) );
    }

    @Override
    public void end( String namespace, String name ) {
      getDigester().pop();
    }
  }
}
