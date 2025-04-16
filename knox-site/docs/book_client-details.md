<!---
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
--->

## Client Details ##
The KnoxShell release artifact provides a small footprint client environment that removes all unnecessary server dependencies, configuration, binary scripts, etc. It is comprised a couple different things that empower different sorts of users.

* A set of SDK type classes for providing access to Hadoop resources over HTTP
* A Groovy based DSL for scripting access to Hadoop resources based on the underlying SDK classes
* A KnoxShell Token based Sessions to provide a CLI SSO session for executing multiple scripts

The following sections provide an overview and quickstart for the KnoxShell.

### Client Quickstart ###
The following installation and setup instructions should get you started with using the KnoxShell very quickly.

1. Download a knoxshell-x.x.x.zip or tar file and unzip it in your preferred location `{GATEWAY_CLIENT_HOME}`

        home:knoxshell-0.12.0 larry$ ls -l
        total 296
        -rw-r--r--@  1 larry  staff  71714 Mar 14 14:06 LICENSE
        -rw-r--r--@  1 larry  staff    164 Mar 14 14:06 NOTICE
        -rw-r--r--@  1 larry  staff  71714 Mar 15 20:04 README
        drwxr-xr-x@ 12 larry  staff    408 Mar 15 21:24 bin
        drwxr--r--@  3 larry  staff    102 Mar 14 14:06 conf
        drwxr-xr-x+  3 larry  staff    102 Mar 15 12:41 logs
        drwxr-xr-x@ 18 larry  staff    612 Mar 14 14:18 samples
        
    |Directory    | Description |
    |-------------|-------------|
    |bin          |contains the main knoxshell jar and related shell scripts|
    |conf         |only contains log4j config|
    |logs         |contains the knoxshell.log file|
    |samples      |has numerous examples to help you get started|

