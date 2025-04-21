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
Health Monitoring REST API
===

Knox provides REST-ful API for monitoring the core service. It primarily exposes the health of the Knox service that includes service status (up/down) as well as other health metrics. This is a work-in-progress feature, which started with an extensible framework to support basic functionalities. In particular, it currently supports the API to  A) *ping* the service and B) time-based statistics related to all API calls.

#### Health Monitoring Setup
The basic setup includes two major steps A) add configurations to enable the metrics collection and reporting B) write a topology file and upload it into *topologies* directory.

##### Service Configurations
At first, we need to make sure the gateway configurations to gather and report to JMX are turned on in *gateway-site.xml*. The following two configurations into *gateway-site.xml* will serve the purpose.

```
<property>
   <name>gateway.metrics.enabled</name>
   <value>true</value>
   <description>Boolean flag indicates whether to enable the metrics collection</description>
</property>
<property>
   <name>gateway.jmx.metrics.reporting.enabled</name>
   <value>true</value>
   <description>Boolean flag indicates whether to enable the metrics reporting using JMX</description>
</property>

```
    
##### health.xml Topology
In order to enable health monitoring REST service, you need to add a new topology file (i.e. *health.xml*). The following is an example that is configured to test the basic functionalities of Knox service. It is highly recommended using more restricted authentication mechanism.

```
<topology>

    <gateway>

        <provider>
            <role>authentication</role>
            <name>ShiroProvider</name>
            <enabled>true</enabled>
            <param>
                <!-- 
                session timeout in minutes,  this is really idle timeout,
                defaults to 30 mins, if the property value is not defined,, 
                current client authentication would expire if client idles continuously for more than this value
                -->
                <name>sessionTimeout</name>
                <value>30</value>
            </param>
            <param>
                <name>main.ldapRealm</name>
                <value>org.apache.knox.gateway.shirorealm.KnoxLdapRealm</value>
            </param>
            <param>
                <name>main.ldapContextFactory</name>
                <value>org.apache.knox.gateway.shirorealm.KnoxLdapContextFactory</value>
            </param>
            <param>
                <name>main.ldapRealm.contextFactory</name>
                <value>$ldapContextFactory</value>
            </param>
            <param>
                <name>main.ldapRealm.userDnTemplate</name>
                <value>uid={0},ou=people,dc=hadoop,dc=apache,dc=org</value>
            </param>
            <param>
                <name>main.ldapRealm.contextFactory.url</name>
                <value>ldap://localhost:33389</value>
            </param>
            <param>
                <name>main.ldapRealm.contextFactory.authenticationMechanism</name>
                <value>simple</value>
            </param>
            <param>
                <name>urls./**</name>
                <value>authcBasic</value>
            </param>
        </provider>

        <provider>
            <role>authorization</role>
            <name>AclsAuthz</name>
            <enabled>false</enabled>
            <param>
                <name>knox.acl</name>
                <value>admin;*;*</value>
            </param>
        </provider>

        <provider>
            <role>identity-assertion</role>
            <name>Default</name>
            <enabled>false</enabled>
        </provider>

        <provider>
            <role>hostmap</role>
            <name>static</name>
            <enabled>true</enabled>
            <param><name>localhost</name><value>sandbox,sandbox.hortonworks.com</value></param>
        </provider>

    </gateway>

    <service>
        <role>HEALTH</role>
    </service>

</topology>
```

Just as with any Knox service, the gateway providers protect the health monitoring REST service defined above it. In this case, the ShiroProvider is taking care of HTTP Basic Auth using LDAP. Once the user authenticates with LDAP, the request processing continues to the *Health* service that will perform the necessary actions.

The authenticate/federation provider can be swapped out to fit your deployment environment.

After creating the file health.xml with above contents, you need to copy the file to *KNOX_HOME/conf/topologies* directory. If Knox/gateway service is not running, you can start it using "*bin/gateway.sh start*". Otherwise the service would automatically pick this new '*health*' service. When gateway service registers the new service, it displays the following log messages in *log/gateway.log*.

```
2017-08-22 03:44:25,045 INFO  knox.gateway (GatewayServer.java:handleCreateDeployment(677)) - Deploying topology health to /home/joe/knox/knox-0.12.0/bin/../data/deployments/health.topo.15e080a91c0
2017-08-22 03:44:25,045 INFO  knox.gateway (GatewayServer.java:internalDeactivateTopology(596)) - Deactivating topology health
2017-08-22 03:44:25,119 INFO  knox.gateway (DefaultGatewayServices.java:initializeContribution(197)) - Creating credential store for the cluster: health
2017-08-22 03:44:25,142 INFO  knox.gateway (GatewayServer.java:internalActivateTopology(566)) - Activating topology health
2017-08-22 03:44:25,142 INFO  knox.gateway (GatewayServer.java:internalActivateArchive(576)) - Activating topology health archive %2F

```
##### Verify

Once the health service is active, you can verify it by using the following *curl* command. The '*ping*' end point displays if the service is up. This end point can be utilized for monitoring the basic health of a Knox service.

