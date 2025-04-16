<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

<img src="../../static/images/knox-logo.gif" alt="Knox"/>
<img src="../../static/images/apache-logo.gif" align="right" alt="Apache"/>

# Apache Knox Gateway 2.1.x Developer's Guide #

## Overview ##

Apache Knox gateway is a specialized reverse proxy gateway for various Hadoop REST APIs.
However, the gateway is built entirely upon a fairly generic framework.
This framework is used to "plug-in" all of the behavior that makes it specific to Hadoop in general and any particular Hadoop REST API.
It would be equally as possible to create a customized reverse proxy for other non-Hadoop HTTP endpoints.
This approach is taken to ensure that the Apache Knox gateway can scale with the rapidly evolving Hadoop ecosystem.

Throughout this guide we will be using a publicly available REST API to demonstrate the development of various extension mechanisms.
http://openweathermap.org/

### Architecture Overview ###

The gateway itself is a layer over an embedded Jetty JEE server.
At the very highest level the gateway processes requests by using request URLs to lookup specific JEE Servlet Filter chain that is used to process the request.
The gateway framework provides extensible mechanisms to assemble chains of custom filters that support secured access to services.

The gateway has two primary extensibility mechanisms: Service and Provider.
The Service extensibility framework provides a way to add support for new HTTP/REST endpoints.
For example, the support for WebHdfs is plugged into the Knox gateway as a Service.
The Provider extensibility framework allows adding new features to the gateway that can be used across Services.
An example of a Provider is an authentication provider.
Providers can also expose APIs that other service and provider extensions can utilize.

Service and Provider integrations interact with the gateway framework in two distinct phases: Deployment and Runtime.
The gateway framework can be thought of as a layer over the JEE Servlet framework.
Specifically all runtime processing within the gateway is performed by JEE Servlet Filters.
The two phases interact with this JEE Servlet Filter based model in very different ways.
The first phase, Deployment, is responsible for converting fairly simple to understand configuration called topology into JEE WebArchive (WAR) based implementation details.
The second phase, Runtime, is the processing of requests via a set of Filters configured in the WAR.

From an "ethos" perspective, Service and Provider extensions should attempt to incur complexity associated with configuration in the deployment phase.
This should allow for very streamlined request processing that is very high performance and easily testable.
The preference at runtime, in OO style, is for small classes that perform a specific function.
The ideal set of implementation classes are then assembled by the Service and Provider plugins during deployment.

A second critical design consideration is streaming.
The processing infrastructure is build around JEE Servlet Filters as they provide a natural streaming interception model.
All Provider implementations should make every attempt to maintaining this streaming characteristic.

### Project Overview ###

The table below describes the purpose of the current modules in the project.
Of particular importance are the root pom.xml and the gateway-release module.
The root pom.xml is critical because this is where all dependency version must be declared.
There should be no dependency version information in module pom.xml files.
The gateway-release module is critical because the dependencies declared there essentially define the classpath of the released gateway server.
This is also true of the other -release modules in the project.

| File/Module                                    | Description                                               |
| -----------------------------------------------|-----------------------------------------------------------|
| LICENSE                                        | The license for all source files in the release.          |
| NOTICE                                         | Attributions required by dependencies.                    |
| README                                         | A brief overview of the Knox project.                     |
| CHANGES                                        | A description of the changes for each release.            |
| ISSUES                                         | The knox issues for the current release.                  |
| gateway-util-common                            | Common low level utilities used by many modules.          |
| gateway-util-launcher                          | The launcher framework.                                   |
| gateway-util-urltemplate                       | The i18n logging and resource framework.                  |
| gateway-i18n                                   | The URL template and rewrite utilities                    |
| gateway-i18n-logging-log4j                     | The integration of i18n logging with log4j.               |
| gateway-i18n-logging-sl4j                      | The integration of i18n logging with sl4j.                |
| gateway-spi                                    | The SPI for service and provider extensions.              |
| gateway-provider-identity-assertion-common     | The identity assertion provider base                      |
| gateway-provider-identity-assertion-concat     | An identity assertion provider that facilitates prefix and suffix concatenation.|
| gateway-provider-identity-assertion-pseudo     | The default identity assertion provider.                  |
| gateway-provider-jersey                        | The jersey display provider.                              |
| gateway-provider-rewrite                       | The URL rewrite provider.                                 |
| gateway-provider-rewrite-func-hostmap-static   | Host mapping function extension to rewrite.               |
| gateway-provider-rewrite-func-service-registry | Service registry function extension to rewrite.           |
| gateway-provider-rewrite-step-secure-query     | Crypto step extension to rewrite.                         |
| gateway-provider-security-authz-acls           | Service level authorization.                              |
| gateway-provider-security-jwt                  | JSON Web Token utilities.                                 |
| gateway-provider-security-preauth              | Preauthenticated SSO header support.                      |
| gateway-provider-security-shiro                | Shiro authentiation integration.                          |
| gateway-provider-security-webappsec            | Filters to prevent common webapp security issues.         |
| gateway-service-as                             | The implementation of the Access service POC.             |
| gateway-service-definitions                    | The implementation of the Service definition and rewrite files. |
| gateway-service-hbase                          | The implementation of the HBase service.                  |
| gateway-service-hive                           | The implementation of the Hive service.                   |
| gateway-service-oozie                          | The implementation of the Oozie service.                  |
| gateway-service-tgs                            | The implementation of the Ticket Granting service POC.    |
| gateway-service-webhdfs                        | The implementation of the WebHdfs service.                |
| gateway-discovery-ambari                       | The Ambari service URL discovery implementation.          |
| gateway-service-remoteconfig                   | The implementation of the RemoteConfigurationRegistryClientService. |
| gateway-server                                 | The implementation of the Knox gateway server.            |
| gateway-shell                                  | The implementation of the Knox Groovy shell.              |
| gateway-test-ldap                              | Pulls in all of the dependencies of the test LDAP server. |
| gateway-server-launcher                        | The launcher definition for the gateway.                  |
| gateway-shell-launcher                         | The launcher definition for the shell.                    |
| knox-cli-launcher                              | A module to pull in all of the dependencies of the CLI.   |
| gateway-test-ldap-launcher                     | The launcher definition for the test LDAP server.         |
| gateway-release                                | The definition of the gateway binary release. Contains content and dependencies to be included in binary gateway package. |
| gateway-test-utils                             | Various utilities used in unit and system tests.          |
| gateway-test                                   | The functional tests.                                     |
| pom.xml                                        | The top level pom.                                        |
| build.xml                                      | A collection of utility for building and releasing.       |


### Development Processes ###

The project uses Maven in general with a few convenience Ant targets.

Building the project can be built via Maven or Ant.  The two commands below are equivalent.

```
mvn clean install
ant
```

A more complete build can be done that builds and generates the unsigned ZIP release artifacts.
You will find these in the target/{version} directory (e.g. target/0.XX.0-SNAPSHOT).

```
mvn -Ppackage clean install
ant release
```

There are a few other Ant targets that are especially convenient for testing.

This command installs the gateway into the {{{install}}} directory of the project.
Note that this command does not first build the project.

```
ant install-test-home
```

This command starts the gateway and LDAP servers installed by the command above into a test GATEWAY_HOME (i.e. install).
Note that this command does not first install the test home.

```
ant start-test-servers
```

So putting things together the following Ant command will build a release, install it and start the servers ready for manual testing.

```
ant release install-test-home start-test-servers
```

### Docker Image ###

Apache Knox ships with a `docker` Maven module that will build a Docker image. To build the Knox Docker image, you must have Docker running on your machine. The following Maven command will build Knox and package it into a Docker image.

```
mvn -Ppackage,release,docker clean package
```

This will build 2 Docker images:

* `apache/knox:gateway-2.1.0-SNAPSHOT`
* `apache/knox:ldap-2.1.0-SNAPSHOT`

The `gateway` image will use an entrypoint to start Knox Gateway. The `ldap` image will use an entrypoint to start Knox Demo LDAP.

An example of using the Docker images would be the following:

```
docker run -d --name knox-ldap -p 33389:33389 apache/knox:ldap-2.1.0-SNAPSHOT
docker run -d --name knox-gateway -p 8443:8443 apache/knox:gateway-2.1.0-SNAPSHOT
```