2. cd `{GATEWAY_CLIENT_HOME}`
3. Get/setup truststore for the target Knox instance or fronting load balancer
    - As of 1.3.0 release you may use the KnoxShell command buildTrustStore to create the truststore. `
    - if you have access to the server you may also use the command `knoxcli.sh export-cert --type JKS`
    - copy the resulting `gateway-client-identity.jks` to your user home directory
4. Execute the an example script from the `{GATEWAY_CLIENT_HOME}/samples` directory - for instance:
    - `bin/knoxshell.sh samples/ExampleWebHdfsLs.groovy`
    
            home:knoxshell-0.12.0 larry$ bin/knoxshell.sh samples/ExampleWebHdfsLs.groovy
            Enter username: guest
            Enter password:
            [app-logs, apps, mapred, mr-history, tmp, user]

At this point, you should have seen something similar to the above output - probably with different directories listed. You should get the idea from the above. Take a look at the sample that we ran above:

    import groovy.json.JsonSlurper
    import org.apache.knox.gateway.shell.Hadoop
    import org.apache.knox.gateway.shell.hdfs.Hdfs

    import org.apache.knox.gateway.shell.Credentials

    gateway = "https://localhost:8443/gateway/sandbox"

    credentials = new Credentials()
    credentials.add("ClearInput", "Enter username: ", "user")
                    .add("HiddenInput", "Enter pas" + "sword: ", "pass")
    credentials.collect()

    username = credentials.get("user").string()
    pass = credentials.get("pass").string()

    session = Hadoop.login( gateway, username, pass )

    text = Hdfs.ls( session ).dir( "/" ).now().string
    json = (new JsonSlurper()).parseText( text )
    println json.FileStatuses.FileStatus.pathSuffix
    session.shutdown()

Some things to note about this sample:

1. The gateway URL is hardcoded
    - Alternatives would be passing it as an argument to the script, using an environment variable or prompting for it with a ClearInput credential collector
2. Credential collectors are used to gather credentials or other input from various sources. In this sample the HiddenInput and ClearInput collectors prompt the user for the input with the provided prompt text and the values are acquired by a subsequent get call with the provided name value.
3. The Hadoop.login method establishes a login session of sorts which will need to be provided to the various API classes as an argument.
4. The response text is easily retrieved as a string and can be parsed by the JsonSlurper or whatever you like

### Build Truststore for use with KnoxShell Client Applications ###
The buildTrustStore command in KnoxShell allows remote clients that only have access to the KnoxShell install to build a local trustore from the server they intend to use. It should be understood that this mechanism is less secure than getting the cert directly from the Knox CLI - as a MITM could present you with a certificate that will be trusted when doing this remotely.

   buildTrustStore <knox-gateway-url> - downloads the given gateway server's public certificate and builds a trust store to be used by KnoxShell
        example: knoxshell.sh buildTrustStore https://localhost:8443/

### Client Token Sessions ###
Building on the Quickstart above we will drill into some of the token session details here and walk through another sample.

Unlike the quickstart, token sessions require the server to be configured in specific ways to allow the use of token sessions/federation.

#### Server Setup ####
1. KnoxToken service should be added to your `sandbox` descriptor - see the [KnoxToken Configuration] (#KnoxToken+Configuration)

        "services": [
          {
            "name": "KNOXTOKEN",
            "params": {
              "knox.token.ttl": "36000000",
		      "knox.token.audiences": "tokenbased",
		      "knox.token.target.url": "https://localhost:8443/gateway/tokenbased"
            }
          }
        ]

2. Include the following in the provider configuration referenced from the `tokenbased` descriptor to accept tokens as federation tokens for access to exposed resources with the [JWTProvider](#JWT+Provider)

        "providers": [
          {
            "role": "federation",
            "name": "JWTProvider",
            "enabled": "true",
            "params": {
    		  "knox.token.audiences": "tokenbased"
            }
          }
        ]


3. Use the KnoxShell token commands to establish and manage your session
    - bin/knoxshell.sh init https://localhost:8443/gateway/sandbox to acquire a token and cache in user home directory
    - bin/knoxshell.sh list to display the details of the cached token, the expiration time and optionally the target url
    - bin/knoxshell destroy to remove the cached session token and terminate the session

4. Execute a script that can take advantage of the token credential collector and target URL

        import groovy.json.JsonSlurper
        import java.util.HashMap
        import java.util.Map
        import org.apache.knox.gateway.shell.Credentials
        import org.apache.knox.gateway.shell.KnoxSession
        import org.apache.knox.gateway.shell.hdfs.Hdfs

        credentials = new Credentials()
        credentials.add("KnoxToken", "none: ", "token")
        credentials.collect()

        token = credentials.get("token").string()

        gateway = System.getenv("KNOXSHELL_TOPOLOGY_URL")
        if (gateway == null || gateway.equals("")) {
          gateway = credentials.get("token").getTargetUrl()
        }

        println ""
        println "*****************************GATEWAY INSTANCE**********************************"
        println gateway
        println "*******************************************************************************"
        println ""

        headers = new HashMap()
        headers.put("Authorization", "Bearer " + token)

        session = KnoxSession.login( gateway, headers )

        if (args.length > 0) {
          dir = args[0]
        } else {
          dir = "/"
        }

        text = Hdfs.ls( session ).dir( dir ).now().string
        json = (new JsonSlurper()).parseText( text )
        statuses = json.get("FileStatuses");

        println statuses

        session.shutdown()

Note the following about the above sample script:

1. Use of the KnoxToken credential collector
2. Use of the targetUrl from the credential collector
3. Optional override of the target url with environment variable
4. The passing of the headers map to the session creation in Hadoop.login
5. The passing of an argument for the ls command for the path to list or default to "/"

Also note that there is no reason to prompt for username and password as long as the token has not been destroyed or expired.
There is also no hardcoded endpoint for using the token - it is specified in the token cache or overridden by environment variable.




## Client DSL and SDK Details ##

The lack of any formal SDK or client for REST APIs in Hadoop led to thinking about a very simple client that could help people use and evaluate the gateway.
The list below outlines the general requirements for such a client.

* Promote the evaluation and adoption of the Apache Knox Gateway
* Simple to deploy and use on data worker desktops for access to remote Hadoop clusters
* Simple to extend with new commands both by other Hadoop projects and by the end user
* Support the notion of a SSO session for multiple Hadoop interactions
* Support the multiple authentication and federation token capabilities of the Apache Knox Gateway
* Promote the use of REST APIs as the dominant remote client mechanism for Hadoop services
* Promote the sense of Hadoop as a single unified product
* Aligned with the Apache Knox Gateway's overall goals for security

The result is a very simple DSL ([Domain Specific Language](http://en.wikipedia.org/wiki/Domain-specific_language)) of sorts that is used via [Groovy](http://groovy.codehaus.org) scripts.
Here is an example of a command that copies a file from the local file system to HDFS.

_Note: The variables `session`, `localFile` and `remoteFile` are assumed to be defined._

    Hdfs.put(session).file(localFile).to(remoteFile).now()

*This work is in very early development but is already very useful in its current state.*
*We are very interested in receiving feedback about how to improve this feature and the DSL in particular.*

A note of thanks to [REST-assured](https://code.google.com/p/rest-assured/) which provides a [Fluent interface](http://en.wikipedia.org/wiki/Fluent_interface) style DSL for testing REST services.
It served as the initial inspiration for the creation of this DSL.

### Assumptions ###

This document assumes a few things about your environment in order to simplify the examples.

* The JVM is executable as simply `java`.
* The Apache Knox Gateway is installed and functional.
* The example commands are executed within the context of the `GATEWAY_HOME` current directory.
The `GATEWAY_HOME` directory is the directory within the Apache Knox Gateway installation that contains the README file and the bin, conf and deployments directories.
* A few examples require the use of commands from a standard Groovy installation.  These examples are optional but to try them you will need Groovy [installed](http://groovy.codehaus.org/Installing+Groovy).


### Basics ###

In order for secure connections to be made to the Knox gateway server over SSL, the user will need to trust
the certificate presented by the gateway while connecting. The knoxcli command export-cert may be used to get
access the gateway-identity cert. It can then be imported into cacerts on the client machine or put into a
keystore that will be discovered in:

* The user's home directory
* In a directory specified in an environment variable: `KNOX_CLIENT_TRUSTSTORE_DIR`
* In a directory specified with the above variable with the keystore filename specified in the variable: `KNOX_CLIENT_TRUSTSTORE_FILENAME`
* Default password "changeit" or password may be specified in environment variable: `KNOX_CLIENT_TRUSTSTORE_PASS`
* Or the JSSE system property `javax.net.ssl.trustStore` can be used to specify its location

The DSL requires a shell to interpret the Groovy script.
The shell can either be used interactively or to execute a script file.
To simplify use, the distribution contains an embedded version of the Groovy shell.

The shell can be run interactively. Use the command `exit` to exit.

    java -jar bin/shell.jar

When running interactively it may be helpful to reduce some of the output generated by the shell console.
Use the following command in the interactive shell to reduce that output.
This only needs to be done once as these preferences are persisted.

    set verbosity QUIET
    set show-last-result false

Also when running interactively use the `exit` command to terminate the shell.
Using `^C` to exit can sometimes leaves the parent shell in a problematic state.

The shell can also be used to execute a script by passing a single filename argument.

    java -jar bin/shell.jar samples/ExampleWebHdfsPutGet.groovy


### Examples ###

Once the shell can be launched the DSL can be used to interact with the gateway and Hadoop.
Below is a very simple example of an interactive shell session to upload a file to HDFS.

    java -jar bin/shell.jar
    knox:000> session = Hadoop.login( "https://localhost:8443/gateway/sandbox", "guest", "guest-password" )
    knox:000> Hdfs.put( session ).file( "README" ).to( "/tmp/example/README" ).now()

The `knox:000>` in the example above is the prompt from the embedded Groovy console.
If you output doesn't look like this you may need to set the verbosity and show-last-result preferences as described above in the Usage section.

If you receive an error `HTTP/1.1 403 Forbidden` it may be because that file already exists.
Try deleting it with the following command and then try again.

    knox:000> Hdfs.rm(session).file("/tmp/example/README").now()

Without using some other tool to browse HDFS it is hard to tell that this command did anything.
Execute this to get a bit more feedback.

    knox:000> println "Status=" + Hdfs.put( session ).file( "README" ).to( "/tmp/example/README2" ).now().statusCode
    Status=201

Notice that a different filename is used for the destination.
Without this an error would have resulted.
Of course the DSL also provides a command to list the contents of a directory.

    knox:000> println Hdfs.ls( session ).dir( "/tmp/example" ).now().string
    {"FileStatuses":{"FileStatus":[{"accessTime":1363711366977,"blockSize":134217728,"group":"hdfs","length":19395,"modificationTime":1363711366977,"owner":"guest","pathSuffix":"README","permission":"644","replication":1,"type":"FILE"},{"accessTime":1363711375617,"blockSize":134217728,"group":"hdfs","length":19395,"modificationTime":1363711375617,"owner":"guest","pathSuffix":"README2","permission":"644","replication":1,"type":"FILE"}]}}

It is a design decision of the DSL to not provide type safe classes for various request and response payloads.
Doing so would provide an undesirable coupling between the DSL and the service implementation.
It also would make adding new commands much more difficult.
See the Groovy section below for a variety capabilities and tools for working with JSON and XML to make this easy.
The example below shows the use of JsonSlurper and GPath to extract content from a JSON response.

    knox:000> import groovy.json.JsonSlurper
    knox:000> text = Hdfs.ls( session ).dir( "/tmp/example" ).now().string
    knox:000> json = (new JsonSlurper()).parseText( text )
    knox:000> println json.FileStatuses.FileStatus.pathSuffix
    [README, README2]

*In the future, "built-in" methods to slurp JSON and XML may be added to make this a bit easier.*
*This would allow for the following type of single line interaction:*

    println Hdfs.ls(session).dir("/tmp").now().json().FileStatuses.FileStatus.pathSuffix

Shell sessions should always be ended with shutting down the session.
The examples above do not touch on it but the DSL supports the simple execution of commands asynchronously.
The shutdown command attempts to ensures that all asynchronous commands have completed before existing the shell.

    knox:000> session.shutdown()
    knox:000> exit

All of the commands above could have been combined into a script file and executed as a single line.

    java -jar bin/shell.jar samples/ExampleWebHdfsPutGet.groovy

This would be the content of that script.

    import org.apache.knox.gateway.shell.Hadoop
    import org.apache.knox.gateway.shell.hdfs.Hdfs
    import groovy.json.JsonSlurper
    
    gateway = "https://localhost:8443/gateway/sandbox"
    username = "guest"
    password = "guest-password"
    dataFile = "README"
    
    session = Hadoop.login( gateway, username, password )
    Hdfs.rm( session ).file( "/tmp/example" ).recursive().now()
    Hdfs.put( session ).file( dataFile ).to( "/tmp/example/README" ).now()
    text = Hdfs.ls( session ).dir( "/tmp/example" ).now().string
    json = (new JsonSlurper()).parseText( text )
    println json.FileStatuses.FileStatus.pathSuffix
    session.shutdown()
    exit

Notice the `Hdfs.rm` command.  This is included simply to ensure that the script can be rerun.
Without this an error would result the second time it is run.

### Futures ###

The DSL supports the ability to invoke commands asynchronously via the later() invocation method.
The object returned from the `later()` method is a `java.util.concurrent.Future` parameterized with the response type of the command.
This is an example of how to asynchronously put a file to HDFS.

    future = Hdfs.put(session).file("README").to("/tmp/example/README").later()
    println future.get().statusCode

The `future.get()` method will block until the asynchronous command is complete.
To illustrate the usefulness of this however multiple concurrent commands are required.

    readmeFuture = Hdfs.put(session).file("README").to("/tmp/example/README").later()
    licenseFuture = Hdfs.put(session).file("LICENSE").to("/tmp/example/LICENSE").later()
    session.waitFor( readmeFuture, licenseFuture )
    println readmeFuture.get().statusCode
    println licenseFuture.get().statusCode

The `session.waitFor()` method will wait for one or more asynchronous commands to complete.


### Closures ###

Futures alone only provide asynchronous invocation of the command.
What if some processing should also occur asynchronously once the command is complete.
Support for this is provided by closures.
Closures are blocks of code that are passed into the `later()` invocation method.
In Groovy these are contained within `{}` immediately after a method.
These blocks of code are executed once the asynchronous command is complete.

    Hdfs.put(session).file("README").to("/tmp/example/README").later(){ println it.statusCode }

In this example the `put()` command is executed on a separate thread and once complete the `println it.statusCode` block is executed on that thread.
The `it` variable is automatically populated by Groovy and is a reference to the result that is returned from the future or `now()` method.
The future example above can be rewritten to illustrate the use of closures.

    readmeFuture = Hdfs.put(session).file("README").to("/tmp/example/README").later() { println it.statusCode }
    licenseFuture = Hdfs.put(session).file("LICENSE").to("/tmp/example/LICENSE").later() { println it.statusCode }
    session.waitFor( readmeFuture, licenseFuture )

Again, the `session.waitFor()` method will wait for one or more asynchronous commands to complete.


### Constructs ###

In order to understand the DSL there are three primary constructs that need to be understood.


#### Session ####

This construct encapsulates the client side session state that will be shared between all command invocations.
In particular it will simplify the management of any tokens that need to be presented with each command invocation.
It also manages a thread pool that is used by all asynchronous commands which is why it is important to call one of the shutdown methods.

The syntax associated with this is expected to change. We expect that credentials will not need to be provided to the gateway. Rather it is expected that some form of access token will be used to initialize the session.

#### ClientContext ####

The ClientContext encapsulates the connection parameters, such as the URL, socket timeout parameters, retry configuration and connection pool parameters.

    ClientContext context = ClientContext.with("http://localhost:8443");
    context.connection().retryCount(2).requestSentRetryEnabled(false).retryIntervalMillis(1000).end();
    KnoxSession session = KnoxSession.login(context);

* retryCount - how many times to retry; -1 means no retries
* requestSentRetryEnabled - true if it's OK to retry requests that have been sent
* retryIntervalMillis - The interval between the subsequent auto-retries when the service is unavailable
      
#### Services ####

Services are the primary extension point for adding new suites of commands.
The current built-in examples are: Hdfs, Job and Workflow.
The desire for extensibility is the reason for the slightly awkward `Hdfs.ls(session)` syntax.
Certainly something more like `session.hdfs().ls()` would have been preferred but this would prevent adding new commands easily.
At a minimum it would result in extension commands with a different syntax from the "built-in" commands.

The service objects essentially function as a factory for a suite of commands.


#### Commands ####

Commands provide the behavior of the DSL.
They typically follow a Fluent interface style in order to allow for single line commands.
There are really three parts to each command: Request, Invocation, Response


#### Request ####

The request is populated by all of the methods following the "verb" method and the "invoke" method.
For example in `Hdfs.rm(session).ls(dir).now()` the request is populated between the "verb" method `rm()` and the "invoke" method `now()`.


#### Invocation ####

The invocation method controls how the request is invoked.
Currently supported synchronous and asynchronous invocation.
The `now()` method executes the request and returns the result immediately.
The `later()` method submits the request to be executed later and returns a future from which the result can be retrieved.
In addition `later()` invocation method can optionally be provided a closure to execute when the request is complete.
See the Futures and Closures sections below for additional detail and examples.


#### Response ####

The response contains the results of the invocation of the request.
In most cases the response is a thin wrapper over the HTTP response.
In fact many commands will share a single BasicResponse type that only provides a few simple methods.

    public int getStatusCode()
    public long getContentLength()
    public String getContentType()
    public String getContentEncoding()
    public InputStream getStream()
    public String getString()
    public byte[] getBytes()
    public void close();

Thanks to Groovy these methods can be accessed as attributes.
In the some of the examples the staticCode was retrieved for example.

    println Hdfs.put(session).rm(dir).now().statusCode

Groovy will invoke the getStatusCode method to retrieve the statusCode attribute.

The three methods `getStream()`, `getBytes()` and `getString()` deserve special attention.
Care must be taken that the HTTP body is fully read once and only once.
Therefore one of these methods (and only one) must be called once and only once.
Calling one of these more than once will cause an error.
Failing to call one of these methods once will result in lingering open HTTP connections.
The `close()` method may be used if the caller is not interested in reading the result body.
Most commands that do not expect a response body will call close implicitly.
If the body is retrieved via `getBytes()` or `getString()`, the `close()` method need not be called.
When using `getStream()`, care must be taken to consume the entire body otherwise lingering open HTTP connections will result.
The `close()` method may be called after reading the body partially to discard the remainder of the body.


### Services ###

The built-in supported client DSL for each Hadoop service can be found in the #[Service Details] section.


### Extension ###

Extensibility is a key design goal of the KnoxShell and client DSL.
There are two ways to provide extended functionality for use with the shell.
The first is to simply create Groovy scripts that use the DSL to perform a useful task.
The second is to add new services and commands.
In order to add new service and commands new classes must be written in either Groovy or Java and added to the classpath of the shell.
Fortunately there is a very simple way to add classes and JARs to the shell classpath.
The first time the shell is executed it will create a configuration file in the same directory as the JAR with the same base name and a `.cfg` extension.

    bin/shell.jar
    bin/shell.cfg

That file contains both the main class for the shell as well as a definition of the classpath.
Currently that file will by default contain the following.

    main.class=org.apache.knox.gateway.shell.Shell
    class.path=../lib; ../lib/*.jar; ../ext; ../ext/*.jar

Therefore to extend the shell you should copy any new service and command class either to the `ext` directory or if they are packaged within a JAR copy the JAR to the `ext` directory.
The `lib` directory is reserved for JARs that may be delivered with the product.

Below are samples for the service and command classes that would need to be written to add new commands to the shell.
These happen to be Groovy source files but could - with very minor changes - be Java files.
The easiest way to add these to the shell is to compile them directly into the `ext` directory.
*Note: This command depends upon having the Groovy compiler installed and available on the execution path.*

    groovy -d ext -cp bin/shell.jar samples/SampleService.groovy \
        samples/SampleSimpleCommand.groovy samples/SampleComplexCommand.groovy

These source files are available in the samples directory of the distribution but are included here for convenience.


#### Sample Service (Groovy)

    import org.apache.knox.gateway.shell.Hadoop

    class SampleService {

        static String PATH = "/webhdfs/v1"

        static SimpleCommand simple( Hadoop session ) {
            return new SimpleCommand( session )
        }

        static ComplexCommand.Request complex( Hadoop session ) {
            return new ComplexCommand.Request( session )
        }

    }

#### Sample Simple Command (Groovy)

    import org.apache.knox.gateway.shell.AbstractRequest
    import org.apache.knox.gateway.shell.BasicResponse
    import org.apache.knox.gateway.shell.Hadoop
    import org.apache.http.client.methods.HttpGet
    import org.apache.http.client.utils.URIBuilder

    import java.util.concurrent.Callable

    class SimpleCommand extends AbstractRequest<BasicResponse> {

        SimpleCommand( Hadoop session ) {
            super( session )
        }

        private String param
        SimpleCommand param( String param ) {
            this.param = param
            return this
        }

        @Override
        protected Callable<BasicResponse> callable() {
            return new Callable<BasicResponse>() {
                @Override
                BasicResponse call() {
                    URIBuilder uri = uri( SampleService.PATH, param )
                    addQueryParam( uri, "op", "LISTSTATUS" )
                    HttpGet get = new HttpGet( uri.build() )
                    return new BasicResponse( execute( get ) )
                }
            }
        }

    }


#### Sample Complex Command (Groovy)

    import com.jayway.jsonpath.JsonPath
    import org.apache.knox.gateway.shell.AbstractRequest
    import org.apache.knox.gateway.shell.BasicResponse
    import org.apache.knox.gateway.shell.Hadoop
    import org.apache.http.HttpResponse
    import org.apache.http.client.methods.HttpGet
    import org.apache.http.client.utils.URIBuilder

    import java.util.concurrent.Callable

    class ComplexCommand {

        static class Request extends AbstractRequest<Response> {

            Request( Hadoop session ) {
                super( session )
            }

            private String param;
            Request param( String param ) {
                this.param = param;
                return this;
            }

            @Override
            protected Callable<Response> callable() {
                return new Callable<Response>() {
                    @Override
                    Response call() {
                        URIBuilder uri = uri( SampleService.PATH, param )
                        addQueryParam( uri, "op", "LISTSTATUS" )
                        HttpGet get = new HttpGet( uri.build() )
                        return new Response( execute( get ) )
                    }
                }
            }

        }

        static class Response extends BasicResponse {

            Response(HttpResponse response) {
                super(response)
            }

            public List<String> getNames() {
                return JsonPath.read( string, "\$.FileStatuses.FileStatus[*].pathSuffix" )
            }

        }

    }


### Groovy

The shell included in the distribution is basically an unmodified packaging of the Groovy shell.
The distribution does however provide a wrapper that makes it very easy to setup the class path for the shell.
In fact the JARs required to execute the DSL are included on the class path by default.
Therefore these command are functionally equivalent if you have Groovy installed.
See below for a description of what is required for JARs required by the DSL from `lib` and `dep` directories.

    java -jar bin/shell.jar samples/ExampleWebHdfsPutGet.groovy
    groovy -classpath {JARs required by the DSL from lib and dep} samples/ExampleWebHdfsPutGet.groovy

The interactive shell isn't exactly equivalent.
However the only difference is that the shell.jar automatically executes some additional imports that are useful for the KnoxShell client DSL.
So these two sets of commands should be functionality equivalent.
*However there is currently a class loading issue that prevents the groovysh command from working properly.*

    java -jar bin/shell.jar

    groovysh -classpath {JARs required by the DSL from lib and dep}
    import org.apache.knox.gateway.shell.Hadoop
    import org.apache.knox.gateway.shell.hdfs.Hdfs
    import org.apache.knox.gateway.shell.job.Job
    import org.apache.knox.gateway.shell.workflow.Workflow
    import java.util.concurrent.TimeUnit

Alternatively, you can use the Groovy Console which does not appear to have the same class loading issue.

    groovyConsole -classpath {JARs required by the DSL from lib and dep}

    import org.apache.knox.gateway.shell.Hadoop
    import org.apache.knox.gateway.shell.hdfs.Hdfs
    import org.apache.knox.gateway.shell.job.Job
    import org.apache.knox.gateway.shell.workflow.Workflow
    import java.util.concurrent.TimeUnit

The JARs currently required by the client DSL are

    lib/gateway-shell-{GATEWAY_VERSION}.jar
    dep/httpclient-4.3.6.jar
    dep/httpcore-4.3.3.jar
    dep/commons-lang3-3.4.jar
    dep/commons-codec-1.7.jar

So on Linux/MacOS you would need this command

    groovy -cp lib/gateway-shell-0.10.0.jar:dep/httpclient-4.3.6.jar:dep/httpcore-4.3.3.jar:dep/commons-lang3-3.4.jar:dep/commons-codec-1.7.jar samples/ExampleWebHdfsPutGet.groovy

and on Windows you would need this command

    groovy -cp lib/gateway-shell-0.10.0.jar;dep/httpclient-4.3.6.jar;dep/httpcore-4.3.3.jar;dep/commons-lang3-3.4.jar;dep/commons-codec-1.7.jar samples/ExampleWebHdfsPutGet.groovy

The exact list of required JARs is likely to change from release to release so it is recommended that you utilize the wrapper `bin/shell.jar`.

In addition because the DSL can be used via standard Groovy, the Groovy integrations in many popular IDEs (e.g. IntelliJ, Eclipse) can also be used.
This makes it particularly nice to develop and execute scripts to interact with Hadoop.
The code-completion features in modern IDEs in particular provides immense value.
All that is required is to add the `gateway-shell-{GATEWAY_VERSION}.jar` to the projects class path.

There are a variety of Groovy tools that make it very easy to work with the standard interchange formats (i.e. JSON and XML).
In Groovy the creation of XML or JSON is typically done via a "builder" and parsing done via a "slurper".
In addition once JSON or XML is "slurped" the GPath, an XPath like feature build into Groovy can be used to access data.

* XML
    * Markup Builder [Overview](http://groovy.codehaus.org/Creating+XML+using+Groovy's+MarkupBuilder), [API](http://groovy.codehaus.org/api/groovy/xml/MarkupBuilder.html)
    * XML Slurper [Overview](http://groovy.codehaus.org/Reading+XML+using+Groovy's+XmlSlurper), [API](http://groovy.codehaus.org/api/groovy/util/XmlSlurper.html)
    * XPath [Overview](http://groovy.codehaus.org/GPath), [API](http://docs.oracle.com/javase/1.5.0/docs/api/javax/xml/xpath/XPath.html)
* JSON
    * JSON Builder [API](http://groovy.codehaus.org/gapi/groovy/json/JsonBuilder.html)
    * JSON Slurper [API](http://groovy.codehaus.org/gapi/groovy/json/JsonSlurper.html)
    * JSON Path [API](https://code.google.com/p/json-path/)
    * GPath [Overview](http://groovy.codehaus.org/GPath)