```
$ curl -i -k -u guest:guest-password -X GET 'https://localhost:8445/gateway/health/v1/ping'
HTTP/1.1 200 OK
Date: Tue, 22 Aug 2017 07:09:37 GMT
Set-Cookie: JSESSIONID=1o82bcvoqbhbb1apt7zs8ubybb;Path=/gateway/health;Secure;HttpOnly
Expires: Thu, 01 Jan 1970 00:00:00 GMT
Set-Cookie: rememberMe=deleteMe; Path=/gateway/health; Max-Age=0; Expires=Mon, 21-Aug-2017 07:09:37 GMT
Cache-Control: must-revalidate,no-cache,no-store
Content-Type: text/plain; charset=ISO-8859-1
Content-Length: 3
Server: Jetty(9.2.15.v20160210)

OK
```

To retrieve the meaningful metrics details of various service calls, you may need to run multiple REST calls such as the followings. After that, execute the metrics REST call as shown below with a sample output. As shown, metrics output is returned in JSON format.

```
curl -i -k -u guest:guest-password -X GET 'https://localhost:8445/gateway/sandbox/webhdfs/v1/?op=LISTSTATUS'
```
```
$ curl -i -k -u guest:guest-password -X GET 'https://localhost:8445/gateway/health/v1/metrics?pretty=true'
HTTP/1.1 200 OK
Date: Tue, 22 Aug 2017 07:10:44 GMT
Set-Cookie: JSESSIONID=kqntcdaje9uai3pup7ffvfw4;Path=/gateway/health;Secure;HttpOnly
Expires: Thu, 01 Jan 1970 00:00:00 GMT
Set-Cookie: rememberMe=deleteMe; Path=/gateway/health; Max-Age=0; Expires=Mon, 21-Aug-2017 07:10:44 GMT
Content-Type: application/json
Cache-Control: must-revalidate,no-cache,no-store
Transfer-Encoding: chunked
Server: Jetty(9.2.15.v20160210)

{
  "version" : "3.0.0",
  "gauges" : { },
  "counters" : { },
  "histograms" : { },
  "meters" : { },
  "timers" : {
    "client./gateway/health/v1/metrics.GET-requests" : {
      "count" : 5,
      "max" : 0.624587973,
      "mean" : 0.027655743001736188,
      "min" : 0.006145587,
      "p50" : 0.010020548,
      "p75" : 0.010020548,
      "p95" : 0.074454725,
      "p98" : 0.624587973,
      "p99" : 0.624587973,
      "p999" : 0.624587973,
      "stddev" : 0.0929226225229978,
      "m15_rate" : 2.657500857422334E-7,
      "m1_rate" : 5.770087852901534E-89,
      "m5_rate" : 4.769163772973399E-19,
      "mean_rate" : 4.0952378345310894E-4,
      "duration_units" : "seconds",
      "rate_units" : "calls/second"
    },
    "client./gateway/health/v1/ping.GET-requests" : {
      "count" : 1,
      "max" : 0.017257638000000002,
      "mean" : 0.017257638000000002,
      "min" : 0.017257638000000002,
      "p50" : 0.017257638000000002,
      "p75" : 0.017257638000000002,
      "p95" : 0.017257638000000002,
      "p98" : 0.017257638000000002,
      "p99" : 0.017257638000000002,
      "p999" : 0.017257638000000002,
      "stddev" : 0.0,
      "m15_rate" : 0.18710139700632353,
      "m1_rate" : 0.0735758882342885,
      "m5_rate" : 0.1637461506155964,
      "mean_rate" : 0.014990517517814805,
      "duration_units" : "seconds",
      "rate_units" : "calls/second"
    },
    "client./gateway/sandbox/health/v1/.GET-requests" : {
      "count" : 1,
      "max" : 4.01873E-4,
      "mean" : 4.01873E-4,
      "min" : 4.01873E-4,
      "p50" : 4.01873E-4,
      "p75" : 4.01873E-4,
      "p95" : 4.01873E-4,
      "p98" : 4.01873E-4,
      "p99" : 4.01873E-4,
      "p999" : 4.01873E-4,
      "stddev" : 0.0,
      "m15_rate" : 2.536740427767808E-7,
      "m1_rate" : 7.074903404511115E-90,
      "m5_rate" : 4.081014139447941E-19,
      "mean_rate" : 8.179827684854002E-5,
      "duration_units" : "seconds",
      "rate_units" : "calls/second"
    },
    "client./gateway/sandbox/v1/health/.GET-requests" : {
      "count" : 1,
      "max" : 5.470700000000001E-4,
      "mean" : 5.470700000000001E-4,
      "min" : 5.470700000000001E-4,
      "p50" : 5.470700000000001E-4,
      "p75" : 5.470700000000001E-4,
      "p95" : 5.470700000000001E-4,
      "p98" : 5.470700000000001E-4,
      "p99" : 5.470700000000001E-4,
      "p999" : 5.470700000000001E-4,
      "stddev" : 0.0,
      "m15_rate" : 2.413022137213267E-7,
      "m1_rate" : 3.341947732164585E-90,
      "m5_rate" : 3.512561421726287E-19,
      "mean_rate" : 8.149518570285245E-5,
      "duration_units" : "seconds",
      "rate_units" : "calls/second"
    },
    "client./gateway/sandbox/webhdfs/v1/.GET-requests" : {
      "count" : 4,
      "max" : 0.463745401,
      "mean" : 0.024924118143299912,
      "min" : 0.016542244,
      "p50" : 0.024799078000000002,
      "p75" : 0.033933548,
      "p95" : 0.033933548,
      "p98" : 0.033933548,
      "p99" : 0.033933548,
      "p999" : 0.033933548,
      "stddev" : 0.007284773511002474,
      "m15_rate" : 2.120680068580741E-8,
      "m1_rate" : 4.7541228609699333E-91,
      "m5_rate" : 1.5806080232092864E-20,
      "mean_rate" : 2.7314359915623396E-4,
      "duration_units" : "seconds",
      "rate_units" : "calls/second"
    },
    "service./gateway/sandbox/webhdfs/v1/.get-requests" : {
      "count" : 3,
      "max" : 0.014635496000000001,
      "mean" : 0.00342438191233768,
      "min" : 0.0020088890000000002,
      "p50" : 0.0020088890000000002,
      "p75" : 0.005144646,
      "p95" : 0.005144646,
      "p98" : 0.005144646,
      "p99" : 0.005144646,
      "p999" : 0.005144646,
      "stddev" : 0.0015604555820128599,
      "m15_rate" : 1.9913776931949195E-8,
      "m1_rate" : 3.1334281325640874E-91,
      "m5_rate" : 1.055281734633953E-20,
      "mean_rate" : 2.0486339070804923E-4,
      "duration_units" : "seconds",
      "rate_units" : "calls/second"
    }
  }
}
```
#### REST End Points
As mentioned above, currently Knox provides a few monitoring APIs to start with. The list will gradually grow to support new use-cases.
##### /ping
This end-point can be used to determine if a Knox gateway service is alive or not. It is useful for basic health monitoring of the core service. Although most of the results of REST calls are in JSON format, this one (*/ping*) is in plain text.  

