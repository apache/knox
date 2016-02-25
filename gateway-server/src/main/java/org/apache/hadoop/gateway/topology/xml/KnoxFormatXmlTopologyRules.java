/**
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
package org.apache.hadoop.gateway.topology.xml;

import org.apache.commons.digester3.Rule;
import org.apache.commons.digester3.binder.AbstractRulesModule;
import org.apache.hadoop.gateway.topology.Application;
import org.apache.hadoop.gateway.topology.Param;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.Service;
import org.apache.hadoop.gateway.topology.Version;
import org.apache.hadoop.gateway.topology.builder.BeanPropertyTopologyBuilder;
import org.xml.sax.Attributes;

public class KnoxFormatXmlTopologyRules extends AbstractRulesModule {

  private static final String ROOT_TAG = "topology";
  private static final String NAME_TAG = "name";
  private static final String VERSION_TAG = "version";
  private static final String APPLICATION_TAG = "application";
  private static final String SERVICE_TAG = "service";
  private static final String ROLE_TAG = "role";
  private static final String URL_TAG = "url";
  private static final String PROVIDER_TAG = "gateway/provider";
  private static final String ENABLED_TAG = "enabled";
  private static final String PARAM_TAG = "param";
  private static final String VALUE_TAG = "value";

  private static final Rule paramRule = new ParamRule();

  @Override
  protected void configure() {
    forPattern( ROOT_TAG ).createObject().ofType( BeanPropertyTopologyBuilder.class );
    forPattern( ROOT_TAG + "/" + NAME_TAG ).callMethod("name").usingElementBodyAsArgument();
    forPattern( ROOT_TAG + "/" + VERSION_TAG ).callMethod("version").usingElementBodyAsArgument();

    forPattern( ROOT_TAG + "/" + APPLICATION_TAG ).createObject().ofType( Application.class ).then().setNext( "addApplication" );
    forPattern( ROOT_TAG + "/" + APPLICATION_TAG + "/" + ROLE_TAG ).setBeanProperty();
    forPattern( ROOT_TAG + "/" + APPLICATION_TAG + "/" + NAME_TAG ).setBeanProperty();
    forPattern( ROOT_TAG + "/" + APPLICATION_TAG + "/" + VERSION_TAG ).createObject().ofType(Version.class).then().setBeanProperty().then().setNext("setVersion");
    forPattern( ROOT_TAG + "/" + APPLICATION_TAG + "/" + URL_TAG ).callMethod( "addUrl" ).usingElementBodyAsArgument();
    forPattern( ROOT_TAG + "/" + APPLICATION_TAG + "/" + PARAM_TAG ).createObject().ofType( Param.class ).then().addRule( paramRule ).then().setNext( "addParam" );
    forPattern( ROOT_TAG + "/" + APPLICATION_TAG + "/" + PARAM_TAG + "/" + NAME_TAG ).setBeanProperty();
    forPattern( ROOT_TAG + "/" + APPLICATION_TAG + "/" + PARAM_TAG + "/" + VALUE_TAG ).setBeanProperty();

    forPattern( ROOT_TAG + "/" + SERVICE_TAG ).createObject().ofType( Service.class ).then().setNext( "addService" );
    forPattern( ROOT_TAG + "/" + SERVICE_TAG + "/" + ROLE_TAG ).setBeanProperty();
    forPattern( ROOT_TAG + "/" + SERVICE_TAG + "/" + NAME_TAG ).setBeanProperty();
    forPattern( ROOT_TAG + "/" + SERVICE_TAG + "/" + VERSION_TAG ).createObject().ofType(Version.class).then().setBeanProperty().then().setNext("setVersion");
    forPattern( ROOT_TAG + "/" + SERVICE_TAG + "/" + URL_TAG ).callMethod( "addUrl" ).usingElementBodyAsArgument();
    forPattern( ROOT_TAG + "/" + SERVICE_TAG + "/" + PARAM_TAG ).createObject().ofType( Param.class ).then().addRule( paramRule ).then().setNext( "addParam" );
    forPattern( ROOT_TAG + "/" + SERVICE_TAG + "/" + PARAM_TAG + "/" + NAME_TAG ).setBeanProperty();
    forPattern( ROOT_TAG + "/" + SERVICE_TAG + "/" + PARAM_TAG + "/" + VALUE_TAG ).setBeanProperty();

    forPattern( ROOT_TAG + "/" + PROVIDER_TAG ).createObject().ofType( Provider.class ).then().setNext( "addProvider" );
    forPattern( ROOT_TAG + "/" + PROVIDER_TAG + "/" + ROLE_TAG ).setBeanProperty();
    forPattern( ROOT_TAG + "/" + PROVIDER_TAG + "/" + ENABLED_TAG ).setBeanProperty();
    forPattern( ROOT_TAG + "/" + PROVIDER_TAG + "/" + NAME_TAG ).setBeanProperty();
    forPattern( ROOT_TAG + "/" + PROVIDER_TAG + "/" + PARAM_TAG ).createObject().ofType( Param.class ).then().addRule( paramRule ).then().setNext( "addParam" );
    forPattern( ROOT_TAG + "/" + PROVIDER_TAG + "/" + PARAM_TAG + "/" + NAME_TAG ).setBeanProperty();
    forPattern( ROOT_TAG + "/" + PROVIDER_TAG + "/" + PARAM_TAG + "/" + VALUE_TAG ).setBeanProperty();
  }

  private static class ParamRule extends Rule {

    @Override
    public void begin( String namespace, String name, Attributes attributes ) {
      Param param = getDigester().peek();
      String paramName = attributes.getValue( "name" );
      if( paramName != null ) {
        param.setName( paramName );
        param.setValue( attributes.getValue( "value" ) );
      }
    }

  }

}
