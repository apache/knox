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
--->

### Admin API

Access to the administrator functions of Knox are provided by the Admin REST API.

#### Admin API URL

The URL mapping for the Knox Admin API is:

| Resource    | URL                                                                                     |
|-------------|-----------------------------------------------------------------------------------------|
| GatewayAPI  | `https://{gateway-host}:{gateway-port}/{gateway-path}/admin/api/v1`                     |

Please note that to access this API, the user attempting to connect must have admin credentials configured on the LDAP Server


##### API Documentation 

<table>
  <thead>
    <th>Resource</th>
    <th>Operation</th>
    <th>Description</th>
  </thead>
  <tr>
    <td>version</td>
    <td>GET</td>
    <td>Get the gateway version and the associated version hash</td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Request</td>
    <td><pre>curl -iku admin:admin-password {GatewayAPI}/version -H Accept:application/json</pre></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Response</td>
    <td>
      <pre>
{
  "ServerVersion" : {
    "version" : "VERSION_ID",
    "hash" : "VERSION_HASH"
  }
}     </pre>
    </td>
  </tr>

  <tr>
    <td>topologies</td>
    <td>GET</td>
    <td>Get an enumeration of the topologies currently deployed in the gateway.</td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Request</td>
    <td><pre>curl -iku admin:admin-password {GatewayAPI}/topologies -H Accept:application/json</pre></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Response</td>
    <td>
      <pre>
{
   "topologies" : {
      "topology" : [ {
         "name" : "admin",
         "timestamp" : "1501508536000",
         "uri" : "https://localhost:8443/gateway/admin",
         "href" : "https://localhost:8443/gateway/admin/api/v1/topologies/admin"
      }, {
         "name" : "sandbox",
         "timestamp" : "1501508536000",
         "uri" : "https://localhost:8443/gateway/sandbox",
         "href" : "https://localhost:8443/gateway/admin/api/v1/topologies/sandbox"
      } ]
   }
}     </pre>
    </td>
  </tr>

  <tr>
    <td>topologies/{id}</td>
    <td>GET</td>
    <td>Get a JSON representation of the specified topology</td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Request</td>
    <td><pre>curl -iku admin:admin-password {GatewayAPI}/topologies/admin -H Accept:application/json</pre></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Response</td>
    <td>
      <pre>
{
  "name": "admin",
  "providers": [{
    "enabled": true,
    "name": "ShiroProvider",
    "params": {
      "sessionTimeout": "30",
      "main.ldapRealm": "org.apache.knox.gateway.shirorealm.KnoxLdapRealm",
      "main.ldapRealm.userDnTemplate": "uid={0},ou=people,dc=hadoop,dc=apache,dc=org",
      "main.ldapRealm.contextFactory.url": "ldap://localhost:33389",
      "main.ldapRealm.contextFactory.authenticationMechanism": "simple",
      "urls./**": "authcBasic"
    },
    "role": "authentication"
  }, {
    "enabled": true,
    "name": "AclsAuthz",
    "params": {
      "knox.acl": "admin;*;*"
    },
    "role": "authorization"
  }, {
    "enabled": true,
    "name": "Default",
    "params": {},
    "role": "identity-assertion"
  }, {
    "enabled": true,
    "name": "static",
    "params": {
      "localhost": "sandbox,sandbox.hortonworks.com"
    },
    "role": "hostmap"
  }],
  "services": [{
      "name": null,
      "params": {},
      "role": "KNOX",
      "url": null
  }],
  "timestamp": 1406672646000,
  "uri": "https://localhost:8443/gateway/admin"
}     </pre>
    </td>
  </tr>

  <tr>
    <td>&nbsp;</td>
    <td>PUT</td>
    <td>Add (and deploy) a topology</td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Request</td>
    <td><pre>curl -iku admin:admin-password {GatewayAPI}/topologies/mytopology \
     -X PUT \
     -H Content-Type:application/xml
     -d "@mytopology.xml"</pre></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Response</td>
    <td>
        <pre>