Using docker-compose that would look like this:

```
docker-compose -f gateway-docker/src/main/resources/docker-compose.yml up
```

The images are designed to be a base that can be built on to add your own providers, descriptors, and topologies as necessary.

## Behavior ##

There are two distinct phases in the behavior of the gateway.
These are the deployment and runtime phases.
The deployment phase is responsible for converting topology descriptors into an executable JEE style WAR.
The runtime phase is the processing of requests via WAR created during the deployment phase.

The deployment phase is arguably the more complex of the two phases.
This is because runtime relies on well known JEE constructs while deployment introduces new framework concepts.
The base concept of the deployment framework is that of a "contributor".
In the framework, contributors are pluggable component responsible for generating JEE WAR artifacts from topology files.

### Deployment Behavior ###

The goal of the deployment phase is to take easy to understand topology descriptions and convert them into optimized runtime artifacts.
Our goal is not only should the topology descriptors be easy to understand, but have them be easy for a management system (e.g. Ambari) to generate.
Think of deployment as compiling an assembly descriptor into a JEE WAR.
WARs are then deployed to an embedded JEE container (i.e. Jetty).

Consider the results of starting the gateway the first time.
There are two sets of files that are relevant for deployment.
The first is the topology file `<GATEWAY_HOME>/conf/topologies/sandbox.xml`.
This second set is the WAR structure created during the deployment of the topology file.

```
data/deployments/sandbox.war.143bfef07f0/WEB-INF
  web.xml
  gateway.xml
  shiro.ini
  rewrite.xml
  hostmap.txt
```

Notice that the directory `sandbox.war.143bfef07f0` is an "unzipped" representation of a JEE WAR file.
This specifically means that it contains a `WEB-INF` directory which contains a `web.xml` file.
For the curious the strange number (i.e. 143bfef07f0) in the name of the WAR directory is an encoded timestamp.
This is the timestamp of the topology file (i.e. sandbox.xml) at the time the deployment occurred.
This value is used to determine when topology files have changed and redeployment is required.

Here is a brief overview of the purpose of each file in the WAR structure.

