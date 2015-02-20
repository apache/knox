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

import org.apache.hadoop.gateway.GatewayForwardingServlet;
import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.GatewayServlet;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.descriptor.GatewayDescriptor;
import org.apache.hadoop.gateway.descriptor.GatewayDescriptorFactory;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.registry.ServiceRegistry;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.Service;
import org.apache.hadoop.gateway.topology.Topology;
import org.apache.hadoop.gateway.topology.Version;
import org.apache.hadoop.gateway.util.ServiceDefinitionsLoader;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.webapp30.WebAppDescriptor;
import org.jboss.shrinkwrap.descriptor.api.webcommon30.ServletType;

import java.beans.Statement;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;

public abstract class DeploymentFactory {

  private static final String DEFAULT_APP_REDIRECT_CONTEXT_PATH = "redirectTo";
  private static GatewayMessages log = MessagesFactory.get( GatewayMessages.class );
  private static GatewayServices gatewayServices = null;

  private static Map<String,Map<String,Map<Version, ServiceDeploymentContributor>>> SERVICE_CONTRIBUTOR_MAP;
  static {
    loadServiceContributors();
  }

  private static Set<ProviderDeploymentContributor> PROVIDER_CONTRIBUTORS;
  private static Map<String,Map<String,ProviderDeploymentContributor>> PROVIDER_CONTRIBUTOR_MAP;
  static {
    loadProviderContributors();
  }

  public static void setGatewayServices(GatewayServices services) {
    DeploymentFactory.gatewayServices = services;
  }

  public static WebArchive createDeployment( GatewayConfig config, Topology topology ) {
    DeploymentContext context = null;
     //TODO move the loading of service defs
    String stacks = config.getGatewayStacksDir();
    log.usingStacksDirectory(stacks);
    File stacksDir = new File(stacks);
    Set<ServiceDeploymentContributor> deploymentContributors = ServiceDefinitionsLoader.loadServiceDefinitions(stacksDir);
    addServiceDeploymentContributors(deploymentContributors.iterator());

    Map<String,List<ProviderDeploymentContributor>> providers = selectContextProviders( topology );
    Map<String,List<ServiceDeploymentContributor>> services = selectContextServices( topology );
    context = createDeploymentContext( config, topology.getName(), topology, providers, services );
    initialize( context, providers, services );
    contribute( context, providers, services );
    finalize( context, providers, services );
    if (topology.getName().equals("_default")) {
      // if this is the default topology then add the forwarding webapp as well
      context = deployDefaultTopology(config, topology);
    }
    return context.getWebArchive();
  }

  private static DeploymentContext deployDefaultTopology(GatewayConfig config,
      Topology topology) {
    // this is the "default" topology which does some specialized
    // redirects for compatibility with hadoop cli java client use
    // we do not want the various listeners and providers added or
    // the usual gateway.xml, etc.
    DeploymentContext context;
    Map<String,List<ProviderDeploymentContributor>> providers = new HashMap<String,List<ProviderDeploymentContributor>>();
    Map<String,List<ServiceDeploymentContributor>> services = new HashMap<String,List<ServiceDeploymentContributor>>();
    context = createDeploymentContext( config, "forward", topology, providers, services);
    WebAppDescriptor wad = context.getWebAppDescriptor();
    String servletName = context.getTopology().getName();
    String servletClass = GatewayForwardingServlet.class.getName();
    wad.createServlet().servletName( servletName ).servletClass( servletClass );
    wad.createServletMapping().servletName( servletName ).urlPattern( "/*" );
    ServletType<WebAppDescriptor> servlet = findServlet( context, context.getTopology().getName() );
    servlet.createInitParam()
      .paramName( DEFAULT_APP_REDIRECT_CONTEXT_PATH )
      .paramValue( config.getDefaultAppRedirectPath() );
    writeDeploymentDescriptor(context);
    return context;
  }

  private static DeploymentContext createDeploymentContext(
      GatewayConfig config, String archiveName, Topology topology,
      Map<String,List<ProviderDeploymentContributor>> providers,
      Map<String,List<ServiceDeploymentContributor>> services ) {
    WebArchive webArchive = ShrinkWrap.create( WebArchive.class, archiveName );
    WebAppDescriptor webAppDesc = Descriptors.create( WebAppDescriptor.class );
    GatewayDescriptor gateway = GatewayDescriptorFactory.create();
    DeploymentContext context = new DeploymentContextImpl(
        config, topology, gateway, webArchive, webAppDesc, providers, services );
    return context;
  }

