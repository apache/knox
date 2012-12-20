------------------------------------------------------------------------------
README file for Hadoop Gateway v0.1.0
------------------------------------------------------------------------------
This distribution includes cryptographic software.  The country in 
which you currently reside may have restrictions on the import, 
possession, use, and/or re-export to another country, of 
encryption software.  BEFORE using any encryption software, please 
check your country's laws, regulations and policies concerning the
import, possession, or use, and re-export of encryption software, to 
see if this is permitted.  See <http://www.wassenaar.org/> for more
information.

The U.S. Government Department of Commerce, Bureau of Industry and
Security (BIS), has classified this software as Export Commodity 
Control Number (ECCN) 5D002.C.1, which includes information security
software using or performing cryptographic functions with asymmetric
algorithms.  The form and manner of this Apache Software Foundation
distribution makes it eligible for export under the License Exception
ENC Technology Software Unrestricted (TSU) exception (see the BIS 
Export Administration Regulations, Section 740.13) for both object 
code and source code.

The following provides more details on the included cryptographic
software:
  This package includes the use of ApacheDS which is dependent upon the 
Bouncy Castle Crypto APIs written by the Legion of the Bouncy Castle
http://www.bouncycastle.org/ feedback-crypto@bouncycastle.org.

------------------------------------------------------------------------------
Description
------------------------------------------------------------------------------
The charter for the Gateway project is to simplify and normalize the deployment
and implementation of secure Hadoop clusters as well as be a centralize access point
for the service specific REST APIs exposed from within the cluster.

Milestone-1 of this project intends to demonstrate the ability to dynamically
provision reverse proxy capabilities with filter chains that meet the cluster
specific needs for authentication.

BASIC authentication with identity being asserted to the rest of the cluster 
via Pseudo/Simple authentication will be demonstrated for security.

For API aggregation, the Gateway will provide a central endpoint for HDFS and
Templeton APIs for each cluster.

Future Milestone releases will extend these capabilities with additional
authentication, identity assertion, API aggregation and eventually management
capabilities.

------------------------------------------------------------------------------
Requirements
------------------------------------------------------------------------------
Java: 
  Java 1.6 or later

Hadoop Cluster:
  A local installation of a Hadoop Cluster is required at this time.  Hadoop EC2 cluster and/or Sandbox installations
  are currently difficult to access remotely via the Gateway.   The EC2 and Sandbox limitation is caused by Hadoop
  services running with internal IP addresses.  For the Gateway to work in these cases it will need to be deployed
  on the EC2 cluster or Sandbox.  The instructions that follow assume that the Gateway is deployed externally from
  the Hadoop clusters.  Externally in this case is likely to be

  The Hadoop cluster should have WebHDFS and WebHCat (i.e. Templeton) deployed and configured.

------------------------------------------------------------------------------
Know Issues
------------------------------------------------------------------------------
Currently there is an issue with submitting Java MapReduce jobs via the WebHCat REST APIs.  Therefore step 7 in the
Example section currently fails.

The Gateway cannot be be used against either an EC2 cluster or Hadoop Sandbox unless the gateway is deployed in the
EC2 cluster or the on the Sandbox VM.

------------------------------------------------------------------------------
Installation and Deployment Instructions
------------------------------------------------------------------------------

1. Install
     Download and extract the gateway-0.1.0-SNAPSHOT.zip file into the installation directory that will contain your
     GATEWAY_HOME
       jar xf gateway-0.1.0-SNAPSHOT.zip
     This will create a directory 'gateway' in your current directory.

2. Enter Gateway Home directory
     cd gateway
   The fully qualified name of this directory will be referenced as {GATEWAY_HOME} throughout the remainder of this
   document.

