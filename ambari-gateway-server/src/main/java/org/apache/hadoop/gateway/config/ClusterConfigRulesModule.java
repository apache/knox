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
package org.apache.hadoop.gateway.config;

import org.apache.commons.digester3.binder.AbstractRulesModule;

public class ClusterConfigRulesModule extends AbstractRulesModule {

  private static final String ROOT_TAG = "gateway";
  private static final String RESOURCE_TAG = "service";
  private static final String FILTER_TAG = "filter";


  @Override
  protected void configure() {
    forPattern( ROOT_TAG ).createObject().ofType( Config.class );
    forPattern( ROOT_TAG+"/*" ).addRule( new MapSetDigesterRule( RESOURCE_TAG ) );

    forPattern( ROOT_TAG+"/"+RESOURCE_TAG ).createObject().ofType( Config.class );
    forPattern( ROOT_TAG+"/"+RESOURCE_TAG ).setNext( "addChild" );
    forPattern( ROOT_TAG+"/"+RESOURCE_TAG+"/*" ).addRule( new MapSetDigesterRule( FILTER_TAG ) );

    forPattern( ROOT_TAG+"/"+RESOURCE_TAG+"/"+FILTER_TAG ).createObject().ofType( Config.class );
    forPattern( ROOT_TAG+"/"+RESOURCE_TAG+"/"+FILTER_TAG ).setNext( "addChild" );
    forPattern( ROOT_TAG+"/"+RESOURCE_TAG+"/"+FILTER_TAG+"/*" ).addRule( new MapSetDigesterRule() );
  }

}