  // Scan through the providers in the topology.  Collect any named providers in their roles list.
  // Scan through all of the loaded providers.  For each that doesn't have an existing provider in the role
  // list add it.
  private static Map<String,List<ProviderDeploymentContributor>> selectContextProviders( Topology topology ) {
    Map<String,List<ProviderDeploymentContributor>> providers = new LinkedHashMap<String, List<ProviderDeploymentContributor>>();
    collectTopologyProviders( topology, providers );
    collectDefaultProviders( providers );
    return providers;
  }

  private static void collectTopologyProviders(
      Topology topology, Map<String, List<ProviderDeploymentContributor>> defaults ) {
    for( Provider provider : topology.getProviders() ) {
      String name = provider.getName();
      if( name != null ) {
        String role = provider.getRole();
        Map<String,ProviderDeploymentContributor> nameMap = PROVIDER_CONTRIBUTOR_MAP.get( role );
        if( nameMap != null ) {
          ProviderDeploymentContributor contributor = nameMap.get( name );
          // If there isn't a contributor with this role/name try to find a "*" contributor.
          if( contributor == null ) {
            nameMap = PROVIDER_CONTRIBUTOR_MAP.get( "*" );
            if( nameMap != null ) {
              contributor = nameMap.get( name );
            }
          }
          if( contributor != null ) {
            List list = defaults.get( role );
            if( list == null ) {
              list = new ArrayList( 1 );
              defaults.put( role, list );
            }
            if( !list.contains( contributor ) ) {
              list.add( contributor );
            }
          }
        }
      }
    }
  }

  private static void collectDefaultProviders( Map<String,List<ProviderDeploymentContributor>> defaults ) {
    for( ProviderDeploymentContributor contributor : PROVIDER_CONTRIBUTORS ) {
      String role = contributor.getRole();
      List<ProviderDeploymentContributor> list = defaults.get( role );
      if( list == null ) {
        list = new ArrayList<ProviderDeploymentContributor>();
        defaults.put( role, list );
      }
      if( list.isEmpty() ) {
        list.add( contributor );
      }
    }
  }

  // Scan through the services in the topology.
  // For each that we find add it to the list of service roles included in the topology.
  private static Map<String,List<ServiceDeploymentContributor>> selectContextServices( Topology topology ) {
    Map<String,List<ServiceDeploymentContributor>> defaults
        = new HashMap<String,List<ServiceDeploymentContributor>>();
    for( Service service : topology.getServices() ) {
      String role = service.getRole();
      ServiceDeploymentContributor contributor = getServiceContributor( role, service.getName(), service.getVersion() );
      if( contributor != null ) {
        List<ServiceDeploymentContributor> list = defaults.get( role );
        if( list == null ) {
          list = new ArrayList<ServiceDeploymentContributor>( 1 );
          defaults.put( role, list );
        }
        if( !list.contains( contributor ) ) {
          list.add( contributor );
        }
      }
    }
    return defaults;
  }

  private static void initialize(
      DeploymentContext context,
      Map<String,List<ProviderDeploymentContributor>> providers,
      Map<String,List<ServiceDeploymentContributor>> services ) {
    WebAppDescriptor wad = context.getWebAppDescriptor();
    String servletName = context.getTopology().getName();
    String servletClass = GatewayServlet.class.getName();
    wad.createServlet().servletName( servletName ).servletClass( servletClass );
    wad.createServletMapping().servletName( servletName ).urlPattern( "/*" );
    if (gatewayServices != null) {
      gatewayServices.initializeContribution(context);
    } else {
      log.gatewayServicesNotInitialized();
    }
    for( String role : providers.keySet() ) {
      for( ProviderDeploymentContributor contributor : providers.get( role ) ) {
        try {
          injectServices(contributor);
          contributor.initializeContribution( context );
        } catch( Exception e ) {
          log.failedToInitializeContribution( e );
          throw new DeploymentException("Failed to initialize contribution.", e);
        }
      }
    }
    for( String role : services.keySet() ) {
      for( ServiceDeploymentContributor contributor : services.get( role ) ) {
        try {
          injectServices(contributor);
          contributor.initializeContribution( context );
        } catch( Exception e ) {
          log.failedToInitializeContribution( e );
          throw new DeploymentException("Failed to initialize contribution.", e);
        }
      }
    }
  }

  private static void injectServices(Object contributor) {
    if (gatewayServices != null) {
      Statement stmt = null;
      for(String serviceName : gatewayServices.getServiceNames()) {

        try {
          // TODO: this is just a temporary injection solution
          // TODO: test for the existence of the setter before attempting it
          // TODO: avoid exception throwing when there is no setter
          stmt = new Statement(contributor, "set" + serviceName, new Object[]{gatewayServices.getService(serviceName)});
          stmt.execute();
        } catch (NoSuchMethodException e) {
          // TODO: eliminate the possibility of this being thrown up front
        } catch (Exception e) {
          // Maybe it makes sense to throw exception
          log.failedToInjectService( serviceName, e );
          throw new DeploymentException("Failed to inject service.", e);
        }
      }
    }
  }