&lt;?xml version="1.0" encoding="UTF-8"?&gt;
&lt;topology&gt;
   &lt;uri&gt;https://localhost:8443/gateway/mytopology&lt;/uri&gt;
   &lt;name&gt;mytopology&lt;/name&gt;
   &lt;timestamp&gt;1509720338000&lt;/timestamp&gt;
   &lt;gateway&gt;
      &lt;provider&gt;
         &lt;role&gt;authentication&lt;/role&gt;
         &lt;name&gt;ShiroProvider&lt;/name&gt;
         &lt;enabled&gt;true&lt;/enabled&gt;
         &lt;param&gt;
            &lt;name&gt;sessionTimeout&lt;/name&gt;
            &lt;value&gt;30&lt;/value&gt;
         &lt;/param&gt;
         &lt;param&gt;
            &lt;name&gt;main.ldapRealm&lt;/name&gt;
            &lt;value&gt;org.apache.knox.gateway.shirorealm.KnoxLdapRealm&lt;/value&gt;
         &lt;/param&gt;
         &lt;param&gt;
            &lt;name&gt;main.ldapContextFactory&lt;/name&gt;
            &lt;value&gt;org.apache.knox.gateway.shirorealm.KnoxLdapContextFactory&lt;/value&gt;
         &lt;/param&gt;
         &lt;param&gt;
            &lt;name&gt;main.ldapRealm.contextFactory&lt;/name&gt;
            &lt;value&gt;$ldapContextFactory&lt;/value&gt;
         &lt;/param&gt;
         &lt;param&gt;
            &lt;name&gt;main.ldapRealm.userDnTemplate&lt;/name&gt;
            &lt;value&gt;uid={0},ou=people,dc=hadoop,dc=apache,dc=org&lt;/value&gt;
         &lt;/param&gt;
         &lt;param&gt;
            &lt;name&gt;main.ldapRealm.contextFactory.url&lt;/name&gt;
            &lt;value&gt;ldap://localhost:33389&lt;/value&gt;
         &lt;/param&gt;
         &lt;param&gt;
            &lt;name&gt;main.ldapRealm.contextFactory.authenticationMechanism&lt;/name&gt;
            &lt;value&gt;simple&lt;/value&gt;
         &lt;/param&gt;
         &lt;param&gt;
            &lt;name&gt;urls./**&lt;/name&gt;
            &lt;value&gt;authcBasic&lt;/value&gt;
         &lt;/param&gt;
      &lt;/provider&gt;
      &lt;provider&gt;
         &lt;role&gt;identity-assertion&lt;/role&gt;
         &lt;name&gt;Default&lt;/name&gt;
         &lt;enabled&gt;true&lt;/enabled&gt;
      &lt;/provider&gt;
      &lt;provider&gt;
         &lt;role&gt;hostmap&lt;/role&gt;
         &lt;name&gt;static&lt;/name&gt;
         &lt;enabled&gt;true&lt;/enabled&gt;
         &lt;param&gt;
            &lt;name&gt;localhost&lt;/name&gt;
            &lt;value&gt;sandbox,sandbox.hortonworks.com&lt;/value&gt;
         &lt;/param&gt;
      &lt;/provider&gt;
   &lt;/gateway&gt;
   &lt;service&gt;
      &lt;role&gt;NAMENODE&lt;/role&gt;
      &lt;url&gt;hdfs://localhost:8020&lt;/url&gt;
   &lt;/service&gt;
   &lt;service&gt;
      &lt;role&gt;JOBTRACKER&lt;/role&gt;
      &lt;url&gt;rpc://localhost:8050&lt;/url&gt;
   &lt;/service&gt;
   &lt;service&gt;
      &lt;role&gt;WEBHDFS&lt;/role&gt;
      &lt;url&gt;http://localhost:50070/webhdfs&lt;/url&gt;
   &lt;/service&gt;
   &lt;service&gt;
      &lt;role&gt;WEBHCAT&lt;/role&gt;
      &lt;url&gt;http://localhost:50111/templeton&lt;/url&gt;
   &lt;/service&gt;
   &lt;service&gt;
      &lt;role&gt;OOZIE&lt;/role&gt;
      &lt;url&gt;http://localhost:11000/oozie&lt;/url&gt;
   &lt;/service&gt;
   &lt;service&gt;
      &lt;role&gt;WEBHBASE&lt;/role&gt;
      &lt;url&gt;http://localhost:60080&lt;/url&gt;
   &lt;/service&gt;
   &lt;service&gt;
      &lt;role&gt;HIVE&lt;/role&gt;
      &lt;url&gt;http://localhost:10001/cliservice&lt;/url&gt;
   &lt;/service&gt;
   &lt;service&gt;
      &lt;role&gt;RESOURCEMANAGER&lt;/role&gt;
      &lt;url&gt;http://localhost:8088/ws&lt;/url&gt;
   &lt;/service&gt;
&lt;/topology&gt;</pre>
    </td>
  </tr>

  <tr>
    <td>&nbsp;</td>
    <td>DELETE</td>
    <td>Delete (and undeploy) a topology</td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Request</td>
    <td><pre>curl -iku admin:admin-password {GatewayAPI}/topologies/mytopology -X DELETE</pre></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Response</td>
    <td><pre>{ "deleted" : true }</pre></td>
  </tr>

  <tr>
    <td>providerconfig</td>
    <td>GET</td>
    <td>Get an enumeration of the shared provider configurations currently deployed to the gateway.</td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Request</td>
    <td><pre>curl -iku admin:admin-password {GatewayAPI}/providerconfig</pre></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Response</td>
    <td>
      <pre>
{
  "href" : "https://localhost:8443/gateway/admin/api/v1/providerconfig",
  "items" : [ {
    "href" : "https://localhost:8443/gateway/admin/api/v1/providerconfig/myproviders",
    "name" : "myproviders.xml"
  },{
   "href" : "https://localhost:8443/gateway/admin/api/v1/providerconfig/sandbox-providers",
   "name" : "sandbox-providers.xml"
  } ]
}     </pre>
    </td>
  </tr>

  <tr>
    <td>providerconfig/{id}</td>
    <td>GET</td>
    <td>Get the XML content of the specified shared provider configuration.</td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Request</td>
    <td><pre>curl -iku admin:admin-password {GatewayAPI}/providerconfig/sandbox-providers \
     -H Accept:application/xml</pre></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Response</td>
    <td>
      <pre>
&lt;gateway&gt;
    &lt;provider&gt;
        &lt;role&gt;authentication&lt;/role&gt;
        &lt;name&gt;ShiroProvider&lt;/name&gt;
        &lt;enabled&gt;true&lt;/enabled&gt;
        &lt;param&gt;
            &lt;name&gt;sessionTimeout&lt;/name&gt;
            &lt;value&gt;30&lt;/value&gt;
        &lt;/param&gt;
        &lt;param&gt;
            &lt;name&gt;main.ldapRealm&lt;/name&gt;
            &lt;value&gt;org.apache.knox.gateway.shirorealm.KnoxLdapRealm&lt;/value&gt;
        &lt;/param&gt;
        &lt;param&gt;
            &lt;name&gt;main.ldapContextFactory&lt;/name&gt;
            &lt;value&gt;org.apache.knox.gateway.shirorealm.KnoxLdapContextFactory&lt;/value&gt;
        &lt;/param&gt;
        &lt;param&gt;
            &lt;name&gt;main.ldapRealm.contextFactory&lt;/name&gt;
            &lt;value&gt;$ldapContextFactory&lt;/value&gt;
        &lt;/param&gt;
        &lt;param&gt;
            &lt;name&gt;main.ldapRealm.userDnTemplate&lt;/name&gt;
            &lt;value&gt;uid={0},ou=people,dc=hadoop,dc=apache,dc=org&lt;/value&gt;
        &lt;/param&gt;
        &lt;param&gt;
            &lt;name&gt;main.ldapRealm.contextFactory.url&lt;/name&gt;
            &lt;value&gt;ldap://localhost:33389&lt;/value&gt;
        &lt;/param&gt;
        &lt;param&gt;
            &lt;name&gt;main.ldapRealm.contextFactory.authenticationMechanism&lt;/name&gt;
            &lt;value&gt;simple&lt;/value&gt;
        &lt;/param&gt;
        &lt;param&gt;
            &lt;name&gt;urls./**&lt;/name&gt;
            &lt;value&gt;authcBasic&lt;/value&gt;
        &lt;/param&gt;
    &lt;/provider&gt;

    &lt;provider&gt;
        &lt;role&gt;identity-assertion&lt;/role&gt;
        &lt;name&gt;Default&lt;/name&gt;
        &lt;enabled&gt;true&lt;/enabled&gt;
    &lt;/provider&gt;

    &lt;provider&gt;
        &lt;role&gt;hostmap&lt;/role&gt;
        &lt;name&gt;static&lt;/name&gt;
        &lt;enabled&gt;true&lt;/enabled&gt;
        &lt;param&gt;
            &lt;name&gt;localhost&lt;/name&gt;
            &lt;value&gt;sandbox,sandbox.hortonworks.com&lt;/value&gt;
        &lt;/param&gt;
    &lt;/provider&gt;
&lt;/gateway&gt;</pre>
    </td>
  </tr>
  </tr>
    <td>&nbsp;</td>
    <td>PUT</td>
    <td>Add a shared provider configuration.</td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Request</td>
    <td><pre>curl -iku admin:admin-password {GatewayAPI}/providerconfig/sandbox-providers \
     -X PUT \ 
     -H Content-Type:application/xml \
     -d "@sandbox-providers.xml"</pre></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Response</td>
    <td><pre>HTTP 201 Created</pre></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>DELETE</td>
    <td>Delete a shared provider configuration</td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Request</td>
    <td><pre>curl -iku admin:admin-password {GatewayAPI}/providerconfig/sandbox-providers -X DELETE</pre></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Response</td>
    <td>
      <pre>{ "deleted" : "provider config sandbox-providers" }</pre>
    </td>
  </tr>

  <tr>
    <td>descriptors</td>
    <td>GET</td>
    <td>Get an enumeration of the simple descriptors currently deployed to the gateway.</td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Request</td>
    <td><pre>curl -iku admin:admin-password {GatewayAPI}/descriptors -H Accept:application/json</pre></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Response</td>
    <td>
      <pre>
{
   "href" : "https://localhost:8443/gateway/admin/api/v1/descriptors",
   "items" : [ {
      "href" : "https://localhost:8443/gateway/admin/api/v1/descriptors/docker-sandbox",
      "name" : "docker-sandbox.json"
   }, {
      "href" : "https://localhost:8443/gateway/admin/api/v1/descriptors/mytopology",
      "name" : "mytopology.yml"
   } ]
}     </pre>
    </td>
  </tr>

  <tr>
    <td>descriptors/{id}</td>
    <td>GET</td>
    <td>Get the content of the specified descriptor.</td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Request</td>
    <td><pre>curl -iku admin:admin-password {GatewayAPI}/descriptors/docker-sandbox \
     -H Accept:application/json</pre></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Response</td>
    <td>
      <pre>
{
  "discovery-type":"AMBARI",
  "discovery-address":"http://sandbox.hortonworks.com:8080",
  "provider-config-ref":"sandbox-providers",
  "cluster":"Sandbox",
  "services":[
    {"name":"NAMENODE"},
    {"name":"JOBTRACKER"},
    {"name":"WEBHDFS"},
    {"name":"WEBHCAT"},
    {"name":"OOZIE"},
    {"name":"WEBHBASE"},
    {"name":"HIVE"},
    {"name":"RESOURCEMANAGER"} ]
}    </pre>
    </td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>PUT</td>
    <td>Add a simple descriptor (and generate and deploy a full topology descriptor).</td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Request</td>
    <td><pre>curl -iku admin:admin-password {GatewayAPI}/descriptors/docker-sandbox \
     -X PUT \
     -H Content-Type:application/json \
     -d "@docker-sandbox.json"</pre></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Response</td>
    <td><pre>HTTP 201 Created</pre></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>DELETE</td>
    <td>Delete a simple descriptor (and undeploy the associated topology)</td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Request</td>
    <td><pre>curl -iku admin:admin-password {GatewayAPI}/descriptors/docker-sandbox -X DELETE</pre></td>
  <tr>
  </tr>
    <td>&nbsp;</td>
    <td>Example Response</td>
    <td>
      <pre>{ "deleted" : "descriptor docker-sandbox" }</pre>
    </td>
  </tr>

  <tr>
    <td>aliases/{topology}</td>
    <td>GET</td>
    <td>Get the aliases associated with the specified topology.</td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Request</td>
    <td><pre>curl -iku admin:admin-password {GatewayAPI}/aliases/sandbox</pre></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Response</td>
    <td>
      <pre>
{
  "topology":"sandbox",
  "aliases":["myalias","encryptquerystring"]
}
	  </pre>
    </td>
  </tr>

  <tr>
    <td>aliases/{topology}/{alias}</td>
    <td>PUT</td>
    <td>Add the specified alias for the specified topology.</td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Request</td>
    <td><pre>curl -iku admin:admin-password {GatewayAPI}/aliases/sandbox/putalias -X PUT \
     -H "Content-Type: application/json" \
     -d "value=mysecret"</pre>
    </td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Response</td>
    <td>
      <pre>
{
  "created" : {
    "topology": "sandbox",
    "alias": "putalias"
  }
}</pre>
    </td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>POST</td>
    <td>Add the specified alias for the specified topology.</td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Request</td>
    <td><pre>curl -iku admin:admin-password {GatewayAPI}/aliases/sandbox/postalias -X POST \
     -H "Content-Type: application/json" \
     -d "value=mysecret"</pre>
    </td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Response</td>
    <td>
      <pre>
{
  "created" : {
    "topology": "sandbox",
    "alias": "postalias"
  }
}</pre>
    </td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>DELETE</td>
    <td>Remove the specified alias for the specified topology.</td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Request</td>
    <td><pre>curl -iku admin:admin-password {GatewayAPI}/aliases/sandbox/myalias -X DELETE</pre></td>
  </tr>
  <tr>
    <td>&nbsp;</td>
    <td>Example Response</td>
    <td>
	  <pre>
{
  "deleted" : {
    "topology": "sandbox",
    "alias": "myalias"
  }
}</pre></td>
  </tr>

</table>



