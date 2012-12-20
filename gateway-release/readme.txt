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
  Local installation of a Hadoop Cluster (EC2 instances and/or Sandbox installations will not work at this time)
  The EC2 and Sandbox limitation is caused by Hadoop services running with internal IP addresses.  The Gateway
  will work in these cases but it must be deployed on the EC2 cluster or Sandbox VM.  The instructions that
  follow assume that the Gateway is deployed externally from the Hadoop clusters.

  The Hadoop cluster should have WebHDFS and WebHCat (i.e. Templeton) deployed and configured.

------------------------------------------------------------------------------
Installation and Deployment Instructions
------------------------------------------------------------------------------

1. Install
     Download and extract the gateway-0.1.0-download.zip file into the installation directory that will contain your
     GATEWAY_HOME
2. Enter Gateway Home directory
     cd gateway-0.1.0
3. Start the demo LDAP server (ApacheDS)
   a. Make a backup copy of the {GATEWAY_HOME}/conf/users.ldif file and add your users and groups to the file
   b. Start the LDAP server - pointing it to the config dir where it will find the users.ldif file
        java -jar bin/gateway-test-ldap-0.1.0-SNAPSHOT.jar conf/ &
4. Start the Gateway server
     java -jar bin/gateway-test-server-0.1.0-SNAPSHOT.jar
5. Configure for the Management of your Hadoop Cluster
   a. Edit the file {GATEWAY_HOME}/resources/topology.xml
   b. Change the host and port in the url for the NAMENODE service to match your cluster deployment
        <service>
         <role>NAMENODE</role>
         <url>http://host:80/webhdfs/v1</url>
        </service>
   c. Save or copy this file to {GATEWAY_HOME}/deployments
      this directory is monitored by the Gateway server and reacts to the discovery of a topology descriptor by
      provisioning the endpoints and required filter chains to serve the needs of each cluster as described by the
      topology file.
6. Test the Installation and Configuration of your Hadoop Cluster within the Gateway process
   invoke the LISTSATUS operation on HDFS represented by your configured NAMENODE by using your web browser or curl:

     {!!!!!!!!!! TODO: BASIC and gateway URLS !!!!!!!!!!!!!!!!}
     curl -i -L http://vm-hdpt:50070/webhdfs/v1/?op=LISTSTATUS

   The results of the above command should result in something to along the lines of
   (details subject to the content within HDFS):

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
The Gateway functions in many ways as a reverse proxy.  As such it maintains a mapping of URLs that are exposed
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
The value for <cluster-name> is derrived from the name of the cluster topology descriptor
(e.g. {GATEWAY_HOME}/deployments/{cluster-name>.xml).
The value for <namenode-host> are provided via the cluster topology descriptor.
Note: The ports 50070 and 50111 are the defaults for NameNode and Templeton respectively.
      Their values can also be provided via the cluster topology descriptor.