  private static void contribute(
      DeploymentContext context,
      Map<String,List<ProviderDeploymentContributor>> providers,
      Map<String,List<ServiceDeploymentContributor>> services ) {
      Topology topology = context.getTopology();
    for( Provider provider : topology.getProviders() ) {
      ProviderDeploymentContributor contributor = getProviderContributor( providers, provider.getRole(), provider.getName() );
      if( contributor != null && provider.isEnabled() ) {
        try {
          contributor.contributeProvider( context, provider );
        } catch( Exception e ) {
          // Maybe it makes sense to throw exception
          log.failedToContributeProvider( provider.getName(), provider.getRole(), e );
          throw new DeploymentException("Failed to contribute provider.", e);
        }
      }
    }
    for( Service service : topology.getServices() ) {
      ServiceDeploymentContributor contributor = getServiceContributor( service.getRole(), service.getName(), service.getVersion() );
      if( contributor != null ) {
        try {
          contributor.contributeService( context, service );
          if (gatewayServices != null) {
            ServiceRegistry sr = gatewayServices.getService(GatewayServices.SERVICE_REGISTRY_SERVICE);
            if (sr != null) {
              String regCode = sr.getRegistrationCode(topology.getName());
              sr.registerService(regCode, topology.getName(), service.getRole(), service.getUrls() );
            }
          }
        } catch( Exception e ) {
          // Maybe it makes sense to throw exception
          log.failedToContributeService( service.getName(), service.getRole(), e );
          throw new DeploymentException("Failed to contribute service.", e);
        }
      }
    }
  }

  public static ProviderDeploymentContributor getProviderContributor( String role, String name ) {
    ProviderDeploymentContributor contributor = null;
    Map<String,ProviderDeploymentContributor> nameMap = PROVIDER_CONTRIBUTOR_MAP.get( role );
    if( nameMap != null ) {
      if( name != null ) {
        contributor = nameMap.get( name );
      } else if ( !nameMap.isEmpty() ) {
        contributor = nameMap.values().iterator().next();
      }
    }
    return contributor;
  }

  public static ServiceDeploymentContributor getServiceContributor( String role, String name, Version version ) {
    ServiceDeploymentContributor contributor = null;
    Map<String,Map<Version, ServiceDeploymentContributor>> nameMap = SERVICE_CONTRIBUTOR_MAP.get( role );
    if( nameMap != null && !nameMap.isEmpty()) {
      Map<Version, ServiceDeploymentContributor> versionMap = null;
      if ( name == null ) {
        versionMap = nameMap.values().iterator().next();
      } else {
        versionMap = nameMap.get( name );
      }
      if ( versionMap != null && !versionMap.isEmpty()) {
        if( version == null ) {
          contributor = ((TreeMap<Version, ServiceDeploymentContributor>) versionMap).firstEntry().getValue();
        } else {
          contributor = versionMap.get( version );
        }
      }
    }
    return contributor;
  }

  private static void finalize(
      DeploymentContext context,
      Map<String,List<ProviderDeploymentContributor>> providers,
      Map<String,List<ServiceDeploymentContributor>> services ) {
    try {
      // Write the gateway descriptor (gateway.xml) into the war.
      StringWriter writer = new StringWriter();
      GatewayDescriptorFactory.store( context.getGatewayDescriptor(), "xml", writer );
      context.getWebArchive().addAsWebInfResource(
          new StringAsset( writer.toString() ),
          GatewayServlet.GATEWAY_DESCRIPTOR_LOCATION_DEFAULT );

      // Set the location of the gateway descriptor as a servlet init param.
      ServletType<WebAppDescriptor> servlet = findServlet( context, context.getTopology().getName() );
      servlet.createInitParam()
          .paramName( GatewayServlet.GATEWAY_DESCRIPTOR_LOCATION_PARAM )
          .paramValue( GatewayServlet.GATEWAY_DESCRIPTOR_LOCATION_DEFAULT );

      if (gatewayServices != null) {
        gatewayServices.finalizeContribution(context);
      }
      for( String role : providers.keySet() ) {
        for( ProviderDeploymentContributor contributor : providers.get( role ) ) {
          try {
            contributor.finalizeContribution( context );
          } catch( Exception e ) {
            // Maybe it makes sense to throw exception
            log.failedToFinalizeContribution( e );
            throw new DeploymentException("Failed to finalize contribution.", e);
          }
        }
      }
      for( String role : services.keySet() ) {
        for( ServiceDeploymentContributor contributor : services.get( role ) ) {
          try {
            contributor.finalizeContribution( context );
          } catch( Exception e ) {
            // Maybe it makes sense to throw exception
            log.failedToFinalizeContribution( e );
            throw new DeploymentException("Failed to finalize contribution.", e);
          }
        }
      }

      writeDeploymentDescriptor(context);

    } catch ( IOException e ) {
      throw new RuntimeException( e );
    }
  }