web.xml
: A standard JEE WAR descriptor.
In this case a build-in GatewayServlet is mapped to the url pattern /*.

gateway.xml
: The configuration file for the GatewayServlet.
Defines the filter chain that will be applied to each service's various URLs.

shiro.ini
: The configuration file for the Shiro authentication provider's filters.
This information is derived from the information in the provider section of the topology file.

rewrite.xml
: The configuration file for the rewrite provider's filter.
This captures all of the rewrite rules for the services.
These rules are contributed by the contributors for each service.

hostmap.txt
: The configuration file the hostmap provider's filter.
This information is derived from the information in the provider section of the topology file.

The deployment framework follows "visitor" style patterns.
Each topology file is parsed and the various constructs within it are "visited".
The appropriate contributor for each visited construct is selected by the framework.
The contributor is then passed the contrust from the topology file and asked to update the JEE WAR artifacts.
Each contributor is free to inspect and modify any portion of the WAR artifacts.

The diagram below provides an overview of the deployment processing.
Detailed descriptions of each step follow the diagram.

```plantuml
@startuml
!include deployment-overview.puml
@enduml
```

1. The gateway server loads a topology file from conf/topologies into an internal structure.

2. The gateway server delegates to a deployment factory to create the JEE WAR structure.

3. The deployment factory first creates a basic WAR structure with WEB-INF/web.xml.

4. Each provider and service in the topology is visited and the appropriate deployment contributor invoked.
Each contributor is passed the appropriate information from the topology and modifies the WAR structure.

5. A complete WAR structure is returned to the gateway service.

6. The gateway server uses internal container APIs to dynamically deploy the WAR.

The Java method below is the actual code from the DeploymentFactory that implements this behavior.
You will note the initialize, contribute, finalize sequence.
Each contributor is given three opportunities to interact with the topology and archive.
This allows the various contributors to interact if required.
For example, the service contributors use the deployment descriptor added to the WAR by the rewrite provider.

```java
public static WebArchive createDeployment( GatewayConfig config, Topology topology ) {
  Map<String,List<ProviderDeploymentContributor>> providers;
  Map<String,List<ServiceDeploymentContributor>> services;
  DeploymentContext context;

  providers = selectContextProviders( topology );
  services = selectContextServices( topology );
  context = createDeploymentContext( config, topology.getName(), topology, providers, services );

  initialize( context, providers, services );
  contribute( context, providers, services );
  finalize( context, providers, services );

  return context.getWebArchive();
}
```

Below is a diagram that provides more detail.
This diagram focuses on the interactions between the deployment factory and the service deployment contributors.
Detailed description of each step follow the diagram.

```plantuml
@startuml
!include deployment-service.puml
@enduml
```

1. The gateway server loads global configuration (i.e. <GATEWAY_HOME>/conf/gateway-site.xml

2. The gateway server loads a topology descriptor file.

3. The gateway server delegates to the deployment factory to create a deployable WAR structure.

4. The deployment factory creates a runtime descriptor to configure that gateway servlet.

5. The deployment factory creates a basic WAR structure and adds the gateway servlet runtime descriptor to it.

6. The deployment factory creates a deployment context object and adds the WAR structure to it.

7. For each service defined in the topology descriptor file the appropriate service deployment contributor is selected and invoked.
The correct service deployment contributor is determined by matching the role of a service in the topology descriptor
to a value provided by the getRole() method of the ServiceDeploymentContributor interface.
The initializeContribution method from _each_ service identified in the topology is called.
Each service deployment contributor is expected to setup any runtime artifacts in the WAR that other services or provides may need.

8. The contributeService method from _each_ service identified in the topology is called.
This is where the service deployment contributors will modify any runtime descriptors.

9. One of they ways that a service deployment contributor can modify the runtime descriptors is by asking the framework to contribute filters.
This is how services are loosely coupled to the providers of features.
For example a service deployment contributor might ask the framework to contribute the filters required for authorization.
The deployment framework will then delegate to the correct provider deployment contributor to add filters for that feature.

10. Finally the finalizeContribution method for each service is invoked.
This provides an opportunity to react to anything done via the contributeService invocations and tie up any loose ends.

11. The populated WAR is returned to the gateway server.

The following diagram will provide expanded detail on the behavior of provider deployment contributors.
Much of the beginning and end of the sequence shown overlaps with the service deployment sequence above.
Those steps (i.e. 1-6, 17) will not be described below for brevity.
The remaining steps have detailed descriptions following the diagram.

```plantuml
@startuml
!include deployment-provider.puml
@enduml
```

7. For each provider the appropriate provider deployment contributor is selected and invoked.
The correct service deployment contributor is determined by first matching the role of a provider in the topology descriptor
to a value provided by the getRole() method of the ProviderDeploymentContributor interface.
If this is ambiguous, the name from the topology is used match the value provided by the getName() method of the ProviderDeploymentContributor interface.
The initializeContribution method from _each_ provider identified in the topology is called.
Each provider deployment contributor is expected to setup any runtime artifacts in the WAR that other services or provides may need.
Note: In addition, others provider not explicitly referenced in the topology may have their initializeContribution method called.
If this is the case only one default instance for each role declared vis the getRole() method will be used.
The method used to determine the default instance is non-deterministic so it is best to select a particular named instance of a provider for each role.

8. Each provider deployment contributor will typically add any runtime deployment descriptors it requires for operation.
These descriptors are added to the WAR structure within the deployment context.

9. The contributeProvider method of each configured or default provider deployment contributor is invoked.

10. Each provider deployment contributor populates any runtime deployment descriptors based on information in the topology.

11. Provider deployment contributors are never asked to contribute to the deployment directly.
Instead a service deployment contributor will ask to have a particular provider role (e.g. authentication) contribute to the deployment.

12. A service deployment contributor asks the framework to contribute filters for a given provider role.

13. The framework selects the appropriate provider deployment contributor and invokes its contributeFilter method.

14. During this invocation the provider deployment contributor populate populate service specific information.
In particular it will add filters to the gateway servlet's runtime descriptor by adding JEE Servlet Filters.
These filters will be added to the resources (or URLs) identified by the service deployment contributor.

15. The finalizeContribute method of all referenced and default provider deployment contributors is invoked.

16. The provider deployment contributor is expected to perform any final modifications to the runtime descriptors in the WAR structure.

### Runtime Behavior ###

The runtime behavior of the gateway is somewhat simpler as it more or less follows well known JEE models.
There is one significant wrinkle.
The filter chains are managed within the GatewayServlet as opposed to being managed by the JEE container.
This is the result of an early decision made in the project.
The intention is to allow more powerful URL matching than is provided by the JEE Servlet mapping mechanisms.

The diagram below provides a high-level overview of the runtime processing.
An explanation for each step is provided after the diagram.

```plantuml
@startuml
!include runtime-overview.puml
@enduml
```

1. A REST client makes a HTTP request that is received by the embedded JEE container.

2. A filter chain is looked up in a map of URLs to filter chains.

3. The filter chain, which is itself a filter, is invoked.

4. Each filter invokes the filters that follow it in the chain.
The request and response objects can be wrapped in typically JEE Filter fashion.
Filters may not continue chain processing and return if that is appropriate.

5. Eventually the end of the last filter in the chain is invoked.
Typically this is a special "dispatch" filter that is responsible for dispatching the request to the ultimate endpoint.
Dispatch filters are also responsible for reading the response.

6. The response may be in the form of a number of content types (e.g. application/json, text/xml).

7. The response entity is streamed through the various response wrappers added by the filters.
These response wrappers may rewrite various portions of the headers and body as per their configuration.

8. The return of the response entity to the client is ultimately "pulled through" the filter response wrapper by the container.

9. The response entity is returned original client.

This diagram provides a more detailed breakdown of the request processing.
Again, descriptions of each step follow the diagram.

```plantuml
@startuml
!include runtime-request-processing.puml
@enduml
```

1. A REST client makes a HTTP request that is received by the embedded JEE container.

2. The embedded container looks up the servlet mapped to the URL and invokes the service method.
This our case the GatewayServlet is mapped to /* and therefore receives all requests for a given topology.
Keep in mind that the WAR itself is deployed on a root context path that typically contains a level for the gateway and the name of the topology.
This means that there is a single GatewayServlet per topology and it is effectivly mapped to <gateway>/<topology>/*.

3. The GatewayServlet holds a single reference to a GatewayFilter which is a specialized JEE Servlet Filter.
This choice was made to allow the GatewayServlet to dynamically deploy modified topologies.
This is done by building a new GatewayFilter instance and replacing the old in an atomic fashion.

4. The GatewayFilter contains another layer of URL mapping as defined in the gateway.xml runtime descriptor.
The various service deployment contributor added these mappings at deployment time.
Each service may add a number of different sub-URLs depending in their requirements.
These sub-URLs will all be mapped to independently configured filter chains.

5. The GatewayFilter invokes the doFilter method on the selected chain.

6. The chain invokes the doFilter method of the first filter in the chain.

7. Each filter in the chain continues processing by invoking the doFilter on the next filter in the chain.
Ultimately a dispatch filter forward the request to the real service instead of invoking another filter.
This is sometimes referred to as pivoting.

## Gateway Servlet & Gateway Filter ##

TODO

```xml
<web-app>

  <servlet>
    <servlet-name>sample</servlet-name>
    <servlet-class>org.apache.knox.gateway.GatewayServlet</servlet-class>
    <init-param>
      <param-name>gatewayDescriptorLocation</param-name>
      <param-value>gateway.xml</param-value>
    </init-param>
  </servlet>

  <servlet-mapping>
    <servlet-name>sandbox</servlet-name>
    <url-pattern>/*</url-pattern>
  </servlet-mapping>

  <listener>
    <listener-class>org.apache.knox.gateway.services.GatewayServicesContextListener</listener-class>
  </listener>

  ...

</web-app>
```

```xml
<gateway>

  <resource>
    <role>WEATHER</role>
    <pattern>/weather/**?**</pattern>

    <filter>
      <role>authentication</role>
      <name>sample</name>
      <class>...</class>
    </filter>

    <filter>...</filter>*

  </resource>

</gateway>
```

```java
@Test
public void testDevGuideSample() throws Exception {
  Template pattern, input;
  Matcher<String> matcher;
  Matcher<String>.Match match;

  // GET http://api.openweathermap.org/data/2.5/weather?q=Palo+Alto
  pattern = Parser.parse( "/weather/**?**" );
  input = Parser.parse( "/weather/2.5?q=Palo+Alto" );

  matcher = new Matcher<String>();
  matcher.add( pattern, "fake-chain" );
  match = matcher.match( input );

  assertThat( match.getValue(), is( "fake-chain") );
}
```

## Extension Logistics ##

There are a number of extension points available in the gateway: services, providers, rewrite steps and functions, etc.
All of these use the Java ServiceLoader mechanism for their discovery.
There are two ways to make these extensions available on the class path at runtime.
The first way to add a new module to the project and have the extension "built-in".
The second is to add the extension to the class path of the server after it is installed.
Both mechanism are described in more detail below.

### Service Loaders ###

Extensions are discovered via Java's [Service Loader](http://docs.oracle.com/javase/6/docs/api/java/util/ServiceLoader.html) mechanism.
There are good [tutorials](http://docs.oracle.com/javase/tutorial/ext/basics/spi.html) available for learning more about this.
The basics come down to two things.

1. Implement the service contract interface (e.g. ServiceDeploymentContributor, ProviderDeploymentContributor)

2. Create a file in META-INF/services of the JAR that will contain the extension.
This file will be named as the fully qualified name of the contract interface (e.g. org.apache.knox.gateway.deploy.ProviderDeploymentContributor).
The contents of the file will be the fully qualified names of any implementation of that contract interface in that JAR.

One tip is to include a simple test with each of you extension to ensure that it will be properly discovered.
This is very helpful in situations where a refactoring fails to change the a class in the META-INF/services files.
An example of one such test from the project is shown below.

```java
  @Test
  public void testServiceLoader() throws Exception {
    ServiceLoader loader = ServiceLoader.load( ProviderDeploymentContributor.class );
    Iterator iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof ShiroDeploymentContributor ) {
        return;
      }
    }
    fail( "Failed to find " + ShiroDeploymentContributor.class.getName() + " via service loader." );
  }
```

### Class Path ###

One way to extend the functionality of the server without having to recompile is to add the extension JARs to the servers class path.
As an extensible server this is made straight forward but it requires some understanding of how the server's classpath is setup.
In the <GATEWAY_HOME> directory there are four class path related directories (i.e. bin, lib, dep, ext).

The bin directory contains very small "launcher" jars that contain only enough code to read configuration and setup a class path.
By default the configuration of a launcher is embedded with the launcher JAR but it may also be extracted into a .cfg file.
In that file you will see how the class path is defined.

```
class.path=../lib/*.jar,../dep/*.jar;../ext;../ext/*.jar
```

The paths are all relative to the directory that contains the launcher JAR.

../lib/*.jar
: These are the "built-in" jars that are part of the project itself.
Information is provided elsewhere in this document for how to integrate a built-in extension.

../dep/*.jar
: These are the JARs for all of the external dependencies of the project.
This separation between the generated JARs and dependencies help keep licensing issues straight.

../ext
: This directory is for post-install extensions and is empty by default.
Including the directory (vs *.jar) allows for individual classes to be placed in this directory.

../ext/*.jar
: This would pick up all extension JARs placed in the ext directory.

Note that order is significant.  The lib JARs take precedence over dep JARs and they take precedence over ext classes and JARs.

### Maven Module ###

Integrating an extension into the project follows well established Maven patterns for adding modules.
Below are several points that are somewhat unique to the Knox project.

1. Add the module to the root pom.xml file's <modules> list.
Take care to ensure that the module is in the correct place in the list based on its dependencies.
Note: In general modules should not have non-test dependencies on gateway-server but rather gateway-spi

2. Any new dependencies must be represented in the root pom.xml file's <dependencyManagement> section.
The required version of the dependencies will be declared there.
The new sub-module's pom.xml file must not include dependency version information.
This helps prevent dependency version conflict issues.

3. If the extension is to be "built into" the released gateway server it needs to be added as a dependency to the gateway-release module.
This is done by adding to the <dependencies> section of the gateway-release's pom.xml file.
If this isn't done the JARs for the module will not be automatically packaged into the release artifacts.
This can be useful while an extension is under development but not yet ready for inclusion in the release.

More detailed examples of adding both a service and a provider extension are provided in subsequent sections.

### Services ###

Services are extensions that are responsible for converting information in the topology file to runtime descriptors.
Typically services do not require their own runtime descriptors.
Rather, they modify either the gateway runtime descriptor (i.e. gateway.xml) or descriptors of other providers (e.g. rewrite.xml).

The service provider interface for a Service is ServiceDeploymentContributor and is shown below.

```java
package org.apache.knox.gateway.deploy;
import org.apache.knox.gateway.topology.Service;
public interface ServiceDeploymentContributor {
  String getRole();
  void initializeContribution( DeploymentContext context );
  void contributeService( DeploymentContext context, Service service ) throws Exception;
  void finalizeContribution( DeploymentContext context );
}
```

Each service provides an implementation of this interface that is discovered via the ServerLoader mechanism previously described.
The meaning of this is best understood in the context of the structure of the topology file.
A fragment of a topology file is shown below.

```xml
<topology>
    <gateway>
        ....
    </gateway>
    <service>
        <role>WEATHER</role>
        <url>http://api.openweathermap.org/data</url>
    </service>
    ....
</topology>
```

With these two things a more detailed description of the purpose of each ServiceDeploymentContributor method should be helpful.

String getRole();
: This is the value the framework uses to associate a given `<service><role>` with a particular ServiceDeploymentContributor implementation.
See below how the example WeatherDeploymentContributor implementation returns the role WEATHER that matches the value in the topology file.
This will result in the WeatherDeploymentContributor's methods being invoked when a WEATHER service is encountered in the topology file.

```java
public class WeatherDeploymentContributor extends ServiceDeploymentContributorBase {
  private static final String ROLE = "WEATHER";
  @Override
  public String getRole() {
    return ROLE;
  }
  ...
}
```

void initializeContribution( DeploymentContext context );
: In this method a contributor would create, initialize and add any descriptors it was responsible for to the deployment context.
For the weather service example this isn't required so the empty method isn't shown here.

void contributeService( DeploymentContext context, Service service ) throws Exception;
: In this method a service contributor typically add and configures any features it requires.
This method will be dissected in more detail below.

void finalizeContribution( DeploymentContext context );
: In this method a contributor would finalize any descriptors it was responsible for to the deployment context.
For the weather service example this isn't required so the empty method isn't shown here.

#### Service Contribution Behavior ####

In order to understand the job of the ServiceDeploymentContributor a few runtime descriptors need to be introduced.

Gateway Runtime Descriptor: WEB-INF/gateway.xml
: This runtime descriptor controls the behavior of the GatewayFilter.
It defines a mapping between resources (i.e. URL patterns) and filter chains.
The sample gateway runtime descriptor helps illustrate.

```xml
<gateway>
  <resource>
    <role>WEATHER</role>
    <pattern>/weather/**?**</pattern>
    <filter>
      <role>authentication</role>
      <name>sample</name>
      <class>...</class>
    </filter>
    <filter>...</filter>*
    ...
  </resource>
</gateway>
```

Rewrite Provider Runtime Descriptor: WEB-INF/rewrite.xml
: The rewrite provider runtime descriptor controls the behavior of the rewrite filter.
Each service contributor is responsible for adding the rules required to control the URL rewriting required by that service.
Later sections will provide more detail about the capabilities of the rewrite provider.

```xml
<rules>
  <rule dir="IN" name="WEATHER/openweathermap/inbound/versioned/file"
      pattern="*://*:*/**/weather/{version}?{**}">
    <rewrite template="{$serviceUrl[WEATHER]}/{version}/weather?{**}"/>
  </rule>
