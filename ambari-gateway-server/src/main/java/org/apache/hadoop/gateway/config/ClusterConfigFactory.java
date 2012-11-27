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

import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.ExtendedBaseRules;
import org.apache.commons.digester3.binder.DigesterLoader;
import org.apache.hadoop.gateway.topology.ClusterComponent;
import org.apache.hadoop.gateway.topology.ClusterTopology;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Map;

import static org.apache.commons.digester3.binder.DigesterLoader.newLoader;

public class ClusterConfigFactory {

  private static DigesterLoader loader = newLoader( new ClusterConfigRulesModule() );

  public static Config create( URL configUrl, Map<String,String> params ) throws IOException, SAXException {
    Digester digester = loader.newDigester( new ExtendedBaseRules() );
    digester.setValidating( false );
    Config config = digester.parse( configUrl );
    if( params != null ) {
      for( Map.Entry<String,String> param : params.entrySet() ) {
        config.put( param.getKey(), param.getValue() );
      }
    }
    return config;
  }

  public static Config create( GatewayConfig gatewayConfig, ClusterTopology clusterTopology ) {
    Config clusterConfig = new Config();
    // Copy the config values into the root config.
    if( gatewayConfig != null ) {
      Map<String,String> params = gatewayConfig.getValByRegex( ".*" );
      for( Map.Entry<String,String> param : params.entrySet() ) {
        clusterConfig.put( param.getKey(), param.getValue() );
      }
    }
    if( clusterTopology != null ) {
      clusterConfig.put( "name", clusterTopology.getName() );
      for( ClusterComponent clusterComponent : clusterTopology.getComponents() ) {
        Collection<Config> componentConfigs = createResources( clusterConfig, clusterComponent );
        for( Config componentConfig : componentConfigs ) {
          clusterConfig.addChild( componentConfig );
        }
      }
    }
    return clusterConfig;
  }

  private static Collection<Config> createResources( Config clusterConfig, ClusterComponent clusterComponent ) {
    ResourceConfigFactory factory = ResourceConfigFactoryProvider.getResourceConfigFactory( clusterComponent );
    Collection<Config> configs = factory.createResourceConfig( clusterConfig, clusterComponent );
    return configs;
  }

}
