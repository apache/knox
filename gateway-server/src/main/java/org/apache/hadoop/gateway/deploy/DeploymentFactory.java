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
package org.apache.hadoop.gateway.deploy;

import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.deploy.impl.DeploymentContextImpl;
import org.apache.hadoop.gateway.deploy.impl.FinalizeDeploymentContributor;
import org.apache.hadoop.gateway.deploy.impl.GatewayDeploymentContributor;
import org.apache.hadoop.gateway.deploy.impl.InitializeDeploymentContributor;
import org.apache.hadoop.gateway.descriptor.GatewayDescriptor;
import org.apache.hadoop.gateway.descriptor.GatewayDescriptorFactory;
import org.apache.hadoop.gateway.topology.Topology;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.webapp30.WebAppDescriptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

public abstract class DeploymentFactory {

  private static Set<DeploymentContributor> CONTRIBUTORS = loadContributors();

  public static WebArchive createDeployment( GatewayConfig config, Topology topology ) {
    WebArchive webArchive = ShrinkWrap.create( WebArchive.class, topology.getName() );
    WebAppDescriptor webAppDesc = Descriptors.create( WebAppDescriptor.class );
    GatewayDescriptor gateway = GatewayDescriptorFactory.create();

    DeploymentContext context
        = new DeploymentContextImpl( config, topology, gateway, webArchive, webAppDesc );

    DeploymentContributor initializeContributor = new InitializeDeploymentContributor();
    DeploymentContributor gatewayContributor = new GatewayDeploymentContributor();
    DeploymentContributor finalizeContributor = new FinalizeDeploymentContributor();

    initializeContributor.contribute( context );
    gatewayContributor.contribute( context );
    invokeLoadedContributors( context );
    finalizeContributor.contribute( context );

    return webArchive;
  }

  private static void invokeLoadedContributors( DeploymentContext context ) {
    for( DeploymentContributor contributor : CONTRIBUTORS ) {
      try {
        contributor.contribute( context );
      } catch( Exception e ) {
        //TODO: I18N message.
        e.printStackTrace();
      }
    }
  }

  private static Set<DeploymentContributor> loadContributors() {
    Set<DeploymentContributor> set = new HashSet<DeploymentContributor>();
    ServiceLoader<DeploymentContributor> loader = ServiceLoader.load( DeploymentContributor.class );
    Iterator<DeploymentContributor> contributors = loader.iterator();
    while( contributors.hasNext() ) {
      DeploymentContributor contributor = contributors.next();
      set.add( contributor );
    }
    return Collections.unmodifiableSet( set );
  }

}
