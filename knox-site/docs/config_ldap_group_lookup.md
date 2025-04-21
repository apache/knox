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

### LDAP Group Lookup ###

Knox can be configured to look up LDAP groups that the authenticated user belong to.
Knox can look up both Static LDAP Groups and Dynamic LDAP Groups.
The looked up groups are populated as Principal(s) in the Java Subject of the authenticated user.
Therefore service authorization rules can be defined in terms of LDAP groups looked up from a LDAP directory.

To look up LDAP groups of authenticated user from LDAP, you have to use `org.apache.knox.gateway.shirorealm.KnoxLdapRealm` in Shiro configuration.

Please see below a sample Shiro configuration snippet from a topology file that was tested looking LDAP groups.

    <provider>
        <role>authentication</role>
        <name>ShiroProvider</name>
        <enabled>true</enabled>
        <!-- 
        session timeout in minutes,  this is really idle timeout,
        defaults to 30mins, if the property value is not defined,, 
        current client authentication would expire if client idles continuously for more than this value
        -->
        <!-- defaults to: 30 minutes
        <param>
            <name>sessionTimeout</name>
            <value>30</value>
        </param>
        -->

        <!--
          Use single KnoxLdapRealm to do authentication and ldap group look up
        -->
        <param>
            <name>main.ldapRealm</name>
            <value>org.apache.knox.gateway.shirorealm.KnoxLdapRealm</value>
        </param>
        <param>
            <name>main.ldapGroupContextFactory</name>
            <value>org.apache.knox.gateway.shirorealm.KnoxLdapContextFactory</value>
        </param>
        <param>
            <name>main.ldapRealm.contextFactory</name>
            <value>$ldapGroupContextFactory</value>
        </param>
        <!-- defaults to: simple
        <param>
            <name>main.ldapRealm.contextFactory.authenticationMechanism</name>
            <value>simple</value>
        </param>
        -->
        <param>
            <name>main.ldapRealm.contextFactory.url</name>
            <value>ldap://localhost:33389</value>
        </param>
        <param>
            <name>main.ldapRealm.userDnTemplate</name>
            <value>uid={0},ou=people,dc=hadoop,dc=apache,dc=org</value>
        </param>

        <param>
            <name>main.ldapRealm.authorizationEnabled</name>
            <!-- defaults to: false -->
            <value>true</value>
        </param>
        <!-- defaults to: simple
        <param>
            <name>main.ldapRealm.contextFactory.systemAuthenticationMechanism</name>
            <value>simple</value>
        </param>
        -->
        <param>
            <name>main.ldapRealm.searchBase</name>
            <value>ou=groups,dc=hadoop,dc=apache,dc=org</value>
        </param>
        <!-- defaults to: groupOfNames
        <param>
            <name>main.ldapRealm.groupObjectClass</name>
            <value>groupOfNames</value>
        </param>
        -->
        <!-- defaults to: member
        <param>
            <name>main.ldapRealm.memberAttribute</name>
            <value>member</value>
        </param>
        -->
        <param>
             <name>main.cacheManager</name>
             <value>org.apache.shiro.cache.MemoryConstrainedCacheManager</value>
        </param>
        <param>
            <name>main.securityManager.cacheManager</name>
            <value>$cacheManager</value>
        </param>
        <param>
            <name>main.ldapRealm.memberAttributeValueTemplate</name>
            <value>uid={0},ou=people,dc=hadoop,dc=apache,dc=org</value>
        </param>
        <!-- the above element is the template for most ldap servers 
            for active directory use the following instead and
            remove the above configuration.
        <param>
            <name>main.ldapRealm.memberAttributeValueTemplate</name>
            <value>cn={0},ou=people,dc=hadoop,dc=apache,dc=org</value>
        </param>
        -->
        <param>
            <name>main.ldapRealm.contextFactory.systemUsername</name>
            <value>uid=guest,ou=people,dc=hadoop,dc=apache,dc=org</value>
        </param>
        <param>
            <name>main.ldapRealm.contextFactory.systemPassword</name>
            <value>${ALIAS=ldcSystemPassword}</value>
        </param>

        <param>
            <name>urls./**</name> 
            <value>authcBasic</value>
        </param>

    </provider>

The configuration shown above would look up Static LDAP groups of the authenticated user and populate the group principals in the Java Subject corresponding to the authenticated user.

If you want to look up Dynamic LDAP Groups instead of Static LDAP Groups, you would have to specify groupObjectClass and memberAttribute params as shown below:

    <param>
        <name>main.ldapRealm.groupObjectClass</name>
        <value>groupOfUrls</value>
    </param>
    <param>
        <name>main.ldapRealm.memberAttribute</name>
        <value>memberUrl</value>
    </param>

### Template topology files and LDIF files to try out LDAP Group Look up ###

Knox bundles some template topology files and ldif files that you can use to try and test LDAP Group Lookup and associated authorization ACLs.
All these template files are located under `{GATEWAY_HOME}/templates`.


#### LDAP Static Group Lookup Templates, authentication and group lookup from the same directory ####

* topology file: sandbox.knoxrealm1.xml
* ldif file: users.ldapgroups.ldif

To try this out

    cd {GATEWAY_HOME}
    cp templates/sandbox.knoxrealm1.xml conf/topologies/sandbox.xml
    cp templates/users.ldapgroups.ldif conf/users.ldif
    java -jar bin/ldap.jar conf
    java -Dsandbox.ldcSystemPassword=guest-password -jar bin/gateway.jar -persist-master

Following call to WebHDFS should report HTTP/1.1 401 Unauthorized
As guest is not a member of group "analyst", authorization provider states user should be member of group "analyst"

    curl  -i -v  -k -u guest:guest-password  -X GET https://localhost:8443/gateway/sandbox/webhdfs/v1?op=GETHOMEDIRECTORY

Following call to WebHDFS should report: {"Path":"/user/sam"}
As sam is a member of group "analyst", authorization provider states user should be member of group "analyst"

    curl  -i -v  -k -u sam:sam-password  -X GET https://localhost:8443/gateway/sandbox/webhdfs/v1?op=GETHOMEDIRECTORY


#### LDAP Static Group Lookup Templates, authentication and group lookup from different  directories ####

* topology file: sandbox.knoxrealm2.xml
* ldif file: users.ldapgroups.ldif

To try this out

    cd {GATEWAY_HOME}
    cp templates/sandbox.knoxrealm2.xml conf/topologies/sandbox.xml
    cp templates/users.ldapgroups.ldif conf/users.ldif
    java -jar bin/ldap.jar conf
    java -Dsandbox.ldcSystemPassword=guest-password -jar bin/gateway.jar -persist-master

Following call to WebHDFS should report HTTP/1.1 401 Unauthorized
As guest is not a member of group "analyst", authorization provider states user should be member of group "analyst"

    curl  -i -v  -k -u guest:guest-password  -X GET https://localhost:8443/gateway/sandbox/webhdfs/v1?op=GETHOMEDIRECTORY

Following call to WebHDFS should report: {"Path":"/user/sam"}
As sam is a member of group "analyst", authorization provider states user should be member of group "analyst"

    curl  -i -v  -k -u sam:sam-password  -X GET https://localhost:8443/gateway/sandbox/webhdfs/v1?op=GETHOMEDIRECTORY

#### LDAP Dynamic Group Lookup Templates, authentication and dynamic group lookup from same  directory ####

* topology file: sandbox.knoxrealmdg.xml
* ldif file: users.ldapdynamicgroups.ldif

To try this out

    cd {GATEWAY_HOME}
    cp templates/sandbox.knoxrealmdg.xml conf/topologies/sandbox.xml
    cp templates/users.ldapdynamicgroups.ldif conf/users.ldif
    java -jar bin/ldap.jar conf
    java -Dsandbox.ldcSystemPassword=guest-password -jar bin/gateway.jar -persist-master

Please note that user.ldapdynamicgroups.ldif also loads necessary schema to create dynamic groups in Apache DS.

Following call to WebHDFS should report HTTP/1.1 401 Unauthorized
As guest is not a member of dynamic group "directors", authorization provider states user should be member of group "directors"

    curl  -i -v  -k -u guest:guest-password  -X GET https://localhost:8443/gateway/sandbox/webhdfs/v1?op=GETHOMEDIRECTORY

Following call to WebHDFS should report: {"Path":"/user/bob"}
As bob is a member of dynamic group "directors", authorization provider states user should be member of group "directors"

    curl  -i -v  -k -u sam:sam-password  -X GET https://localhost:8443/gateway/sandbox/webhdfs/v1?op=GETHOMEDIRECTORY