3. Start the demo LDAP server (ApacheDS)
   a. The default configuration edited above contains the LDAP URL for a LDAP server.  By default that file
      is configured to access this simple ApacheDS based LDAP server and its default configuration.  Specifically,
      by default this server listens on port 33389.
   b. Edit {GATEWAY_HOME}/conf/users.ldif if required and add your users and groups to the file.
      A number of normal Hadoop users (e.g. hdfs, mapred, hcat, hive) have already been included.  Note that
      the passwords in this file are "fictitious" and have nothing to do with the actual accounts on the Hadoop
      cluster you are using.  There is also a copy of this file in the templates directory that you can use to
      start over if necessary.
   c. Start the LDAP server - pointing it to the config dir where it will find the users.ldif file in the conf
      directory.
        java -jar bin/gateway-test-ldap-0.1.0-SNAPSHOT.jar conf &
      There are a number of messages of the form "Created null." that can safely be ignored.
      Take note of the port on which it was started as this needs to match later configuration.
      This will create a directory named 'org.apache.hadoop.gateway.security.EmbeddedApacheDirectoryServer' that
      can safely be ignored.

4. Start the Gateway server
     java -jar bin/gateway-server-0.1.0-SNAPSHOT.jar
   Take note of the port identified in the logging output as you will need this for accessing the gateway.

5. Configure the Gateway with the topology of your Hadoop cluster
   a. Edit the file {GATEWAY_HOME}/deployments/sample.xml
   b. Change the host and port in the urls of the <service> elements for NAMENODE and TEMPLETON service to match your
      cluster deployment.
   c. Optionally you can change the LDAP URL for the LDAP server to be used for authentication.  This is set via
      the main.ldapRealm.contextFactory.url property in the <gateway><provider><authentication> section.
   d. Save the file.  The directory {GATEWAY_HOME}/deployments is monitored by the Gateway server and reacts to the
      discovery of a new or changed cluster topology descriptor by provisioning the endpoints and required filter
      chains to serve the needs of each cluster as described by the topology file.  Note that the name of the file
      excluding the extension is also used as the path for that cluster in the URL.  So for example the sample.xml
      file will result in Gateway URLs of the form
        http://{gateway-host}:{gateway-port}/gateway/sample/namenode/api/v1

6. Test the installation and configuration of your Gateway
   Invoke the LISTSATUS operation on HDFS represented by your configured NAMENODE by using your web browser or curl:

     curl --user hdfs:hdfs-password -i -L http://localhost:8888/gateway/sample/namenode/api/v1/tmp?op=LISTSTATUS

   The results of the above command should result in something to along the lines of the output below.  The exact
   information returned is subject to the content within HDFS in your Hadoop cluster.

     HTTP/1.1 200 OK
       Content-Type: application/json
       Content-Length: 760
       Server: Jetty(6.1.26)

     {"FileStatuses":{"FileStatus":[
     {"accessTime":0,"blockSize":0,"group":"hdfs","length":0,"modificationTime":1350595859762,"owner":"hdfs","pathSuffix":"apps","permission":"755","replication":0,"type":"DIRECTORY"},
     {"accessTime":0,"blockSize":0,"group":"mapred","length":0,"modificationTime":1350595874024,"owner":"mapred","pathSuffix":"mapred","permission":"755","replication":0,"type":"DIRECTORY"},
     {"accessTime":0,"blockSize":0,"group":"hdfs","length":0,"modificationTime":1350596040075,"owner":"hdfs","pathSuffix":"tmp","permission":"777","replication":0,"type":"DIRECTORY"},
     {"accessTime":0,"blockSize":0,"group":"hdfs","length":0,"modificationTime":1350595857178,"owner":"hdfs","pathSuffix":"user","permission":"755","replication":0,"type":"DIRECTORY"}
     ]}}

   For additional information on HDFS and Templeton APIs, see the following URLs respectively:

   http://hadoop.apache.org/docs/r1.0.4/webhdfs.html
     and
   http://people.apache.org/~thejas/templeton_doc_v1/

------------------------------------------------------------------------------
Mapping Gateway URLs to Hadoop cluster URLs
------------------------------------------------------------------------------
The Gateway functions in much like a reverse proxy.  As such it maintains a mapping of URLs that are exposed
externally by the Gateway to URLs that are provided by the Hadoop cluster.  Examples of mappings for the NameNode and
Templeton are shown below.  These mapping are generated from the combination of the Gateway configuration file
(i.e. {GATEWAY_HOME}/gateway-site.xml) and the cluster topology descriptors
(e.g. {GATEWAY_HOME}/deployments/<cluster-name>.xml}.

  HDFS (NameNode)
    Gateway: http://<gateway-host>:<gateway-port>/<gateway-path>/<cluster-name>namenode/api/v1
    Cluster: http://<namenode-host>:50070/webhdfs/v1
  Templeton
    Gateway: http://<gateway-host>:<gateway-port>/<gateway-path>/<cluster-name>templeton/api/v1
    Cluster: http://<templeton-host>:50111/templeton/v1

