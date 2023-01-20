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
package org.apache.knox.gateway.topology.xml;

import org.apache.commons.digester3.Rule;
import org.apache.commons.digester3.binder.AbstractRulesModule;
import org.apache.knox.gateway.service.definition.CustomDispatch;
import org.apache.knox.gateway.service.definition.DispatchParam;
import org.apache.knox.gateway.topology.Application;
import org.apache.knox.gateway.topology.Param;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Version;
import org.apache.knox.gateway.topology.builder.BeanPropertyTopologyBuilder;
import org.xml.sax.Attributes;

public class KnoxFormatXmlTopologyRules extends AbstractRulesModule {

  private static final String ROOT_TAG = "topology";
  private static final String NAME_TAG = "name";
  private static final String VERSION_TAG = "version";
  private static final String DEFAULT_SERVICE_TAG = "path";
  private static final String GENERATED_TAG = "generated";
  private static final String REDEPLOY_TIME_TAG = "redeployTime";
  private static final String APPLICATION_TAG = "application";
  private static final String SERVICE_TAG = "service";
  private static final String ROLE_TAG = "role";
  private static final String URL_TAG = "url";
  private static final String PROVIDER_TAG = "gateway/provider";
  private static final String ENABLED_TAG = "enabled";
  private static final String PARAM_TAG = "param";
  private static final String VALUE_TAG = "value";

  /* topology dispatch tags */
  private static final String DISPATCH_TAG = "dispatch";
  private static final String CONTRIBUTOR_NAME = "contributor-name";
  private static final String HA_CONTRIBUTOR_NAME = "ha-contributor-name";
  private static final String CLASSNAME = "classname";
  private static final String HA_CLASSNAME = "ha-classname";
  private static final String HTTP_CLIENT_FACTORY = "http-client-factory";
  private static final String USE_TWO_WAY_SSL = "use-two-way-ssl";

  private static final Rule paramRule = new ParamRule();
  private static final Rule dispatchParamRule = new DispatchParamRule();

  @Override
  protected void configure() {
    forPattern( ROOT_TAG ).createObject().ofType( BeanPropertyTopologyBuilder.class );
    forPattern( ROOT_TAG + "/" + NAME_TAG ).callMethod("name").usingElementBodyAsArgument();
    forPattern( ROOT_TAG + "/" + VERSION_TAG ).callMethod("version").usingElementBodyAsArgument();
    forPattern( ROOT_TAG + "/" + DEFAULT_SERVICE_TAG ).callMethod("defaultService").usingElementBodyAsArgument();
    forPattern( ROOT_TAG + "/" + GENERATED_TAG ).callMethod("generated").usingElementBodyAsArgument();
    forPattern( ROOT_TAG + "/" + REDEPLOY_TIME_TAG ).callMethod("redeployTime").usingElementBodyAsArgument();

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
    /* topology service dispatch */
    forPattern( ROOT_TAG + "/" + SERVICE_TAG + "/" + DISPATCH_TAG).createObject().ofType( CustomDispatch.class ).then().setNext( "addDispatch" );
    forPattern( ROOT_TAG + "/" + SERVICE_TAG + "/" + DISPATCH_TAG + "/" + CONTRIBUTOR_NAME ).callMethod( "setContributorName" ).usingElementBodyAsArgument();
    forPattern( ROOT_TAG + "/" + SERVICE_TAG + "/" + DISPATCH_TAG + "/" + HA_CONTRIBUTOR_NAME ).callMethod( "setHaContributorName" ).usingElementBodyAsArgument();
    forPattern( ROOT_TAG + "/" + SERVICE_TAG + "/" + DISPATCH_TAG + "/" + CLASSNAME ).callMethod( "setClassName" ).usingElementBodyAsArgument();
    forPattern( ROOT_TAG + "/" + SERVICE_TAG + "/" + DISPATCH_TAG + "/" + HA_CLASSNAME ).callMethod( "setHaClassName" ).usingElementBodyAsArgument();
    forPattern( ROOT_TAG + "/" + SERVICE_TAG + "/" + DISPATCH_TAG + "/" + HTTP_CLIENT_FACTORY ).callMethod( "setHttpClientFactory" ).usingElementBodyAsArgument();
    forPattern( ROOT_TAG + "/" + SERVICE_TAG + "/" + DISPATCH_TAG + "/" + USE_TWO_WAY_SSL ).callMethod( "setUseTwoWaySsl" ).usingElementBodyAsArgument();
    forPattern( ROOT_TAG + "/" + SERVICE_TAG + "/" + DISPATCH_TAG + "/" + PARAM_TAG ).createObject().ofType( DispatchParam.class ).then().addRule( dispatchParamRule ).then().setNext( "addParam" );
    forPattern( ROOT_TAG + "/" + SERVICE_TAG + "/" + DISPATCH_TAG + "/" + PARAM_TAG + "/" + NAME_TAG ).setBeanProperty();
    forPattern( ROOT_TAG + "/" + SERVICE_TAG + "/" + DISPATCH_TAG + "/" + PARAM_TAG + "/" + VALUE_TAG ).setBeanProperty();

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

  private static class DispatchParamRule extends Rule {
    @Override
    public void begin( String namespace, String name, Attributes attributes ) {
      DispatchParam param = getDigester().peek();
      String paramName = attributes.getValue( "name" );
      if( paramName != null ) {
        param.setName( paramName );
        param.setValue( attributes.getValue( "value" ) );
      }
    }
  }
}