</rules>
```

With these two descriptors in mind a detailed breakdown of the WeatherDeploymentContributor's contributeService method will make more sense.
At a high level the important concept is that contributeService is invoked by the framework for each <service> in the topology file.

```java
public class WeatherDeploymentContributor extends ServiceDeploymentContributorBase {
  ...
  @Override
  public void contributeService( DeploymentContext context, Service service ) throws Exception {
    contributeResources( context, service );
    contributeRewriteRules( context );
  }

  private void contributeResources( DeploymentContext context, Service service ) throws URISyntaxException {
    ResourceDescriptor resource = context.getGatewayDescriptor().addResource();
    resource.role( service.getRole() );
    resource.pattern( "/weather/**?**" );
    addAuthenticationFilter( context, service, resource );
    addRewriteFilter( context, service, resource );
    addDispatchFilter( context, service, resource );
  }

  private void contributeRewriteRules( DeploymentContext context ) throws IOException {
    UrlRewriteRulesDescriptor allRules = context.getDescriptor( "rewrite" );
    UrlRewriteRulesDescriptor newRules = loadRulesFromClassPath();
    allRules.addRules( newRules );
  }

  ...
}
```

The DeploymentContext parameter contains information about the deployment as well as the WAR structure being created via deployment.
The Service parameter is the object representation of the <service> element in the topology file.
Details about particularly important lines follow the code block.

ResourceDescriptor resource = context.getGatewayDescriptor().addResource();
: Obtains a reference to the gateway runtime descriptor and adds a new resource element.
Note that many of the APIs in the deployment framework follow a fluent vs bean style.

resource.role( service.getRole() );
: Sets the role for a particular resource.
Many of the filters may need access to this role information in order to make runtime decisions.

resource.pattern( "/weather/**?**" );
: Sets the URL pattern to which the filter chain that will follow will be mapped within the GatewayFilter.

add*Filter( context, service, resource );
: These are taken from a base class.
A representation of the implementation of that method from the base class is shown below.
Notice how this essentially delegates back to the framework to add the filters required by a particular provider role (e.g. "rewrite").

```java
  protected void addRewriteFilter( DeploymentContext context, Service service, ResourceDescriptor resource ) {
    context.contributeFilter( service, resource, "rewrite", null, null );
  }
```

UrlRewriteRulesDescriptor allRules = context.getDescriptor( "rewrite" );
: Here the rewrite provider runtime descriptor is obtained by name from the deployment context.
This does represent a tight coupling in this case between this service and the default rewrite provider.
The rewrite provider however is unlikely to be related with alternate implementations.

UrlRewriteRulesDescriptor newRules = loadRulesFromClassPath();
: This is convenience method for loading partial rewrite descriptor information from the classpath.
Developing and maintaining these rewrite rules is far easier as an external resource.
The rewrite descriptor API could however have been used to achieve the same result.

allRules.addRules( newRules );
: Here the rewrite rules for the weather service are merged into the larger set of rewrite rules.

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.apache.knox</groupId>
        <artifactId>gateway</artifactId>
        <version>2.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>gateway-service-weather</artifactId>
    <name>gateway-service-weather</name>
    <description>A sample extension to the gateway for a weather REST API.</description>

    <licenses>
        <license>
            <name>The Apache Software License, Version 2.0</name>
            <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
            <distribution>repo</distribution>
        </license>
    </licenses>

    <dependencies>
        <dependency>
            <groupId>${gateway-group}</groupId>
            <artifactId>gateway-spi</artifactId>
        </dependency>
        <dependency>
            <groupId>${gateway-group}</groupId>
            <artifactId>gateway-provider-rewrite</artifactId>
        </dependency>

        ... Test Dependencies ...

    </dependencies>

</project>
```

#### Service Definition Files ####

As of release 0.6.0, the gateway now also supports a declarative way of plugging-in a new Service. A Service can be defined with a
combination of two files, these are:

    service.xml
    rewrite.xml

The rewrite.xml file contains the rewrite rules as defined in other sections of this guide, and the service.xml file contains
the various routes (paths) to be provided by the Service and the rewrite rule bindings to those paths. This will be described in further detail
in this section.

While the service.xml file is absolutely required, the rewrite.xml file in theory is optional (though it is highly unlikely that no rewrite rules
are needed).