  private static void writeDeploymentDescriptor(DeploymentContext context) {
    // Write the web.xml into the war.
    Asset webXmlAsset = new StringAsset( context.getWebAppDescriptor().exportAsString() );
    context.getWebArchive().setWebXML( webXmlAsset );
  }

  public static ServletType<WebAppDescriptor> findServlet( DeploymentContext context, String name ) {
    List<ServletType<WebAppDescriptor>> servlets = context.getWebAppDescriptor().getAllServlet();
    for( ServletType<WebAppDescriptor> servlet : servlets ) {
      if( name.equals( servlet.getServletName() ) ) {
        return servlet;
      }
    }
    return null;
  }

  private static void loadServiceContributors() {
    SERVICE_CONTRIBUTOR_MAP = new HashMap<String, Map<String, Map<Version, ServiceDeploymentContributor>>>();
    ServiceLoader<ServiceDeploymentContributor> loader = ServiceLoader.load( ServiceDeploymentContributor.class );
    Iterator<ServiceDeploymentContributor> contributors = loader.iterator();
    addServiceDeploymentContributors(contributors);
  }

   private static void addServiceDeploymentContributors(Iterator<ServiceDeploymentContributor> contributors) {
      while( contributors.hasNext() ) {
        ServiceDeploymentContributor contributor = contributors.next();
        if( contributor.getName() == null ) {
          log.ignoringServiceContributorWithMissingName( contributor.getClass().getName() );
          continue;
        }
        if( contributor.getRole() == null ) {
          log.ignoringServiceContributorWithMissingRole( contributor.getClass().getName() );
          continue;
        }
        if( contributor.getVersion() == null ) {
          log.ignoringServiceContributorWithMissingVersion(contributor.getClass().getName());
          continue;
        }
        Map<String,Map<Version, ServiceDeploymentContributor>> nameMap = SERVICE_CONTRIBUTOR_MAP.get( contributor.getRole() );
        if( nameMap == null ) {
          nameMap = new HashMap<String,Map<Version, ServiceDeploymentContributor>>();
          SERVICE_CONTRIBUTOR_MAP.put( contributor.getRole(), nameMap );
        }
        Map<Version, ServiceDeploymentContributor> versionMap = nameMap.get(contributor.getName());
        if (versionMap == null) {
          versionMap = new TreeMap<Version, ServiceDeploymentContributor>();
          nameMap.put(contributor.getName(), versionMap);
        }
        versionMap.put( contributor.getVersion(), contributor );
      }
   }

   private static void loadProviderContributors() {
    Set<ProviderDeploymentContributor> set = new HashSet<ProviderDeploymentContributor>();
    Map<String,Map<String,ProviderDeploymentContributor>> roleMap
        = new HashMap<String,Map<String,ProviderDeploymentContributor>>();

    ServiceLoader<ProviderDeploymentContributor> loader = ServiceLoader.load( ProviderDeploymentContributor.class );
    Iterator<ProviderDeploymentContributor> contributors = loader.iterator();
    while( contributors.hasNext() ) {
      ProviderDeploymentContributor contributor = contributors.next();
      if( contributor.getName() == null ) {
        log.ignoringProviderContributorWithMissingName( contributor.getClass().getName() );
        continue;
      }
      if( contributor.getRole() == null ) {
        log.ignoringProviderContributorWithMissingRole( contributor.getClass().getName() );
        continue;
      }
      set.add( contributor );
      Map nameMap = roleMap.get( contributor.getRole() );
      if( nameMap == null ) {
        nameMap = new HashMap<String,ProviderDeploymentContributor>();
        roleMap.put( contributor.getRole(), nameMap );
      }
      nameMap.put( contributor.getName(), contributor );
    }
    PROVIDER_CONTRIBUTORS = set;
    PROVIDER_CONTRIBUTOR_MAP = roleMap;
  }

  static ProviderDeploymentContributor getProviderContributor(
      Map<String,List<ProviderDeploymentContributor>> providers, String role, String name ) {
    ProviderDeploymentContributor contributor = null;
    if( name == null ) {
      List<ProviderDeploymentContributor> list = providers.get( role );
      if( list != null && !list.isEmpty() ) {
        contributor = list.get( 0 );
      }
    } else {
      contributor = getProviderContributor( role, name );
    }
    return contributor;
  }
}
