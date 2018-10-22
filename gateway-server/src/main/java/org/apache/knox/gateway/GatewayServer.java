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
package org.apache.knox.gateway;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.deploy.DeploymentException;
import org.apache.knox.gateway.deploy.DeploymentFactory;
import org.apache.knox.gateway.filter.CorrelationHandler;
import org.apache.knox.gateway.filter.PortMappingHelperHandler;
import org.apache.knox.gateway.filter.RequestUpdateHandler;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.registry.ServiceRegistry;
import org.apache.knox.gateway.services.security.SSLService;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Application;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.topology.TopologyEvent;
import org.apache.knox.gateway.topology.TopologyListener;
import org.apache.knox.gateway.trace.AccessHandler;
import org.apache.knox.gateway.trace.KnoxErrorHandler;
import org.apache.knox.gateway.trace.TraceHandler;
import org.apache.knox.gateway.util.Urls;
import org.apache.knox.gateway.util.XmlUtils;
import org.apache.knox.gateway.websockets.GatewayWebsocketHandler;
import org.apache.log4j.PropertyConfigurator;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.servlets.gzip.GzipHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jboss.shrinkwrap.api.exporter.ExplodedExporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class GatewayServer {
  private static final GatewayResources res = ResourcesFactory.get(GatewayResources.class);
  private static final GatewayMessages log = MessagesFactory.get(GatewayMessages.class);
  private static final Auditor auditor = AuditServiceFactory.getAuditService().getAuditor(AuditConstants.DEFAULT_AUDITOR_NAME,
      AuditConstants.KNOX_SERVICE_NAME, AuditConstants.KNOX_COMPONENT_NAME);

  private static GatewayServer server;
  private static GatewayServices services;

  private static Properties buildProperties;

  private Server jetty;
  private GatewayConfig config;
  private ContextHandlerCollection contexts;
  private TopologyService monitor;
  private TopologyListener listener;
  private Map<String, WebAppContext> deployments;

  public static void main( String[] args ) {
    try {
      configureLogging();
      logSysProps();
      CommandLine cmd = GatewayCommandLine.parse( args );
      if( cmd.hasOption( GatewayCommandLine.HELP_LONG ) ) {
        GatewayCommandLine.printHelp();
      } else if( cmd.hasOption( GatewayCommandLine.VERSION_LONG ) ) {
        printVersion();
      } else if( cmd.hasOption( GatewayCommandLine.REDEPLOY_LONG  ) ) {
        redeployTopologies( cmd.getOptionValue( GatewayCommandLine.REDEPLOY_LONG ) );
      } else {
        buildProperties = loadBuildProperties();
        services = instantiateGatewayServices();
        if (services == null) {
          log.failedToInstantiateGatewayServices();
        }
        GatewayConfig config = new GatewayConfigImpl();
        if (config.isHadoopKerberosSecured()) {
          configureKerberosSecurity( config );
        }
        Map<String,String> options = new HashMap<>();
        options.put(GatewayCommandLine.PERSIST_LONG, Boolean.toString(cmd.hasOption(GatewayCommandLine.PERSIST_LONG)));
        services.init(config, options);
        if (!cmd.hasOption(GatewayCommandLine.NOSTART_LONG)) {
          startGateway( config, services );
        }
      }
    } catch ( ParseException e ) {
      log.failedToParseCommandLine( e );
      GatewayCommandLine.printHelp();
    } catch ( Exception e ) {
      log.failedToStartGateway( e );
      // Make sure the process exits.
      System.exit(1);
    }
  }

  private static void printVersion() {
    System.out.println( res.gatewayVersionMessage( // I18N not required.
        getBuildVersion(),
        getBuildHash() ) );
  }

  public static String getBuildHash() {
    String hash = "unknown";
    if( buildProperties != null ) {
      hash = buildProperties.getProperty( "build.hash", hash );
    }
    return hash;
  }

  public static String getBuildVersion() {
    String version = "unknown";
    if( buildProperties != null ) {
      version = buildProperties.getProperty( "build.version", version );
    }
    return version;
  }

  private static GatewayServices instantiateGatewayServices() {
    ServiceLoader<GatewayServices> loader = ServiceLoader.load( GatewayServices.class );
    Iterator<GatewayServices> services = loader.iterator();
    if (services.hasNext()) {
      return services.next();
    }
    return null;
  }

  public static synchronized GatewayServices getGatewayServices() {
    return services;
  }

  private static void logSysProp( String name ) {
    log.logSysProp( name, System.getProperty( name ) );
  }

  private static void logSysProps() {
    logSysProp( "user.name" );
    logSysProp( "user.dir" );
    logSysProp( "java.runtime.name" );
    logSysProp( "java.runtime.version" );
    logSysProp( "java.home" );
  }

  private static void configureLogging() {
    PropertyConfigurator.configure( System.getProperty( "log4j.configuration" ) );
//    String fileName = config.getGatewayConfDir() + File.separator + "log4j.properties";
//    File file = new File( fileName );
//    if( file.isFile() && file.canRead() ) {
//      FileInputStream stream;
//      try {
//        stream = new FileInputStream( file );
//        Properties properties = new Properties();
//        properties.load( stream );
//        PropertyConfigurator.configure( properties );
//        log.loadedLoggingConfig( fileName );
//      } catch( IOException e ) {
//        log.failedToLoadLoggingConfig( fileName );
//      }
//    }
  }

  private static void configureKerberosSecurity( GatewayConfig config ) {
    setSystemProperty(GatewayConfig.HADOOP_KERBEROS_SECURED, "true");
    setSystemProperty(GatewayConfig.KRB5_CONFIG, config.getKerberosConfig());
    setSystemProperty(GatewayConfig.KRB5_DEBUG, Boolean.toString(config.isKerberosDebugEnabled()));
    setSystemProperty(GatewayConfig.KRB5_LOGIN_CONFIG, config.getKerberosLoginConfig());
    setSystemProperty(GatewayConfig.KRB5_USE_SUBJECT_CREDS_ONLY,  "false");
  }

  private static void setSystemProperty(String name, String value) {
    System.setProperty(name, value);
    log.logSysProp(name, System.getProperty(name));
  }

  private static Properties loadBuildProperties() {
    Properties properties = new Properties();
    InputStream inputStream = GatewayServer.class.getClassLoader().getResourceAsStream( "build.properties" );
    if( inputStream != null ) {
      try {
        properties.load( inputStream );
        inputStream.close();
      } catch( IOException e ) {
        // Ignore.
      }
    }
    return properties;
  }

  public static void redeployTopologies( String topologyName  ) {
    TopologyService ts = getGatewayServices().getService(GatewayServices.TOPOLOGY_SERVICE);
    ts.reloadTopologies();
    ts.redeployTopologies(topologyName);
  }

  private void cleanupTopologyDeployments() {
    File deployDir = new File( config.getGatewayDeploymentDir() );
    TopologyService ts = getGatewayServices().getService(GatewayServices.TOPOLOGY_SERVICE);
    for( Topology topology : ts.getTopologies() ) {
      cleanupTopologyDeployments( deployDir, topology );
    }
  }

  private void cleanupTopologyDeployments( File deployDir, Topology topology ) {
    log.cleanupDeployments( topology.getName() );
    File[] files = deployDir.listFiles( new RegexFilenameFilter( topology.getName() + "\\.(war|topo)\\.[0-9A-Fa-f]+" ) );
    if( files != null ) {
      Arrays.sort( files, new FileModificationTimeDescendingComparator() );
      int verLimit = config.getGatewayDeploymentsBackupVersionLimit();
      long ageLimit = config.getGatewayDeploymentsBackupAgeLimit();
      long keepTime = System.currentTimeMillis() - ageLimit;
      for( int i=1; i<files.length; i++ ) {
        File file = files[i];
        if( ( ( verLimit >= 0 ) && ( i > verLimit ) ) ||
            ( ( ageLimit >= 0 ) && ( file.lastModified() < keepTime ) ) ) {
          log.cleanupDeployment( file.getAbsolutePath() );
          FileUtils.deleteQuietly( file );
        }
      }
    }
  }

  public static GatewayServer startGateway( GatewayConfig config, GatewayServices svcs ) throws Exception {
    log.startingGateway();
    server = new GatewayServer( config );
    synchronized ( server ) {
      //KM[ Commented this out because is causes problems with
      // multiple services instance used in a single test process.
      // I'm not sure what drive including this check though.
      //if (services == null) {
      services = svcs;
      //}
      //KM]
      services.start();
      DeploymentFactory.setGatewayServices(services);
      server.start();
      // Coverity CID 1352654
      URI uri = server.jetty.getURI();

      // Logging for topology <-> port
      InetSocketAddress[] addresses = new InetSocketAddress[server.jetty
          .getConnectors().length];
      for (int i = 0, n = addresses.length; i < n; i++) {
        NetworkConnector connector = (NetworkConnector) server.jetty
            .getConnectors()[i];
        if (connector != null) {
          for(ConnectionFactory x  : connector.getConnectionFactories()) {
            if(x instanceof HttpConnectionFactory) {
                ((HttpConnectionFactory)x).getHttpConfiguration().setSendServerVersion(config.isGatewayServerHeaderEnabled());
            }
          }
          if (connector.getName() == null) {
            log.startedGateway(connector.getLocalPort());
          } else {
            log.startedGateway(connector.getName(), connector.getLocalPort());
          }
        }
      }

      return server;
    }
  }

  public GatewayServer( GatewayConfig config ) {
    this(config, null);
  }

  public GatewayServer( GatewayConfig config, Properties options ) {
      this.config = config;
      this.listener = new InternalTopologyListener();
  }

  /**
   * Create a connector for Gateway Server to listen on.
   *
   * @param server       Jetty server
   * @param config       GatewayConfig
   * @param port         If value is > 0 then the given value is used else we
   *                     use the port provided in GatewayConfig.
   * @param topologyName Connector name, only used when not null
   * @return
   * @throws IOException
   * @throws CertificateException
   * @throws NoSuchAlgorithmException
   * @throws KeyStoreException
   */
  private Connector createConnector(final Server server,
      final GatewayConfig config, final int port, final String topologyName)
      throws IOException, CertificateException, NoSuchAlgorithmException,
      KeyStoreException {

    ServerConnector connector;

    // Determine the socket address and check availability.
    InetSocketAddress address = config.getGatewayAddress();
    checkAddressAvailability( address );

    final int connectorPort = port > 0 ? port : address.getPort();

    checkPortConflict(connectorPort, topologyName, config);

    HttpConfiguration httpConfig = new HttpConfiguration();
    httpConfig.setRequestHeaderSize( config.getHttpServerRequestHeaderBuffer() );
    //httpConfig.setRequestBufferSize( config.getHttpServerRequestBuffer() );
    httpConfig.setResponseHeaderSize( config.getHttpServerResponseHeaderBuffer() );
    httpConfig.setOutputBufferSize( config.getHttpServerResponseBuffer() );

    if (config.isSSLEnabled()) {
      HttpConfiguration httpsConfig = new HttpConfiguration( httpConfig );
      httpsConfig.setSecureScheme( "https" );
      httpsConfig.setSecurePort( connectorPort );
      httpsConfig.addCustomizer( new SecureRequestCustomizer() );
      SSLService ssl = services.getService("SSLService");
      String keystoreFileName = config.getGatewaySecurityDir() + File.separatorChar + "keystores" + File.separatorChar + "gateway.jks";
      SslContextFactory sslContextFactory = (SslContextFactory)ssl.buildSslContextFactory( keystoreFileName );
      connector = new ServerConnector( server, sslContextFactory, new HttpConnectionFactory( httpsConfig ) );
    } else {
      connector = new ServerConnector( server );
    }
    connector.setHost( address.getHostName() );
    connector.setPort( connectorPort );

    if(!StringUtils.isBlank(topologyName)) {
      connector.setName(topologyName);
    }

    long idleTimeout = config.getGatewayIdleTimeout();
    if (idleTimeout > 0l) {
      connector.setIdleTimeout(idleTimeout);
    }

    return connector;
  }

  private static HandlerCollection createHandlers(
      final GatewayConfig config,
      final GatewayServices services,
      final ContextHandlerCollection contexts,
      final Map<String, Integer> topologyPortMap) {
    HandlerCollection handlers = new HandlerCollection();
    RequestLogHandler logHandler = new RequestLogHandler();

    logHandler.setRequestLog( new AccessHandler() );

    TraceHandler traceHandler = new TraceHandler();
    traceHandler.setHandler( contexts );
    traceHandler.setTracedBodyFilter( System.getProperty( "org.apache.knox.gateway.trace.body.status.filter" ) );

    CorrelationHandler correlationHandler = new CorrelationHandler();
    correlationHandler.setHandler( traceHandler );

    /* KNOX-732: Handler for GZip compression */
    GzipHandler gzipHandler = new GzipHandler();
    String[] mimeTypes = {};
    if (config.getMimeTypesToCompress() != null
        && !config.getMimeTypesToCompress().isEmpty()) {
      mimeTypes = config.getMimeTypesToCompress().toArray(new String[0]);
    }
    gzipHandler.addIncludedMimeTypes(mimeTypes);
    gzipHandler.setHandler(correlationHandler);

    // Used to correct the {target} part of request with Topology Port Mapping feature
    final PortMappingHelperHandler portMappingHandler = new PortMappingHelperHandler(config);
    portMappingHandler.setHandler(gzipHandler);

     // If topology to port mapping feature is enabled then we add new Handler {RequestForwardHandler}
     // to the chain, this handler listens on the configured port (in gateway-site.xml)
     // and simply forwards requests to the correct context path.

     //  The reason for adding ContextHandler is so that we can add a connector
     // to it on which the handler listens (exclusively).


    if (config.isGatewayPortMappingEnabled()) {

      for (final Map.Entry<String, Integer> entry : topologyPortMap
          .entrySet()) {
        log.createJettyHandler(entry.getKey());
        final ContextHandler topologyContextHandler = new ContextHandler();

        final RequestUpdateHandler updateHandler = new RequestUpdateHandler(
            config, entry.getKey(), services);

        topologyContextHandler.setHandler(updateHandler);
        topologyContextHandler.setVirtualHosts(
            new String[] { "@" + entry.getKey().toLowerCase(Locale.ROOT) });

        handlers.addHandler(topologyContextHandler);
      }

    }

    handlers.addHandler(logHandler);

    if (config.isWebsocketEnabled()) {
      final GatewayWebsocketHandler websocketHandler = new GatewayWebsocketHandler(
          config, services);
      websocketHandler.setHandler(portMappingHandler);

      handlers.addHandler(websocketHandler);

    } else {
      handlers.addHandler(portMappingHandler);
    }

    return handlers;
  }

  /**
   * Sanity Check to make sure configured ports are free and there is not port
   * conflict.
   *
   * @param port
   * @param topologyName
   * @param config
   * @throws IOException
   */
  public void checkPortConflict(final int port,
      final String topologyName, final GatewayConfig config)
      throws IOException {

    // Throw an exception if port in use
    if (isPortInUse(port)) {
      if (topologyName == null) {
        log.portAlreadyInUse(port);
      } else {
        log.portAlreadyInUse(port, topologyName);
      }
      throw new IOException(String.format(Locale.ROOT, "Port %d already in use.", port));
    }

    // if topology name is blank which means we have all topologies listening on this port
    if (StringUtils.isBlank(topologyName)) {
      // If we have Default Topology old and new configuration (Port Mapping) throw error.
      if (config.getGatewayPortMappings().containsValue(port)
          && !StringUtils.isBlank(config.getDefaultTopologyName())) {
        log.portAlreadyInUse(port);
        throw new IOException(String.format(Locale.ROOT,
            " Please map port %d using either \"gateway.port.mapping.sandbox\" or "
                + "\"default.app.topology.name\" property, "
                + "specifying both is not a valid configuration. ",
            port));
      }
    } else {
      /* check for port conflict */
      final Connector[] connectors = jetty.getConnectors();
      for (int i = 0; i < connectors.length; i++) {
        if (connectors[i] instanceof ServerConnector
            && ((ServerConnector) connectors[i]).getPort() == port) {
          log.portAlreadyInUse(port, topologyName);
          throw new IOException(String.format(Locale.ROOT,
              " Port %d used by topology %s is used by other topology, ports for topologies (if defined) have to be unique. ",
              port, topologyName));
        }
      }

    }

  }

  private synchronized void start() throws Exception {
    // Create the global context handler.
    contexts = new ContextHandlerCollection();

     // A map to keep track of current deployments by cluster name.
    deployments = new ConcurrentHashMap<>();

    // Start Jetty.
    jetty = new Server( new QueuedThreadPool( config.getThreadPoolMax() ) );

    /* topologyName is null because all topology listen on this port */
    jetty.addConnector( createConnector( jetty, config, config.getGatewayPort(), null) );


    // Add Annotations processing into the Jetty server to support JSPs
    Configuration.ClassList classlist = Configuration.ClassList.setServerDefault( jetty );
    classlist.addBefore(
        "org.eclipse.jetty.webapp.JettyWebXmlConfiguration",
        "org.eclipse.jetty.annotations.AnnotationConfiguration" );

    // Load the current topologies.
    // Redeploy autodeploy topologies.
    File topologiesDir = calculateAbsoluteTopologiesDir();
    log.loadingTopologiesFromDirectory(topologiesDir.getAbsolutePath());
    monitor = services.getService(GatewayServices.TOPOLOGY_SERVICE);
    monitor.addTopologyChangeListener(listener);
    monitor.reloadTopologies();
    List<String> autoDeploys = config.getAutoDeployTopologyNames();
    if (autoDeploys != null) {
      for (String topologyName : autoDeploys) {
        monitor.redeployTopologies(topologyName);
      }
    }

    final Collection<Topology> topologies = monitor.getTopologies();
    final Map<String, Integer> topologyPortMap = config.getGatewayPortMappings();

    // List of all the topology that are deployed
    final List<String> deployedTopologyList = new ArrayList<>();

    for (final Topology t : topologies) {
      deployedTopologyList.add(t.getName());
    }


    // Check whether the configured topologies for port mapping exist, if not
    // log WARN message and continue
    checkMappedTopologiesExist(topologyPortMap, deployedTopologyList);

    final HandlerCollection handlers = createHandlers( config, services, contexts, topologyPortMap);

     // Check whether a topology wants dedicated port,
     // if yes then we create a connector that listens on the provided port.

    log.gatewayTopologyPortMappingEnabled(config.isGatewayPortMappingEnabled());
    if (config.isGatewayPortMappingEnabled()) {
      for (Map.Entry<String, Integer> entry : topologyPortMap.entrySet()) {
        // Add connector for only valid topologies, i.e. deployed topologies.
        // and NOT for Default Topology listening on standard gateway port.
        if(deployedTopologyList.contains(entry.getKey()) && (entry.getValue() != config.getGatewayPort()) ) {
          log.createJettyConnector(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
          try {
            jetty.addConnector(createConnector(jetty, config, entry.getValue(),
                entry.getKey().toLowerCase(Locale.ROOT)));
          } catch(final IOException e) {
            /* in case of port conflict we log error and move on */
            if( e.toString().contains("ports for topologies (if defined) have to be unique.") ) {
              log.startedGatewayPortConflict(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
            } else {
              throw e;
            }
          }
        }
      }
    }

    jetty.setHandler(handlers);

    try {
      jetty.start();
    }
    catch (IOException e) {
      log.failedToStartGateway( e );
      throw e;
    }

    cleanupTopologyDeployments();

    // Start the topology monitor.
    log.monitoringTopologyChangesInDirectory(topologiesDir.getAbsolutePath());
    monitor.startMonitor();
  }

  public synchronized void stop() throws Exception {
    log.stoppingGateway();
    services.stop();
    monitor.stopMonitor();
    jetty.stop();
    jetty.join();
    log.stoppedGateway();
  }

  /**
   * Check whether a port is free
   *
   * @param port
   * @return true if port in use else false
   */
  public static boolean isPortInUse(final int port) {

    Socket socket = null;
    try {
      socket = new Socket("localhost", port);
      return true;
    } catch (final UnknownHostException e) {
      return false;
    } catch (final IOException e) {
      return false;
    } finally {
      IOUtils.closeQuietly(socket);
    }

  }

  /**
   * Checks whether the topologies defined in gateway-xml as part of Topology
   * Port mapping feature exists. If it does not Log a message and move on.
   *
   * @param configTopologies
   * @param topologies
   * @return
   */
  private void checkMappedTopologiesExist(
      final Map<String, Integer> configTopologies,
      final List<String> topologies) throws IOException {

    for(final Map.Entry<String, Integer> entry : configTopologies.entrySet()) {

      // If the topologies defined in gateway-config.xml are not found in gateway
      if (!topologies.contains(entry.getKey())) {
        log.topologyPortMappingCannotFindTopology(entry.getKey(), entry.getValue());
      }

    }

  }

  public URI getURI() {
    return jetty.getURI();
  }

  public InetSocketAddress[] getAddresses() {
    InetSocketAddress[] addresses = new InetSocketAddress[ jetty.getConnectors().length ];
    for( int i=0, n=addresses.length; i<n; i++ ) {
      NetworkConnector connector = (NetworkConnector)jetty.getConnectors()[ i ];
      String host = connector.getHost();
      if( host == null ) {
        addresses[ i ] = new InetSocketAddress( connector.getLocalPort() );
      } else {
        addresses[ i ] = new InetSocketAddress( host, connector.getLocalPort() );
      }
    }
    return addresses;
  }

  private KnoxErrorHandler createErrorHandler() {
    KnoxErrorHandler errorHandler = new KnoxErrorHandler();
    errorHandler.setShowStacks( false );
    errorHandler.setTracedBodyFilter( System.getProperty( "org.apache.knox.gateway.trace.body.status.filter" ) );
    return errorHandler;
  }

  private WebAppContext createWebAppContext( Topology topology, File warFile, String warPath ) throws IOException, ZipException, TransformerException, SAXException, ParserConfigurationException {
    String topoName = topology.getName();
    WebAppContext context = new WebAppContext();
    String contextPath;
    contextPath = "/" + Urls.trimLeadingAndTrailingSlashJoin( config.getGatewayPath(), topoName, warPath );
    context.setContextPath( contextPath );
    context.setWar( warFile.getAbsolutePath() );
    context.setAttribute( GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE, topoName );
    context.setAttribute( "org.apache.knox.gateway.frontend.uri", getFrontendUri( context, config ) );
    context.setAttribute( GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE, config );
    // Add support for JSPs.
    context.setAttribute(
        "org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
        ".*/[^/]*servlet-api-[^/]*\\.jar$|.*/javax.servlet.jsp.jstl-.*\\.jar$|.*/[^/]*taglibs.*\\.jar$" );
    context.setTempDirectory( FileUtils.getFile( warFile, "META-INF", "temp" ) );
    context.setErrorHandler( createErrorHandler() );
    context.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false");

    return context;
  }

  private static void explodeWar( File source, File target ) throws IOException, ZipException {
    if( source.isDirectory() ) {
      FileUtils.copyDirectory( source, target );
    } else {
      ZipFile zip = new ZipFile( source );
      zip.extractAll( target.getAbsolutePath() );
    }
  }

  private void mergeWebXmlOverrides( File webInfDir ) throws IOException, SAXException, ParserConfigurationException, TransformerException {
    File webXmlFile = new File( webInfDir, "web.xml" );
    Document webXmlDoc;
    if( webXmlFile.exists() ) {
      // Backup original web.xml file.
      File originalWebXmlFile = new File( webInfDir, "original-web.xml" );
      FileUtils.copyFile( webXmlFile, originalWebXmlFile );
      webXmlDoc = XmlUtils.readXml( webXmlFile );
    } else {
      webXmlDoc = XmlUtils.createDocument();
      webXmlDoc.appendChild( webXmlDoc.createElement( "web-app" ) );
    }
    File overrideWebXmlFile = new File( webInfDir, "override-web.xml" );
    if( overrideWebXmlFile.exists() ) {
      Document overrideWebXmlDoc = XmlUtils.readXml( overrideWebXmlFile );
      Element originalRoot = webXmlDoc.getDocumentElement();
      Element overrideRoot = overrideWebXmlDoc.getDocumentElement();
      NodeList overrideNodes = overrideRoot.getChildNodes();
      for( int i = 0, n = overrideNodes.getLength(); i < n; i++ ) {
        Node overrideNode = overrideNodes.item( i );
        if( overrideNode.getNodeType() == Node.ELEMENT_NODE ) {
          Node importedNode = webXmlDoc.importNode( overrideNode, true );
          originalRoot.appendChild( importedNode );
        }
      }
      
      XmlUtils.writeXml( webXmlDoc, new OutputStreamWriter(new FileOutputStream(webXmlFile), StandardCharsets.UTF_8) );
    }
  }

  private synchronized void internalDeployApplications( Topology topology, File topoDir ) throws IOException, ZipException, ParserConfigurationException, TransformerException, SAXException {
    if( topology != null ) {
      Collection<Application> applications = topology.getApplications();
      if( applications != null ) {
        for( Application application : applications ) {
          List<String> urls = application.getUrls();
          if( urls == null || urls.isEmpty() ) {
            internalDeployApplication( topology, topoDir, application, application.getName() );
          } else {
            for( String url : urls ) {
              internalDeployApplication( topology, topoDir, application, url );
            }
          }
        }
      }
    }
  }

  private synchronized void internalDeployApplication( Topology topology, File topoDir, Application application, String url ) throws IOException, ZipException, TransformerException, SAXException, ParserConfigurationException {
    File appsDir = new File( config.getGatewayApplicationsDir() );
    File appDir = new File( appsDir, application.getName() );
    File[] implFiles = appDir.listFiles( new RegexFilenameFilter( "app|app\\..*" ) );
    if( implFiles == null || implFiles.length == 0 ) {
      throw new DeploymentException( "Failed to find application in " + appDir );
    }
    File implFile = implFiles[0];
    File warDir = new File( topoDir, Urls.encode( "/" + Urls.trimLeadingAndTrailingSlash( url ) ) );
    File webInfDir = new File( warDir, "WEB-INF" );
    explodeWar( implFile, warDir );
    mergeWebXmlOverrides( webInfDir );
    createArchiveTempDir( warDir );
  }

  private synchronized void internalActivateTopology( Topology topology, File topoDir ) throws IOException, ZipException, ParserConfigurationException, TransformerException, SAXException {
    log.activatingTopology( topology.getName() );
    File[] files = topoDir.listFiles( new RegexFilenameFilter( "%.*" ) );
    if( files != null ) {
      for( File file : files ) {
        internalActivateArchive( topology, file );
      }
    }
  }

  private synchronized void internalActivateArchive( Topology topology, File warDir ) throws IOException, ZipException, ParserConfigurationException, TransformerException, SAXException {
    log.activatingTopologyArchive( topology.getName(), warDir.getName() );
    try {
      WebAppContext newContext = createWebAppContext( topology, warDir, Urls.decode( warDir.getName() ) );
      WebAppContext oldContext = deployments.get( newContext.getContextPath() );
      deployments.put( newContext.getContextPath(), newContext );
      if( oldContext != null ) {
        contexts.removeHandler( oldContext );
      }
      contexts.addHandler( newContext );
      if( contexts.isRunning() && !newContext.isRunning() ) {
          newContext.start();
      }

    } catch( Exception e ) {
      auditor.audit( Action.DEPLOY, topology.getName(), ResourceType.TOPOLOGY, ActionOutcome.FAILURE );
      log.failedToDeployTopology( topology.getName(), e );
    }
  }


  private synchronized void internalDeactivateTopology( Topology topology ) {

    log.deactivatingTopology( topology.getName() );

    String topoName = topology.getName();
    String topoPath = "/" + Urls.trimLeadingAndTrailingSlashJoin( config.getGatewayPath(), topoName );
    String topoPathSlash = topoPath + "/";

    ServiceRegistry sr = getGatewayServices().getService(GatewayServices.SERVICE_REGISTRY_SERVICE);
    if (sr != null) {
      sr.removeClusterServices( topoName );
    }

    // Find all the deployed contexts we need to deactivate.
    List<WebAppContext> deactivate = new ArrayList<>();
    if( deployments != null ) {
      for( WebAppContext app : deployments.values() ) {
        String appPath = app.getContextPath();
        if( appPath.equals( topoPath ) || appPath.startsWith( topoPathSlash ) ) {
          deactivate.add( app );
        }
      }
    }

    // Deactivate the required deployed contexts.
    for( WebAppContext context : deactivate ) {
      String contextPath = context.getContextPath();
      deployments.remove( contextPath );
      contexts.removeHandler( context );
      try {
        context.stop();
      } catch( Exception e ) {
        auditor.audit(Action.UNDEPLOY, topology.getName(), ResourceType.TOPOLOGY, ActionOutcome.FAILURE);
        log.failedToUndeployTopology( topology.getName(), e );
      }
    }
    deactivate.clear();

  }

  // Using an inner class to hide the handleTopologyEvent method from consumers of GatewayServer.
  private class InternalTopologyListener implements TopologyListener {

    @Override
    public void handleTopologyEvent( List<TopologyEvent> events ) {
      synchronized ( GatewayServer.this ) {
        for( TopologyEvent event : events ) {
          Topology topology = event.getTopology();
          File deployDir = calculateAbsoluteDeploymentsDir();
          if( event.getType().equals( TopologyEvent.Type.DELETED ) ) {
            handleDeleteDeployment(topology, deployDir);
          } else {
            handleCreateDeployment(topology, deployDir);
          }
        }
      }
    }

    private void handleDeleteDeployment(Topology topology, File deployDir) {
      log.deletingTopology( topology.getName() );
      File[] files = deployDir.listFiles( new RegexFilenameFilter( topology.getName() + "\\.(war|topo)\\.[0-9A-Fa-f]+" ) );
      if( files != null ) {
        auditor.audit(Action.UNDEPLOY, topology.getName(), ResourceType.TOPOLOGY,
          ActionOutcome.UNAVAILABLE);
        internalDeactivateTopology( topology );
        for( File file : files ) {
          log.deletingDeployment( file.getAbsolutePath() );
          FileUtils.deleteQuietly( file );
        }
      }
    }

    private void handleCreateDeployment(Topology topology, File deployDir) {
      try {
        File topoDir = calculateDeploymentDir( topology );
        if( !topoDir.exists() ) {
          auditor.audit( Action.DEPLOY, topology.getName(), ResourceType.TOPOLOGY, ActionOutcome.UNAVAILABLE );

//          KNOX-564 - Topology should fail to deploy with no providers configured.
//TODO:APPS:This should only fail if there are services in the topology.
          if(topology.getProviders().isEmpty()) {
            throw new DeploymentException("No providers found inside topology.");
          }

          log.deployingTopology( topology.getName(), topoDir.getAbsolutePath() );
          internalDeactivateTopology( topology ); // KNOX-152

          EnterpriseArchive ear = DeploymentFactory.createDeployment( config, topology );
          if( !deployDir.exists() && !deployDir.mkdirs() ) {
            throw new DeploymentException( "Failed to create topology deployment temporary directory: " + deployDir.getAbsolutePath() );
          }
          File tmp = ear.as( ExplodedExporter.class ).exportExploded( deployDir, topoDir.getName() + ".tmp" );
          if( !tmp.renameTo( topoDir ) ) {
            FileUtils.deleteQuietly( tmp );
            throw new DeploymentException( "Failed to create topology deployment directory: " + topoDir.getAbsolutePath() );
          }
          internalDeployApplications( topology, topoDir );
          internalActivateTopology( topology, topoDir );
          log.deployedTopology( topology.getName());
        } else {
          auditor.audit( Action.REDEPLOY, topology.getName(), ResourceType.TOPOLOGY, ActionOutcome.UNAVAILABLE );
          log.redeployingTopology( topology.getName(), topoDir.getAbsolutePath() );
          internalActivateTopology( topology, topoDir );
          log.redeployedTopology( topology.getName() );
        }
        cleanupTopologyDeployments( deployDir, topology );
      } catch( Throwable e ) {
        auditor.audit( Action.DEPLOY, topology.getName(), ResourceType.TOPOLOGY, ActionOutcome.FAILURE );
        log.failedToDeployTopology( topology.getName(), e );
      }
    }

  }

  private File createArchiveTempDir( File warDir ) {
    File tempDir = FileUtils.getFile( warDir, "META-INF", "temp" );
    if( !tempDir.exists() && !tempDir.mkdirs() ) {
      throw new DeploymentException( "Failed to create archive temporary directory: " + tempDir.getAbsolutePath() );
    }
    return tempDir;
  }

  private static File calculateAbsoluteTopologiesDir( GatewayConfig config ) {
    File topoDir = new File( config.getGatewayTopologyDir() );
    topoDir = topoDir.getAbsoluteFile();
    return topoDir;
  }

  private static File calculateAbsoluteDeploymentsDir( GatewayConfig config ) {
    File deployDir = new File( config.getGatewayDeploymentDir() );
    deployDir = deployDir.getAbsoluteFile();
    return deployDir;
  }

  private File calculateAbsoluteTopologiesDir() {
    return calculateAbsoluteTopologiesDir( config );
  }

  private File calculateAbsoluteDeploymentsDir() {
    return calculateAbsoluteDeploymentsDir( config );
  }

  private File calculateDeploymentDir( Topology topology ) {
    File dir = new File( calculateAbsoluteDeploymentsDir(), calculateDeploymentName( topology ) );
    return dir;
  }

  private String calculateDeploymentExtension( Topology topology ) {
    return ".topo.";
  }

  private String calculateDeploymentName( Topology topology ) {
    String name = topology.getName() + calculateDeploymentExtension( topology ) + Long.toHexString( topology.getTimestamp() );
    return name;
  }

  private static void checkAddressAvailability( InetSocketAddress address ) throws IOException {
    ServerSocket socket = new ServerSocket();
    socket.bind( address );
    socket.close();
  }

  private static class RegexFilenameFilter implements FilenameFilter {

    Pattern pattern;

    RegexFilenameFilter( String regex ) {
      pattern = Pattern.compile( regex );
    }

    @Override
    public boolean accept( File dir, String name ) {
      return pattern.matcher( name ).matches();
    }
  }

  public URI getFrontendUri( WebAppContext context, GatewayConfig config ) {
    URI frontendUri = null;
    String frontendStr = config.getFrontendUrl();
    if( frontendStr != null && !frontendStr.trim().isEmpty() ) {
      String topoName = (String)context.getAttribute( GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE );
      try {
        frontendStr = frontendStr.trim();
        if( frontendStr.endsWith( "/" ) ) {
          frontendUri = new URI( frontendStr + topoName );
        } else {
          frontendUri = new URI( frontendStr + "/" + topoName );
        }
      } catch( URISyntaxException e ) {
        throw new IllegalArgumentException( e );
      }
    }
    return frontendUri;
  }

  private static class FileModificationTimeDescendingComparator implements Comparator<File>, Serializable {
    /**
     *
     */
    private static final long serialVersionUID = -2269785204848916823L;

    @Override
    public int compare( File left, File right ) {
      long leftTime = ( left == null ? Long.MIN_VALUE : left.lastModified() );
      long rightTime = ( right == null ? Long.MIN_VALUE : right.lastModified() );
      if( leftTime > rightTime ) {
        return -1;
      } else if ( leftTime < rightTime ) {
        return 1;
      } else {
        return 0;
      }
    }
  }

}