The values for <gateway-host>, <gateway-port>, <gateway-path> are provided via the Gateway configuration file
(i.e. {GATEWAY_HOME}/gateway-site.xml).
The value for <cluster-name> is derived from the name of the cluster topology descriptor
(e.g. {GATEWAY_HOME}/deployments/{cluster-name>.xml).
The value for <namenode-host> are provided via the cluster topology descriptor.
Note: The ports 50070 and 50111 are the defaults for NameNode and Templeton respectively.
      Their values can also be provided via the cluster topology descriptor.

------------------------------------------------------------------------------
Enabling logging
------------------------------------------------------------------------------
If necessary you can enable additional logging by editing the log4j.properties file in the conf directory.
Changing the rootLogger value from ERROR to DEBUG will generate a large amount of debug logging.  A number
of useful, more fine loggers are also provided in the file.

------------------------------------------------------------------------------
Filing bugs
------------------------------------------------------------------------------
File bugs at hortonworks.jira.com under Project "Hadoop Gateway Development"
Include the results of
  java -jar bin/gateway-server-0.1.0-SNAPSHOT.jar -version
in the Environment section.  Also include the version of Hadoop that you are using there as well.

------------------------------------------------------------------------------
Example
------------------------------------------------------------------------------
The example below illustrates the sequence of curl commands that could be used to run a word count job.  It utilizes
the hadoop-examples.jar from a Hadoop install for running a simple word count job.  Take care to follow the
instructions below for steps 4/5 and 6/7 where the Location header returned by the call to the NameNode is copied for
use with the call to the DataNode that follows it.

# 1. Create a test input directory /tmp/test/input
curl -i -u mapred:mapred-password -X PUT \
  'http://localhost:8888/gateway/sample/namenode/api/v1/tmp/test/input?op=MKDIRS'

# 2. Create a test output directory /tmp/test/input
curl -i -u mapred:mapred-password -X PUT \
  'http://localhost:8888/gateway/sample/namenode/api/v1/tmp/test/output?op=MKDIRS'

# 3. Create the inode for hadoop-examples.jar in /tmp/test
curl -i -u mapred:mapred-password -X PUT \
  'http://localhost:8888/gateway/sample/namenode/api/v1/tmp/test/hadoop-examples.jar?op=CREATE'

# 4. Upload hadoop-examples.jar to /tmp/test.  Use a hadoop-examples.jar from a Hadoop install.
curl -i -u mapred:mapred-password -T hadoop-examples.jar -X PUT '{Value Location header from command above}'

# 5. Create the inode for a sample file readme.txt in /tmp/test/input
curl -i -u mapred:mapred-password -X PUT \
  'http://localhost:8888/gateway/sample/namenode/api/v1/tmp/test/input/readme.txt?op=CREATE'

# 6. Upload readme.txt to /tmp/test/input.  Use the readme.txt in {GATEWAY_HOME}.
curl -i -u mapred:mapred-password -T readme.txt -X PUT '{Value of Location header from command above}'

# 7. Submit the word count job
curl -i -u mapred:mapred-password -X POST \
  -d jar=/tmp/test/hadoop-examples.jar -d class=org.apache.org.apache.hadoop.examples.WordCount \
  -d arg=/tmp/test/input -d arg=/tmp/test/output \
  'http://localhost:8888/gateway/sample/templeton/api/v1/mapreduce/jar'

# 8. Look at the status of the job queue
curl -i -u mapred:mapred-password -X GET \
  'http://localhost:8888/gateway/sample/templeton/api/v1/queue'

# 9. List the contents of the output directory /tmp/test/output
curl -i -u mapred:mapred-password -X GET \
  'http://localhost:8888/gateway/sample/namenode/api/v1/tmp/test/output?op=LISTSTATUS'

# 10. Cleanup the test directory
curl -i -u mapred:mapred-password -X DELETE \
  'http://localhost:8888/gateway/sample/namenode/api/v1/tmp/test?op=DELETE&recursive=true'
