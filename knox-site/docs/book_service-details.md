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

## Service Details ##

In the sections that follow, the integrations currently available out of the box with the gateway will be described.
In general these sections will include examples that demonstrate how to access each of these services via the gateway.
In many cases this will include both the use of [cURL][curl] as a REST API client as well as the use of the Knox Client DSL.
You may notice that there are some minor differences between using the REST API of a given service via the gateway.
In general this is necessary in order to achieve the goal of not leaking internal Hadoop cluster details to the client.

Keep in mind that the gateway uses a plugin model for supporting Hadoop services.
Check back with the [Apache Knox][site] site for the latest news on plugin availability.
You can also create your own custom plugin to extend the capabilities of the gateway.

These are the current Hadoop services with built-in support.

* [WebHDFS](service_webhdfs.md)  
* [WebHCat](service_webhcat.md)  
* [Oozie](service_oozie.md)  
* [HBase](service_hbase.md)  
* [Hive](service_hive.md)  
* [Yarn](service_yarn.md)  
* [Kafka](service_kafka.md)  
* [Storm](service_storm.md)  
* [Solr](service_solr.md)  
* [Configuration](service_config.md)  
* [Default HA](service_default_ha.md)  
* [Avatica](service_avatica.md)  
* [Cloudera Manager](service_cloudera_manager.md)  
* [Livy](service_livy.md)  
* [Elasticsearch](service_elasticsearch.md)  
* [SSL Certificate Trust](service_ssl_certificate_trust.md)  
* [Service Test](service_service_test.md)

### Assumptions

This document assumes a few things about your environment in order to simplify the examples.

* The JVM is executable as simply `java`.
* The Apache Knox Gateway is installed and functional.
* The example commands are executed within the context of the `GATEWAY_HOME` current directory.
The `GATEWAY_HOME` directory is the directory within the Apache Knox Gateway installation that contains the README file and the bin, conf and deployments directories.
* The [cURL][curl] command line HTTP client utility is installed and functional.
* A few examples optionally require the use of commands from a standard Groovy installation.
These examples are optional but to try them you will need Groovy [installed](http://groovy.codehaus.org/Installing+Groovy).
* The default configuration for all of the samples is setup for use with Hortonworks' [Sandbox][sandbox] version 2.

### Customization

Using these samples with other Hadoop installations will require changes to the steps described here as well as changes to referenced sample scripts.
This will also likely require changes to the gateway's default configuration.
In particular host names, ports, user names and password may need to be changed to match your environment.
These changes may need to be made to gateway configuration and also the Groovy sample script files in the distribution.
All of the values that may need to be customized in the sample scripts can be found together at the top of each of these files.

### cURL

The cURL HTTP client command line utility is used extensively in the examples for each service.
In particular this form of the cURL command line is used repeatedly.

    curl -i -k -u guest:guest-password ...

The option `-i` (aka `--include`) is used to output HTTP response header information.
This will be important when the content of the HTTP Location header is required for subsequent requests.

The option `-k` (aka `--insecure`) is used to avoid any issues resulting from the use of demonstration SSL certificates.

The option `-u` (aka `--user`) is used to provide the credentials to be used when the client is challenged by the gateway.

Keep in mind that the samples do not use the cookie features of cURL for the sake of simplicity.
Therefore each request via cURL will result in an authentication.