To add a new service, simply add a service.xml and rewrite.xml file in an appropriate directory (see #[Service Definition Directory Structure])
in the module gateway-service-definitions to make the new service part of the Knox build.

##### service.xml #####

Below is a sample of a very simple service.xml file, taking the same weather api example.

```xml
<service role="WEATHER" name="weather" version="0.1.0">
    <routes>
        <route path="/weather/**?**"/>
    </routes>
</service>

```

**service**
: The root tag is 'service' that has the three required attributes: *role*, *name* and *version*. These three values disambiguate this
service definition from others. To ensure the exact same service definition is being used in a topology file, all values should be specified,

```xml
<topology>
    <gateway>
        ....
    </gateway>
    <service>
        <role>WEATHER</role>
        <name>weather</name>
        <version>0.1.0</version>
        <url>http://api.openweathermap.org/data</url>
        <dispatch>
            <contributor-name>custom-client</contributor-name>
            <ha-contributor-name>ha-client</ha-contributor-name>
            <classname>org.apache.knox.gateway.dispatch.PassAllHeadersDispatch</classname>
            <ha-classname></ha-classname>
            <http-client-factory></http-client-factory>
            <use-two-way-ssl>false</use-two-way-ssl>
        </dispatch>
    </service>
    ....
</topology>
```

If only *role* is specified in the topology file (the only required element other than *url*) then the first service definition of that role found
will be used with the highest version of that role and name. Similarly if only the *version* is omitted from the topology specification of the service,
the service definition of the highest version will be used. It is therefore important to specify a version for a service if it is desired that
a topology be locked down to a specific version of a service.

The optional `dispatch` element can be used to override the dispatch specified in service.xml file for the service, this can be useful in cases where you want a specific topology to override the dispatch, which is useful for topology based federation from one Knox instance to another. `dispatch\classname` is the most commonly used option, but other options are available if one wants more fine grained control over topology dispatch override. 


**routes**
: Wrapper element for one or more routes.

**route**
: A route specifies the *path* that the service is routing as well as any rewrite bindings or policy bindings. Another child element that may be used here
is a *dispatch* element.

**rewrite**
: A rewrite rule or function that is to be applied to the path. A rewrite element contains a *apply* attribute that references the rewrite function or rule
by name. Along with the *apply* attribute, a *to* attribute must be used. The *to* specifies what part of the request or response to rewrite.
The valid values for the *to* attribute are:

- request.url
- request.headers
- request.cookies
- request.body
- response.headers
- response.cookies
- response.body

Below is an example of a snippet from the WebHDFS service definition

```xml
    <route path="/webhdfs/v1/**?**">
        <rewrite apply="WEBHDFS/webhdfs/inbound/namenode/file" to="request.url"/>
        <rewrite apply="WEBHDFS/webhdfs/outbound/namenode/headers" to="response.headers"/>
    </route>
```

**dispatch**
: The dispatch element can be used to plug-in a custom dispatch class. The interface for Dispatch can be found in the module
gateway-spi, org.apache.knox.gateway.dispatch.Dispatch.

This element can be used at the service level (i.e. as a child of the service tag) or at the route level. A dispatch specified
at the route level takes precedence over a dispatch specified at the service level. By default the dispatch used is
org.apache.knox.gateway.dispatch.DefaultDispatch.

The dispatch tag has four attributes that can be specified.

*contributor-name* : This attribute can be used to specify a deployment contributor to be invoked for a custom dispatch.

*classname* : This attribute can be used to specify a custom dispatch class.

*ha-contributor-name* : This attribute can be used to specify a deployment contributor to be invoked for custom HA dispatch functionality.

*ha-classname* : This attribute can be used to specify a custom dispatch class with HA functionality.

Only one of contributor-name or classname should be specified and one of ha-contributor-name or ha-classname should be specified.

If providing a custom dispatch, either a jar should be provided, see #[Class Path] or a #[Maven Module] should be created.

Check out #[ConfigurableDispatch] about configurable dispatch type. 

**policies**
: This is a wrapper tag for *policy* elements and can be a child of the *service* tag or the *route* tag. Once again, just like with
dispatch, the route level policies defined override the ones at the service level.

This element can contain one or more *policy* elements. The order of the *policy* elements is important as that will be the
order of execution.

**policy**
: At this time the policy element just has two attributes, *role* and *name*. These are used to execute a deployment contributor
by that role and name. Therefore new policies must be added by using the deployment contributor mechanism.

For example,

```xml
<service role="FOO" name="foo" version="1.6.0">
    <policies>
        <policy role="webappsec"/>
        <policy role="authentication"/>
        <policy role="rewrite"/>
        <policy role="identity-assertion"/>
        <policy role="authorization"/>
    </policies>
    <routes>
        <route path="/foo/?**">
            <rewrite apply="FOO/foo/inbound" to="request.url"/>
            <policies>
                <policy role="webappsec"/>
                <policy role="federation"/>
                <policy role="identity-assertion"/>
                <policy role="authorization"/>
                <policy role="rewrite"/>
            </policies>
            <dispatch contributor-name="http-client" />
        </route>
    </routes>
    <dispatch contributor-name="custom-client" ha-contributor-name="ha-client"/>
</service>
```

##### ConfigurableDispatch ####

The `ConfigurableDispatch` allows service definition writers to:

 - exclude certain header(s) from the outbound HTTP request
 - exclude certain header(s) from the outbound HTTP response
 - declares whether parameters should be URL-encoded or not

This dispatch type can be set in `service.xml` as follows:

```
<dispatch classname="org.apache.knox.gateway.dispatch.ConfigurableDispatch" ha-classname="org.apache.knox.gateway.ha.dispatch.ConfigurableHADispatch">
  <param>
    <name>requestExcludeHeaders</name>
    <value>Authorization,Content-Length</value>
  </param>
  <param>
    <name>responseExcludeHeaders</name>
    <value>SET-COOKIE,WWW-AUTHENTICATE</value>
  </param>
  <param>
    <name>removeUrlEncoding</name>
    <value>true</value>
  </param>
</dispatch>
```

The default values of these parameters are:

 - `requestExcludeHeaders` = `Host,Authorization,Content-Length,Transfer-Encoding`
 - `responseExcludeHeaders` = `SET-COOKIE,WWW-AUTHENTICATE`
 - `removeUrlEncoding` = `false`

The `responseExcludeHeaders` handling allows excluding only certain directives of the `SET-COOKIE` HTTP header. The following sample shows how to ecxlude only the `HttpOnly` directive from `SET-COOKIE` and the `WWW-AUTHENTICATE` header entirely in the routbound response HTTP header:

```
<dispatch classname="org.apache.knox.gateway.dispatch.ConfigurableDispatch" ha-classname="org.apache.knox.gateway.ha.dispatch.ConfigurableHADispatch">>
  <param>
    <name>responseExcludeHeaders</name>
    <value>WWW-AUTHENTICATE,SET-COOKIE:HttpOnly</value>
  </param>
</dispatch>
```

##### rewrite.xml #####

The rewrite.xml file that accompanies the service.xml file follows the same rules as described in the section #[Rewrite Provider].

#### Service Definition Directory Structure ####

On installation of the Knox gateway, the following directory structure can be found under ${GATEWAY_HOME}/data. This
is a mirror of the directories and files under the module gateway-service-definitions.

    services
        |______ service name
                        |______ version
                                    |______service.xml
                                    |______rewrite.xml


For example,

    services
        |______ webhdfs
                   |______ 2.4.0
                             |______service.xml
                             |______rewrite.xml


To test out a new service, you can just add the appropriate files (service.xml and rewrite.xml) in a directory under
${GATEWAY_HOME}/data/services. If you want to make the service contribution to the Knox build, they files need to go
in the gateway-service-definitions module.

#### Service Definition Runtime Behavior ####

The runtime artifacts as well as the behavior does not change whether the service is plugged in via the deployment
descriptors or through a service.xml file.

#### Custom Dispatch Dependency Injection ####

When writing a custom dispatch class, one often needs configuration or gateway services. A lightweight dependency injection
system is used that can inject instances of classes or primitives available in the filter configuration's init params or
as a servlet context attribute.

Details of this can be found in the module gateway-util-configinjector and also an example use of it is in the class
org.apache.knox.gateway.dispatch.DefaultDispatch. Look at the following method for example:

```java
 @Configure
   protected void setReplayBufferSize(@Default("8") int size) {
      replayBufferSize = size;
   }
```

### Service Discovery ###

Knox supports the ability to dynamically determine endpoint URLs in topologies for supported Hadoop services.
_This functionality is currently only supported for Ambari-managed Hadoop clusters._
There are a number of these services, which are officially supported, but this set can be extended by modifying the
source or speciyfing external configuration.


#### Service URL Definitions ####

The service discovery system determines service URLs by processing mappings of Hadoop service configuration properties and
corresponding URL templates. The knowledge about converting these arbitrary service configuration properties into correct
service endpoint URLs is defined in a configuration file internal to the Ambari service discovery module.


##### Configuration Details #####

This internal configuration file ( **ambari-service-discovery-url-mappings.xml** ) in the **gateway-discovery-ambari** module is used to specify a
URL template and the associated configuration properties to populate it. A limited degree of conditional logic is supported to accommodate
things like _http_ vs _https_ configurations.

The simplest way to describe its contents will be by examples.

**Example 1**

The simplest example of one such mapping involves a service component for which there is a single configuration property which specifies
the complete URL

    <!-- This is the service mapping declaration. The name must match what is specified as a Knox topology service role -->
    <service name="OOZIE">
	
      <!-- This is the URL pattern with palceholder(s) for values provided by properties -->
      <url-pattern>{OOZIE_URL}</url-pattern>
	
      <properties>
	
        <!-- This is a property, which in this simple case, matches a template placeholder -->
        <property name="OOZIE_URL">
	
          <!-- This is the component whose configuration will be used to lookup the value of the subsequent config property name -->
          <component>OOZIE_SERVER</component>
	  
          <!-- This is the name of the component config property whose value should be assigned to the OOZIE_URL value -->
          <config-property>oozie.base.url</config-property>
	  
        </property>
      </properties>
    </service>

The _OOZIE_SERVER_ component configuration is **oozie-site**
If **oozie-site.xml** has the property named **oozie.base.url** with the value http://ooziehost:11000, then the resulting URL for the _OOZIE_
service will be http://ooziehost:11000


**Example 2**

A slightly more complicated example involves a service component for which the complete URL is not described by a single detail, but
rather multiple endpoint URL details

    <service name="WEBHCAT">
      <url-pattern>http://{HOST}:{PORT}/templeton</url-pattern>
      <properties>
        <property name="HOST">
          <component>WEBHCAT_SERVER</component> 
		  
          <!-- This tells discovery to get the hostname for the WEBHCAT_SERVER component from Ambari -->
          <hostname/>
		  
        </property>
        <property name="PORT">
          <component>WEBHCAT_SERVER</component>
		  
          <!-- This is the name of the component config property whose value should be assigned to -->
		  <!-- the PORT value -->
          <config-property>templeton.port</config-property>
		  
        </property>
      </properties>
    </service>


**Example 3**

An even more complicated example involves a service for which _HTTPS_ is supported, and which employs the limited conditional
logic support

    <service name="ATLAS">
      <url-pattern>{SCHEME}://{HOST}:{PORT}</url-pattern>
      <properties>
	   
        <!-- Property for getting the ATLAS_SERVER component hostname from Ambari -->
        <property name="HOST">
          <component>ATLAS_SERVER</component>
          <hostname/>
        </property>
		
        <!-- Property for capturing whether TLS is enabled or not; This is not a template placeholder property -->
        <property name="TLS_ENABLED">
          <component>ATLAS_SERVER</component>
          <config-property>atlas.enableTLS</config-property>
        </property>
        <!-- Property for getting the http port ; also NOT a template placeholder property -->
        <property name="HTTP_PORT">
          <component>ATLAS_SERVER</component>
          <config-property>atlas.server.http.port</config-property>
        </property>
	
        <!-- Property for getting the https port ; also NOT a template placeholder property -->
        <property name="HTTPS_PORT">
          <component>ATLAS_SERVER</component>
          <config-property>atlas.server.https.port</config-property>
        </property>
	
        <!-- Template placeholder property, dependent on the TLS_ENABLED property value -->
        <property name="PORT">
          <config-property>
            <if property="TLS_ENABLED" value="true">
              <then>HTTPS_PORT</then>
              <else>HTTP_PORT</else>
            </if>
          </config-property>
        </property>
	
        <!-- Template placeholder property, dependent on the TLS_ENABLED property value -->
        <property name="SCHEME">
          <config-property>
            <if property="TLS_ENABLED" value="true">
              <then>https</then>
              <else>http</else>
            </if>
          </config-property>
        </property>
      </properties>
    </service>


##### External Configuration #####
The internal configuration for URL construction can be overridden or augmented by way of a configuration file in the gateway
configuration directory, or an alternative file specified by a Java system property. This mechanism is useful for developing
support for new services, for custom solutions, or any scenario for which rebuilding Knox is not desirable.

The default file, for which Knox will search first, is **_{GATEWAY_HOME}_/conf/ambari-discovery-url-mappings.xml**

If Knox doesn't find that file, it will check for a Java system property named **org.apache.gateway.topology.discovery.ambari.config**,
whose value is the fully-qualified path to an XML file. This file's contents must adhere to the format outlined above.

If this configuration exists, Knox will apply it as if it were part of the internal configuration.

**Example**

If Apache Solr weren't supported, then it could be added by creating the following definition in
**_{GATEWAY_HOME}_/conf/ambari-discovery-url-mappings.xml** :

    <?xml version="1.0" encoding="utf-8"?>
    <service-discovery-url-mappings>
      <service name="SOLR">
        <url-pattern>http://{HOST}:{PORT}</url-pattern>
        <properties>
          <property name="HOST">
            <component>INFRA_SOLR</component>
            <hostname/>
          </property>
          <property name="PORT">
            <component>INFRA_SOLR</component>
            <config-property>infra_solr_port</config-property>
          </property>
        </properties>
      </service>
    </service-discovery-url-mappings>


**_N.B. Knox must be restarted for changes to this external configuration to be applied._**


#### Component Configuration Mapping ####

To support URL construction from service configuration files, Ambari service discovery requires knowledge of the service component types and
their respective relationships to configuration types. This knowledge is defined in a configuration file internal to the Ambari service discovery
module.

##### Configuration Details #####

This internal configuration file ( **ambari-service-discovery-component-config-mapping.properties** ) in the **gateway-discovery-ambari** module is
used to define the mapping of Hadoop service component names to the configuration type from which Knox will lookup property values.

**Example**

    NAMENODE=hdfs-site
    RESOURCEMANAGER=yarn-site
    HISTORYSERVER=mapred-site
    OOZIE_SERVER=oozie-site
    HIVE_SERVER=hive-site
    WEBHCAT_SERVER=webhcat-site

##### External Configuration #####

The internal configuration for component configuration mappings can be overridden or augmented by way of a configuration file in the gateway
configuration directory, or an alternative file specified by a Java system property. This mechanism is useful for developing support for new services,
for custom solutions, or any scenario for which rebuilding Knox is not desirable.

The default file, for which Knox will search first, is **_{GATEWAY_HOME}_/conf/ambari-discovery-component-config.properties**
If Knox doesn't find that file, it will check for a Java system property named **org.apache.knox.gateway.topology.discovery.ambari.component.mapping**,
whose value is the fully-qualified path to a properties file. This file's contents must adhere to the format outlined above.

If this configuration exists, Knox will apply it as if it were part of the internal configuration.

**Example**

Following the aforementioned SOLR example, Knox needs to know in which configuration file to find the _INFRA_SOLR_ component configuration property, so
the following property must be defined in **_{GATEWAY_HOME}_/conf/ambari-discovery-component-config.properties** :

    
    INFRA_SOLR=infra-solr-env
	

This tells Knox to look for the **infra_solr_port** property in the **infra-solr-env** configuration.

**_N.B. Knox must be restarted for changes to this external configuration to be applied._**



### Validator ###
Apache Knox provides preauth federation authentication where  
Knox supports two built-in validators for verifying incoming requests. In this section, we describe how to write a custom validator for this scenario. The provided validators include: 

*  *preauth.default.validation:* This default behavior does not perform any validation check. All requests will pass.
*  *preauth.ip.validation* : This validation checks if a request is originated from an IP address which is configured in Knox service through property *preauth.ip.addresses*.

However, these built-in validation choices may not fulfill the internal requirments of some organization. Therefore, Knox supports (since 0.12) a pluggble framework where anyone can include a custom validator. 

In essence, a user can add a custom validator by following these  steps. The corresponding code examples are incorporated after that:
 
1. Create a separate Java package (e.g. com.company.knox.validator) in a new or existing Maven project.
2. Create a new class (e.g. *CustomValidator*) that implements *org.apache.knox.gateway.preauth.filter.PreAuthValidator*.
3. The class should implement the method *String getName()* that may returns a string constant. The step-9  will need this user defined string constant.
4. The class should implement the method *boolean validate(HttpServletRequest httpRequest, FilterConfig filterConfig)*. This is the key method which will validate the request based on 'httpRequest' and 'filterConfig'. In most common cases, user may need to use HTTP headers value to validate. For example, client can get a token from an authentication service and pass it as HTTP header. This validate method needs to extract that header and verify the token. In some instance, the server may need to contact the same authentication service to validate.
5. Create a text file src/resources/META-INF/services and add fully qualified name of your custom validator class (e.g. *com.company.knox.validator.CustomValidator*).
6. You may need to include the packages "org.apache.knox.gateway-provider-security-preauth"  of version 0.12+ and  "javax.servlet.javax.servlet-api" of version 3.1.0+ in pom.xml.
7. Build your custom jar.
8. Deploy the jar in $GATEWAY_HOME/ext directory.
9. Add/modify a parameter called *preauth.validation.method* with the name of validator used in step #3. Optionally, you may add any new parameter that may be required only for your CustomValidator.

**Validator Class (Step 2-4)** 

	package com.company.knox.validator;
	
	import org.apache.knox.gateway.preauth.filter.PreAuthValidationException;
	import org.apache.knox.gateway.preauth.filter.PreAuthValidator;
	import com.google.common.base.Strings;
	
	import javax.servlet.FilterConfig;
	import javax.servlet.http.HttpServletRequest;
	
	public class CustomValidator extends PreAuthValidator {
	  //Any string constant value should work for these 3 variables
	  //This string will be used in 'services' file.
	  public static final String CUSTOM_VALIDATOR_NAME = "fooValidator"; 
	  //Optional: User may want to pass soemthign through HTTP header. (per client request)
	  public static final String CUSTOM_TOKEN_HEADER_NAME = "foo_claim";
	   
	  
	  /**
	   * @param httpRequest
	   * @param filterConfig
	   * @return
	   * @throws PreAuthValidationException
	   */
	  @Override
	  public boolean validate(HttpServletRequest httpRequest, FilterConfig filterConfig) throws PreAuthValidationException {
	    String claimToken = httpRequest.getHeader(CUSTOM_TOKEN_HEADER_NAME);
	    if (!Strings.isNullOrEmpty(claimToken)) {
	      return checkCustomeToken(claimToken); //to be implemented
	    } else {
	      log.warn("Claim token was empty for header name '" + CUSTOM_TOKEN_HEADER_NAME + "'");
	      return false;
	    }
	  }
	
	  /**
	   * Define unique validator name
	   *
	   * @return
	   */
	  @Override
	  public String getName() {
	    return CUSTOM_VALIDATOR_NAME;
	  }
	}
	

**META-INF/services contents (Step-5)**

`com.company.knox.validator.CustomValidator`


**POM file (Step-6)**

    <dependency>
        <groupId>javax.servlet</groupId>
        <artifactId>javax.servlet-api</artifactId>
        <scope>provided</scope>
    </dependency>

    <dependency>
        <groupId>org.apache.knox</groupId>
        <artifactId>gateway-test-utils</artifactId>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.apache.knox</groupId>
        <artifactId>gateway-provider-security-preauth</artifactId>
        <scope>provided</scope>
    </dependency>


**Deploy Custom Jar (Step-7-8)**

Build the jar (e.g. customValidation.jar) using 'mvn clean package'
`cp customValidation.jar $GATEWAY_HOME/ext/`

**Topology Config (Step-9)**


    <provider>
        <role>federation</role>
        <name>HeaderPreAuth</name>
        <enabled>true</enabled>
        <param><name>preauth.validation.method</name>
        <!--Same as CustomeValidator.CUSTOM_VALIDATOR_NAME   ->
        <value>fooValidator</value></param>
    </provider>

### Providers ###

```java
public interface ProviderDeploymentContributor {
  String getRole();
  String getName();

  void initializeContribution( DeploymentContext context );
  void contributeProvider( DeploymentContext context, Provider provider );
  void contributeFilter(
      DeploymentContext context,
      Provider provider,
      Service service,
      ResourceDescriptor resource,
      List<FilterParamDescriptor> params );

  void finalizeContribution( DeploymentContext context );
}
```

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.apache.knox</groupId>
        <artifactId>gateway</artifactId>
        <version>2.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>gateway-provider-security-authn-sample</artifactId>
    <name>gateway-provider-security-authn-sample</name>
    <description>A simple sample authorization provider.</description>

    <licenses>
        <license>
            <name>The Apache Software License, Version 2.0</name>
            <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
            <distribution>repo</distribution>
        </license>
    </licenses>

    <dependencies>
        <dependency>
            <groupId>${gateway-group}</groupId>
            <artifactId>gateway-spi</artifactId>
        </dependency>
    </dependencies>

</project>
```

### Deployment Context ###

```java
package org.apache.knox.gateway.deploy;

import ...

public interface DeploymentContext {

  GatewayConfig getGatewayConfig();

  Topology getTopology();

  WebArchive getWebArchive();

  WebAppDescriptor getWebAppDescriptor();

  GatewayDescriptor getGatewayDescriptor();

  void contributeFilter(
      Service service,
      ResourceDescriptor resource,
      String role,
      String name,
      List<FilterParamDescriptor> params );

  void addDescriptor( String name, Object descriptor );

  <T> T getDescriptor( String name );

}
```

```java
public class Topology {

  public URI getUri() {...}
  public void setUri( URI uri ) {...}

  public String getName() {...}
  public void setName( String name ) {...}

  public long getTimestamp() {...}
  public void setTimestamp( long timestamp ) {...}

  public Collection<Service> getServices() {...}
  public Service getService( String role, String name ) {...}
  public void addService( Service service ) {...}

  public Collection<Provider> getProviders() {...}
  public Provider getProvider( String role, String name ) {...}
  public void addProvider( Provider provider ) {...}
}
```

```java
public interface GatewayDescriptor {
  List<GatewayParamDescriptor> params();
  GatewayParamDescriptor addParam();
  GatewayParamDescriptor createParam();
  void addParam( GatewayParamDescriptor param );
  void addParams( List<GatewayParamDescriptor> params );

  List<ResourceDescriptor> resources();
  ResourceDescriptor addResource();
  ResourceDescriptor createResource();
  void addResource( ResourceDescriptor resource );
}
```

### Gateway Services ###

TODO - Describe the service registry and other global services.

## Standard Providers ##

### Rewrite Provider ###

gateway-provider-rewrite
org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor

```xml
<rules>
  <rule
      dir="IN"
      name="WEATHER/openweathermap/inbound/versioned/file"
      pattern="*://*:*/**/weather/{version}?{**}">
    <rewrite template="{$serviceUrl[WEATHER]}/{version}/weather?{**}"/>
  </rule>
</rules>
```

```xml
<rules>
  <filter name="WEBHBASE/webhbase/status/outbound">
    <content type="*/json">
      <apply path="$[LiveNodes][*][name]" rule="WEBHBASE/webhbase/address/outbound"/>
    </content>
    <content type="*/xml">
      <apply path="/ClusterStatus/LiveNodes/Node/@name" rule="WEBHBASE/webhbase/address/outbound"/>
    </content>
  </filter>
</rules>
```

```java
@Test
public void testDevGuideSample() throws Exception {
  URI inputUri, outputUri;
  Matcher<Void> matcher;
  Matcher<Void>.Match match;
  Template input, pattern, template;

  inputUri = new URI( "http://sample-host:8443/gateway/topology/weather/2.5?q=Palo+Alto" );

  input = Parser.parse( inputUri.toString() );
  pattern = Parser.parse( "*://*:*/**/weather/{version}?{**}" );
  template = Parser.parse( "http://api.openweathermap.org/data/{version}/weather?{**}" );

  matcher = new Matcher<Void>();
  matcher.add( pattern, null );
  match = matcher.match( input );

  outputUri = Expander.expand( template, match.getParams(), null );

  assertThat(
      outputUri.toString(),
      is( "http://api.openweathermap.org/data/2.5/weather?q=Palo+Alto" ) );
}
```

```java
@Test
public void testDevGuideSampleWithEvaluator() throws Exception {
  URI inputUri, outputUri;
  Matcher<Void> matcher;
  Matcher<Void>.Match match;
  Template input, pattern, template;
  Evaluator evaluator;

  inputUri = new URI( "http://sample-host:8443/gateway/topology/weather/2.5?q=Palo+Alto" );
  input = Parser.parse( inputUri.toString() );

  pattern = Parser.parse( "*://*:*/**/weather/{version}?{**}" );
  template = Parser.parse( "{$serviceUrl[WEATHER]}/{version}/weather?{**}" );

  matcher = new Matcher<Void>();
  matcher.add( pattern, null );
  match = matcher.match( input );

  evaluator = new Evaluator() {
    @Override
    public List<String> evaluate( String function, List<String> parameters ) {
      return Arrays.asList( "http://api.openweathermap.org/data" );
    }
  };

  outputUri = Expander.expand( template, match.getParams(), evaluator );

  assertThat(
      outputUri.toString(),
      is( "http://api.openweathermap.org/data/2.5/weather?q=Palo+Alto" ) );
}
```

#### Rewrite Filters ####
TODO - Cover the supported content types.
TODO - Provide a XML and JSON "properties" example where one NVP is modified based on value of another name.

```xml
<rules>
  <filter name="WEBHBASE/webhbase/regions/outbound">
    <content type="*/json">
      <apply path="$[Region][*][location]" rule="WEBHBASE/webhbase/address/outbound"/>
    </content>
    <content type="*/xml">
      <apply path="/TableInfo/Region/@location" rule="WEBHBASE/webhbase/address/outbound"/>
    </content>
  </filter>
</rules>
```

```xml
<gateway>
  ...
  <resource>
    <role>WEBHBASE</role>
    <pattern>/hbase/*/regions?**</pattern>
    ...
    <filter>
      <role>rewrite</role>
      <name>url-rewrite</name>
      <class>org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletFilter</class>
      <param>
        <name>response.body</name>
        <value>WEBHBASE/webhbase/regions/outbound</value>
      </param>
    </filter>
    ...
  </resource>
  ...
</gateway>
```

HBaseDeploymentContributor
```java
    params = new ArrayList<FilterParamDescriptor>();
    params.add( regionResource.createFilterParam().name( "response.body" ).value( "WEBHBASE/webhbase/regions/outbound" ) );
    addRewriteFilter( context, service, regionResource, params );
```

#### Rewrite Functions ####
TODO - Provide an lowercase function as an example.

```xml
<rules>
  <functions>
    <hostmap config="/WEB-INF/hostmap.txt"/>
  </functions>
  ...
</rules>
```

#### Rewrite Steps ####
TODO - Provide an lowercase step as an example.

```xml
<rules>
  <rule dir="OUT" name="WEBHDFS/webhdfs/outbound/namenode/headers/location">
    <match pattern="{scheme}://{host}:{port}/{path=**}?{**}"/>
    <rewrite template="{gateway.url}/webhdfs/data/v1/{path=**}?{scheme}?host={$hostmap(host)}?{port}?{**}"/>
    <encrypt-query/>
  </rule>
</rules>
```

### Identity Assertion Provider ###
Adding a new identity assertion provider is as simple as extending the AbstractIdentityAsserterDeploymentContributor and the CommonIdentityAssertionFilter from the gateway-provider-identity-assertion-common module to initialize any specific configuration from filter init params and implement two methods:

1. String mapUserPrincipal(String principalName);
2. String[] mapGroupPrincipals(String principalName, Subject subject);

To implement a simple toUpper or toLower identity assertion provider:

```java
package org.apache.knox.gateway.identityasserter.caseshifter.filter;

import org.apache.knox.gateway.identityasserter.common.filter.AbstractIdentityAsserterDeploymentContributor;

public class CaseShifterIdentityAsserterDeploymentContributor extends AbstractIdentityAsserterDeploymentContributor {

  @Override
  public String getName() {
    return "CaseShifter";
  }

  protected String getFilterClassname() {
    return CaseShifterIdentityAssertionFilter.class.getName();
  }
}
```
We merely need to provide the provider name for use in the topology and the filter classname for the contributor to add to the filter chain.

For the identity assertion filter itself it is just a matter of extension and the implementation of the two methods described earlier:

```java
package org.apache.knox.gateway.identityasserter.caseshifter.filter;

import javax.security.auth.Subject;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import org.apache.knox.gateway.identityasserter.common.filter.CommonIdentityAssertionFilter;

public class CaseShifterIdentityAssertionFilter extends CommonIdentityAssertionFilter {
  private boolean toUpper = false;
  
  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    String upper = filterConfig.getInitParameter("caseshift.upper");
    if ("true".equals(upper)) {
      toUpper = true;
    }
    else {
      toUpper = false;
    }
  }

  @Override
  public String[] mapGroupPrincipals(String mappedPrincipalName, Subject subject) {
    return null;
  }

  @Override
  public String mapUserPrincipal(String principalName) {
    if (toUpper) {
      principalName = principalName.toUpperCase();
    }
    else {
      principalName = principalName.toLowerCase();
    }
    return principalName;
  }
}
```

Note that the above: 

1. looks for specific filter init parameters for configuration of whether to convert to upper or to lower case
2. it no-ops the mapGroupPrincipals so that it returns null. This indicates that there are no changes needed to the groups contained within the Subject. If there are groups then they should be continued to flow through the system unchanged. This is actually the same implementation as the base class and is therefore not required to be overridden. We include it here for illustration.
3. based upon the configuration interrogated in the init method the principalName is convert to either upper or lower case.

That is the extent of what is needed to implement a new identity assertion provider module.

### Jersey Provider ###
TODO

### KnoxSSO Integration ###

```plantuml
@startuml
!include knoxsso_integration.puml
@enduml
```

### Health Monitoring API ###

```plantuml
@startuml
!include knox_monitoring_api.puml
@enduml
```

## Auditing ##

```java
public class AuditingSample {

  private static Auditor AUDITOR = AuditServiceFactory.getAuditService().getAuditor(
      "sample-channel", "sample-service", "sample-component" );

  public void sampleMethod() {
      ...
      AUDITOR.audit( Action.AUTHORIZATION, sourceUrl, ResourceType.URI, ActionOutcome.SUCCESS );
      ...
  }

}
```

## Logging ##

```java
@Messages( logger = "org.apache.project.module" )
public interface CustomMessages {

  @Message( level = MessageLevel.FATAL, text = "Failed to parse command line: {0}" )
  void failedToParseCommandLine( @StackTrace( level = MessageLevel.DEBUG ) ParseException e );

}
```

```java
public class CustomLoggingSample {

  private static GatewayMessages MSG = MessagesFactory.get( GatewayMessages.class );

  public void sampleMethod() {
    ...
    MSG.failedToParseCommandLine( e );
    ...
  }

}
```

## Internationalization ##

```java
@Resources
public interface CustomResources {

  @Resource( text = "Apache Hadoop Gateway {0} ({1})" )
  String gatewayVersionMessage( String version, String hash );

}
```

```java
public class CustomResourceSample {

  private static GatewayResources RES = ResourcesFactory.get( GatewayResources.class );

  public void sampleMethod() {
    ...
    String s = RES.gatewayVersionMessage( "0.0.0", "XXXXXXX" ) );
    ...
  }

}
```

## Admin UI  ##

<<admin-ui.md>>

<<../../common/footer.md>>



