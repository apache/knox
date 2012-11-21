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
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

import static org.apache.commons.digester3.binder.DigesterLoader.newLoader;

public class GatewayConfigFactory {

  private static DigesterLoader loader = newLoader( new ClusterConfigRulesModule() );

  public static Config create( URL configUrl, Map<String,String> params ) throws IOException, SAXException {
    Digester digester = loader.newDigester( new ExtendedBaseRules() );
    digester.setValidating( false );

    //digester.setRules( new ExtendedBaseRules() );
    //digester.addObjectCreate( "gateway", Config.class );
    //digester.addRule( "gateway/*", new MapSetDigesterRule( "service" ) );
    //
    //digester.addObjectCreate( "gateway/service", Config.class );
    //digester.addSetNext( "gateway/service", "addChild" );
    //digester.addRule( "gateway/service/*", new MapSetDigesterRule( "filter" ) );
    //
    //digester.addObjectCreate( "gateway/service/filter", Config.class );
    //digester.addSetNext( "gateway/service/filter", "addChild" );
    //digester.addRule( "gateway/service/filter/*", new MapSetDigesterRule() );

    Config config = digester.parse( configUrl );

    if( params != null ) {
      for( Map.Entry<String,String> param : params.entrySet() ) {
        config.put( param.getKey(), param.getValue() );
      }
    }

    return config;
  };

}
