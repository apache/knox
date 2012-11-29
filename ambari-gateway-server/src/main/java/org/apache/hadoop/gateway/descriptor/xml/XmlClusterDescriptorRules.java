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
package org.apache.hadoop.gateway.descriptor.xml;

import org.apache.commons.digester3.FactoryCreateRule;
import org.apache.commons.digester3.binder.AbstractRulesModule;
import org.apache.hadoop.gateway.descriptor.impl.ClusterResourceImpl;

public class XmlClusterDescriptorRules extends AbstractRulesModule implements XmlClusterDescriptorTags {

  @Override
  protected void configure() {
    forPattern( CLUSTER ).addRule( new FactoryCreateRule( XmlClusterDescriptorFactory.class ) );
    forPattern( CLUSTER ).createObject().ofType( ClusterResourceImpl.class );
    forPattern( CLUSTER + "/" + RESOURCE ).setNext( "addResource" );
    forPattern( CLUSTER + "/" + RESOURCE + "/" + RESOURCE_SOURCE ).callMethod( "source" );
    forPattern( CLUSTER+"/"+RESOURCE+"/"+RESOURCE_TARGET ).callMethod( "target" );
    forPattern( CLUSTER + "/" + RESOURCE + "/" + FILTER ).setNext( "addFilter" );
    forPattern( CLUSTER + "/" + RESOURCE + "/" + FILTER + "/" + FILTER_ROLE ).callMethod( "role" );
    forPattern( CLUSTER + "/" + RESOURCE + "/" + FILTER + "/" + FILTER_IMPL ).callMethod( "impl" );
    forPattern( CLUSTER + "/" + RESOURCE + "/" + FILTER + "/" + FILTER_PARAM ).setNext( "addParam" );
    forPattern( CLUSTER+"/"+RESOURCE+"/"+FILTER+"/"+FILTER_PARAM+"/"+FILTER_PARAM_NAME ).callMethod( "name" );
    forPattern( CLUSTER+"/"+RESOURCE+"/"+FILTER+"/"+FILTER_PARAM+"/"+FILTER_PARAM_VALUE ).callMethod( "value" );
  }

}
