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

import org.apache.hadoop.gateway.filter.UrlRewriteFilter;
import org.apache.hadoop.gateway.pivot.HttpClientPivot;
import org.apache.hadoop.gateway.pivot.WebHdfsPivot;
import org.apache.hadoop.gateway.topology.ClusterComponent;
import org.apache.shiro.web.servlet.ShiroFilter;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HdfsConfigFactory implements ResourceConfigFactory {

  private static Set<String> ROLES = createSupportedRoles();

  private static Set<String> createSupportedRoles() {
    Set<String> roles = new HashSet<String>();
    roles.add( "NAMENODE" );
    roles.add( "DATANODE" );
    return Collections.unmodifiableSet( roles );
  }

  @Override
  public Set<String> getSupportedRoles() {
    return ROLES;
  }

  @Override
  public Collection<Config> createResourceConfig( Config clusterConfig, ClusterComponent clusterComponent ) {
    List<Config> configs = new ArrayList<Config>();

    String extClusterUrl = "{request.scheme}://{request.host}:{request.port}/{gateway.path}/{cluster.path}";
    String extHdfsPath = "/namenode/api/v1";
    String intHdfsUrl = clusterComponent.getUrl().toExternalForm();

    Config authc;
    Config rewrite;
    Config pivot;

    Config root = new Config();
    root.put( "name", "namenode-root" );
    root.put( "source", extHdfsPath +  "?{**}" );
    root.put( "target", intHdfsUrl + "?{**}" );
    authc = new Config();
    authc.put( "name", "shiro" );
    authc.put( "class", ShiroFilter.class.getName() );
    root.addChild( authc );
    pivot = new Config();
    pivot.put( "name", "pivot" );
    pivot.put( "class", HttpClientPivot.class.getName() );
    root.addChild( pivot );

    configs.add( root );

    Config file = new Config();
    file.put( "name", "namenode-file" );
    file.put( "source", extHdfsPath + "/{path=**}?{**}" );
    file.put( "target", intHdfsUrl + "/{path=**}?{**}" );
    authc = new Config();
    authc.put( "name", "shiro" );
    authc.put( "class", ShiroFilter.class.getName() );
    file.addChild( authc );
    rewrite = new Config();
    rewrite.put( "name", "rewrite" );
    rewrite.put( "class", UrlRewriteFilter.class.getName() );
    rewrite.put( "rewrite", "webhdfs://*:*/{path=**}" + " " + extClusterUrl + "/" + extHdfsPath + "/{path=**}" );
    file.addChild( rewrite );
    pivot = new Config();
    pivot.put( "name", "pivot" );
    pivot.put( "class", WebHdfsPivot.class.getName() );
    file.addChild( pivot );
    configs.add( file );

    return configs;
  }

}