Sample response

```
OK
```

##### /metrics
This end-point returns all Knox metrics grouped by individual call type. For example, timer metrics for all *webhdfs* calls are aggregated into one set of metrics and then returned in a separate JSON element. This end-point also supports an option (*/metrics?pretty=true*) to pretty print the metrics output.

A sample response with *pretty=true* is shown below:

```
{
  "version" : "3.0.0",
  "gauges" : { },
  "counters" : { },
  "histograms" : { },
  "meters" : { },
  "timers" : {
    "client./gateway/health/v1/ping.GET-requests" : {
      "count" : 1,
      "max" : 0.017257638000000002,
      "mean" : 0.017257638000000002,
      "min" : 0.017257638000000002,
      "p50" : 0.017257638000000002,
      "p75" : 0.017257638000000002,
      "p95" : 0.017257638000000002,
      "p98" : 0.017257638000000002,
      "p99" : 0.017257638000000002,
      "p999" : 0.017257638000000002,
      "stddev" : 0.0,
      "m15_rate" : 0.18710139700632353,
      "m1_rate" : 0.0735758882342885,
      "m5_rate" : 0.1637461506155964,
      "mean_rate" : 0.014990517517814805,
      "duration_units" : "seconds",
      "rate_units" : "calls/second"
    },
    "client./gateway/sandbox/v1/health/.GET-requests" : {
      "count" : 1,
      "max" : 5.470700000000001E-4,
      "mean" : 5.470700000000001E-4,
      "min" : 5.470700000000001E-4,
      "p50" : 5.470700000000001E-4,
      "p75" : 5.470700000000001E-4,
      "p95" : 5.470700000000001E-4,
      "p98" : 5.470700000000001E-4,
      "p99" : 5.470700000000001E-4,
      "p999" : 5.470700000000001E-4,
      "stddev" : 0.0,
      "m15_rate" : 2.413022137213267E-7,
      "m1_rate" : 3.341947732164585E-90,
      "m5_rate" : 3.512561421726287E-19,
      "mean_rate" : 8.149518570285245E-5,
      "duration_units" : "seconds",
      "rate_units" : "calls/second"
    },
    "client./gateway/sandbox/webhdfs/v1/.GET-requests" : {
      "count" : 4,
      "max" : 0.463745401,
      "mean" : 0.024924118143299912,
      "min" : 0.016542244,
      "p50" : 0.024799078000000002,
      "p75" : 0.033933548,
      "p95" : 0.033933548,
      "p98" : 0.033933548,
      "p99" : 0.033933548,
      "p999" : 0.033933548,
      "stddev" : 0.007284773511002474,
      "m15_rate" : 2.120680068580741E-8,
      "m1_rate" : 4.7541228609699333E-91,
      "m5_rate" : 1.5806080232092864E-20,
      "mean_rate" : 2.7314359915623396E-4,
      "duration_units" : "seconds",
      "rate_units" : "calls/second"
    }
  }
}
```
