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
-->

### Hive ###

The [Hive wiki pages](https://cwiki.apache.org/confluence/display/Hive/Home) describe Hive installation and configuration processes.
In sandbox configuration file for Hive is located at `/etc/hive/hive-site.xml`.
Hive Server has to be started in HTTP mode.
Note the properties shown below as they are related to configuration required by the gateway.

    <property>
        <name>hive.server2.thrift.http.port</name>
        <value>10001</value>
        <description>Port number when in HTTP mode.</description>
    </property>

    <property>
        <name>hive.server2.thrift.http.path</name>
        <value>cliservice</value>
        <description>Path component of URL endpoint when in HTTP mode.</description>
    </property>

    <property>
        <name>hive.server2.transport.mode</name>
        <value>http</value>
        <description>Server transport mode. "binary" or "http".</description>
    </property>

    <property>
        <name>hive.server2.allow.user.substitution</name>
        <value>true</value>
    </property>

The gateway by default includes a sample topology descriptor file `{GATEWAY_HOME}/deployments/sandbox.xml`.
The value in this sample is configured to work with an installed Sandbox VM.

    <service>
        <role>HIVE</role>
        <url>http://localhost:10001/cliservice</url>
        <param>
            <name>replayBufferSize</name>
            <value>8</value>
        </param>
    </service>

By default the gateway is configured to use the binary transport mode for Hive in the Sandbox.

A default replayBufferSize of 8KB is shown in the sample topology file above.  This may need to be increased if your query size is larger.

#### Hive JDBC URL Mapping ####

| ------- | ------------------------------------------------------------------------------- |
| Gateway | `jdbc:hive2://{gateway-host}:{gateway-port}/;ssl=true;sslTrustStore={gateway-trust-store-path};trustStorePassword={gateway-trust-store-password};transportMode=http;httpPath={gateway-path}/{cluster-name}/hive` |
| Cluster | `http://{hive-host}:{hive-port}/{hive-path}` |

#### Hive Examples ####

This guide provides detailed examples for how to do some basic interactions with Hive via the Apache Knox Gateway.

##### Hive Setup #####

1. Make sure you are running the correct version of Hive to ensure JDBC/Thrift/HTTP support.
2. Make sure Hive Server is running on the correct port.
3. Make sure Hive Server is running in HTTP mode.
4. Client side (JDBC):
     1. Hive JDBC in HTTP mode depends on following minimal libraries set to run successfully(must be in the classpath):
         * hive-jdbc-0.14.0-standalone.jar;
         * commons-logging-1.1.3.jar;
     2. Connection URL has to be the following: `jdbc:hive2://{gateway-host}:{gateway-port}/;ssl=true;sslTrustStore={gateway-trust-store-path};trustStorePassword={gateway-trust-store-password};transportMode=http;httpPath={gateway-path}/{cluster-name}/hive`
     3. Look at https://cwiki.apache.org/confluence/display/Hive/GettingStarted#GettingStarted-DDLOperations for examples.
       Hint: For testing it would be better to execute `set hive.security.authorization.enabled=false` as the first statement.
       Hint: Good examples of Hive DDL/DML can be found here http://gettingstarted.hadooponazure.com/hw/hive.html

##### Customization #####

This example may need to be tailored to the execution environment.
In particular host name, host port, user name, user password and context path may need to be changed to match your environment.
In particular there is one example file in the distribution that may need to be customized.
Take a moment to review this file.
All of the values that may need to be customized can be found together at the top of the file.

* samples/hive/java/jdbc/sandbox/HiveJDBCSample.java

##### Client JDBC Example #####

Sample example for creating new table, loading data into it from the file system local to the Hive server and querying data from that table.

###### Java ######

    import java.sql.Connection;
    import java.sql.DriverManager;
    import java.sql.ResultSet;
    import java.sql.SQLException;
    import java.sql.Statement;

    import java.util.logging.Level;
    import java.util.logging.Logger;

    public class HiveJDBCSample {

      public static void main( String[] args ) {
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;

        try {
          String user = "guest";
          String password = user + "-password";
          String gatewayHost = "localhost";
          int gatewayPort = 8443;
          String trustStore = "/usr/lib/knox/data/security/keystores/gateway.jks";
          String trustStorePassword = "knoxsecret";
          String contextPath = "gateway/sandbox/hive";
          String connectionString = String.format( "jdbc:hive2://%s:%d/;ssl=true;sslTrustStore=%s;trustStorePassword=%s?hive.server2.transport.mode=http;hive.server2.thrift.http.path=/%s", gatewayHost, gatewayPort, trustStore, trustStorePassword, contextPath );

          // load Hive JDBC Driver
          Class.forName( "org.apache.hive.jdbc.HiveDriver" );

          // configure JDBC connection
          connection = DriverManager.getConnection( connectionString, user, password );

          statement = connection.createStatement();

          // disable Hive authorization - it could be omitted if Hive authorization
          // was configured properly
          statement.execute( "set hive.security.authorization.enabled=false" );

          // create sample table
          statement.execute( "CREATE TABLE logs(column1 string, column2 string, column3 string, column4 string, column5 string, column6 string, column7 string) ROW FORMAT DELIMITED FIELDS TERMINATED BY ' '" );

          // load data into Hive from file /tmp/log.txt which is placed on the local file system
          statement.execute( "LOAD DATA LOCAL INPATH '/tmp/log.txt' OVERWRITE INTO TABLE logs" );

          resultSet = statement.executeQuery( "SELECT * FROM logs" );

          while ( resultSet.next() ) {
            System.out.println( resultSet.getString( 1 ) + " --- " + resultSet.getString( 2 ) + " --- " + resultSet.getString( 3 ) + " --- " + resultSet.getString( 4 ) );
          }
        } catch ( ClassNotFoundException ex ) {
          Logger.getLogger( HiveJDBCSample.class.getName() ).log( Level.SEVERE, null, ex );
        } catch ( SQLException ex ) {
          Logger.getLogger( HiveJDBCSample.class.getName() ).log( Level.SEVERE, null, ex );
        } finally {
          if ( resultSet != null ) {
            try {
              resultSet.close();
            } catch ( SQLException ex ) {
              Logger.getLogger( HiveJDBCSample.class.getName() ).log( Level.SEVERE, null, ex );
            }
          }
          if ( statement != null ) {
            try {
              statement.close();
            } catch ( SQLException ex ) {
              Logger.getLogger( HiveJDBCSample.class.getName() ).log( Level.SEVERE, null, ex );
            }
          }
          if ( connection != null ) {
            try {
              connection.close();
            } catch ( SQLException ex ) {
              Logger.getLogger( HiveJDBCSample.class.getName() ).log( Level.SEVERE, null, ex );
            }
          }
        }
      }
    }

###### Groovy ######

Make sure that `{GATEWAY_HOME/ext}` directory contains the following libraries for successful execution:

- hive-jdbc-0.14.0-standalone.jar;
- commons-logging-1.1.3.jar;

There are several ways to execute this sample depending upon your preference.

You can use the Groovy interpreter provided with the distribution.

    java -jar bin/shell.jar samples/hive/groovy/jdbc/sandbox/HiveJDBCSample.groovy

You can manually type in the KnoxShell DSL script into the interactive Groovy interpreter provided with the distribution.

    java -jar bin/shell.jar

Each line from the file below will need to be typed or copied into the interactive shell.

    import java.sql.DriverManager

    user = "guest";
    password = user + "-password";
    gatewayHost = "localhost";
    gatewayPort = 8443;
    trustStore = "/usr/lib/knox/data/security/keystores/gateway.jks";
    trustStorePassword = "knoxsecret";
    contextPath = "gateway/sandbox/hive";
    connectionString = String.format( "jdbc:hive2://%s:%d/;ssl=true;sslTrustStore=%s;trustStorePassword=%s?hive.server2.transport.mode=http;hive.server2.thrift.http.path=/%s", gatewayHost, gatewayPort, trustStore, trustStorePassword, contextPath );

    // Load Hive JDBC Driver
    Class.forName( "org.apache.hive.jdbc.HiveDriver" );

    // Configure JDBC connection
    connection = DriverManager.getConnection( connectionString, user, password );

    statement = connection.createStatement();

    // Disable Hive authorization - This can be omitted if Hive authorization is configured properly
    statement.execute( "set hive.security.authorization.enabled=false" );

    // Create sample table
    statement.execute( "CREATE TABLE logs(column1 string, column2 string, column3 string, column4 string, column5 string, column6 string, column7 string) ROW FORMAT DELIMITED FIELDS TERMINATED BY ' '" );

    // Load data into Hive from file /tmp/log.txt which is placed on the local file system
    statement.execute( "LOAD DATA LOCAL INPATH '/tmp/sample.log' OVERWRITE INTO TABLE logs" );

    resultSet = statement.executeQuery( "SELECT * FROM logs" );

    while ( resultSet.next() ) {
      System.out.println( resultSet.getString( 1 ) + " --- " + resultSet.getString( 2 ) );
    }

    resultSet.close();
    statement.close();
    connection.close();

Examples use 'log.txt' with content:

    2012-02-03 18:35:34 SampleClass6 [INFO] everything normal for id 577725851
    2012-02-03 18:35:34 SampleClass4 [FATAL] system problem at id 1991281254
    2012-02-03 18:35:34 SampleClass3 [DEBUG] detail for id 1304807656
    2012-02-03 18:35:34 SampleClass3 [WARN] missing id 423340895
    2012-02-03 18:35:34 SampleClass5 [TRACE] verbose detail for id 2082654978
    2012-02-03 18:35:34 SampleClass0 [ERROR] incorrect id  1886438513
    2012-02-03 18:35:34 SampleClass9 [TRACE] verbose detail for id 438634209
    2012-02-03 18:35:34 SampleClass8 [DEBUG] detail for id 2074121310
    2012-02-03 18:35:34 SampleClass0 [TRACE] verbose detail for id 1505582508
    2012-02-03 18:35:34 SampleClass0 [TRACE] verbose detail for id 1903854437
    2012-02-03 18:35:34 SampleClass7 [DEBUG] detail for id 915853141
    2012-02-03 18:35:34 SampleClass3 [TRACE] verbose detail for id 303132401
    2012-02-03 18:35:34 SampleClass6 [TRACE] verbose detail for id 151914369
    2012-02-03 18:35:34 SampleClass2 [DEBUG] detail for id 146527742
    ...

Expected output:

    2012-02-03 --- 18:35:34 --- SampleClass6 --- [INFO]
    2012-02-03 --- 18:35:34 --- SampleClass4 --- [FATAL]
    2012-02-03 --- 18:35:34 --- SampleClass3 --- [DEBUG]
    2012-02-03 --- 18:35:34 --- SampleClass3 --- [WARN]
    2012-02-03 --- 18:35:34 --- SampleClass5 --- [TRACE]
    2012-02-03 --- 18:35:34 --- SampleClass0 --- [ERROR]
    2012-02-03 --- 18:35:34 --- SampleClass9 --- [TRACE]
    2012-02-03 --- 18:35:34 --- SampleClass8 --- [DEBUG]
    2012-02-03 --- 18:35:34 --- SampleClass0 --- [TRACE]
    2012-02-03 --- 18:35:34 --- SampleClass0 --- [TRACE]
    2012-02-03 --- 18:35:34 --- SampleClass7 --- [DEBUG]
    2012-02-03 --- 18:35:34 --- SampleClass3 --- [TRACE]
    2012-02-03 --- 18:35:34 --- SampleClass6 --- [TRACE]
    2012-02-03 --- 18:35:34 --- SampleClass2 --- [DEBUG]
    ...

### HiveServer2 HA ###

Knox provides basic failover functionality for calls made to Hive Server when more than one HiveServer2 instance is
installed in the cluster and registered with the same ZooKeeper ensemble. The HA functionality in this case fetches the
HiveServer2 URL information from a ZooKeeper ensemble, so the user need only supply the necessary ZooKeeper
configuration and not the Hive connection URLs.

To enable HA functionality for Hive in Knox the following configuration has to be added to the topology file.

    <provider>
        <role>ha</role>
        <name>HaProvider</name>
        <enabled>true</enabled>
        <param>
            <name>HIVE</name>
            <value>maxFailoverAttempts=3;failoverSleep=1000;enabled=true;zookeeperEnsemble=machine1:2181,machine2:2181,machine3:2181;zookeeperNamespace=hiveserver2</value>
       </param>
    </provider>

The role and name of the provider above must be as shown. The name in the 'param' section must match that of the service
role name that is being configured for HA and the value in the 'param' section is the configuration for that particular
service in HA mode. In this case the name is 'HIVE'.

The various configuration parameters are described below:

* maxFailoverAttempts -
This is the maximum number of times a failover will be attempted. The failover strategy at this time is very simplistic
in that the next URL in the list of URLs provided for the service is used and the one that failed is put at the bottom
of the list. If the list is exhausted and the maximum number of attempts is not reached then the first URL will be tried
again after the list is fetched again from Zookeeper (a refresh of the list is done at this point)

* failoverSleep -
The amount of time in millis that the process will wait or sleep before attempting to failover.

* enabled -
Flag to turn the particular service on or off for HA.

* zookeeperEnsemble -
A comma separated list of host names (or IP addresses) of the zookeeper hosts that consist of the ensemble that the Hive
servers register their information with. This value can be obtained from Hive's config file hive-site.xml as the value
for the parameter 'hive.zookeeper.quorum'.

* zookeeperNamespace -
This is the namespace under which HiveServer2 information is registered in the Zookeeper ensemble. This value can be
obtained from Hive's config file hive-site.xml as the value for the parameter 'hive.server2.zookeeper.namespace'.


And for the service configuration itself the URLs need not be added to the list. For example.

    <service>
        <role>HIVE</role>
    </service>

Please note that there is no `<url>` tag specified here as the URLs for the Hive servers are obtained from Zookeeper.
