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

### LDAP Authentication Caching ###

Knox can be configured to cache LDAP authentication information. Knox leverages Shiro's built in
caching mechanisms and has been tested with Shiro's EhCache cache manager implementation.

The following provider snippet demonstrates how to configure turning on the cache using the ShiroProvider. In addition to
using `org.apache.knox.gateway.shirorealm.KnoxLdapRealm` in the Shiro configuration, and setting up the cache you *must* set
the flag for enabling caching authentication to true. Please see the property, `main.ldapRealm.authenticationCachingEnabled` below. If caching is enabled on more than one topology, advanced caching configs with differing persistence directories have to be provided. The reason for this is separate topologies manage their own caches in different directories. Two cache has to be in different places otherwise ehcache won't be able to lock the directory and the topology deployment will fail.


    <provider>
        <role>authentication</role>
        <name>ShiroProvider</name>
        <enabled>true</enabled>
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
        <param>
            <name>main.ldapRealm.searchBase</name>
            <value>ou=groups,dc=hadoop,dc=apache,dc=org</value>
        </param>
        <param>
            <name>main.cacheManager</name>
            <value>org.apache.knox.gateway.shirorealm.KnoxCacheManager</value>
        </param>
        <param>
            <name>main.securityManager.cacheManager</name>
            <value>$cacheManager</value>
        </param>
        <param>
            <name>main.ldapRealm.authenticationCachingEnabled</name>
            <value>true</value>
        </param>
        <param>
            <name>main.ldapRealm.memberAttributeValueTemplate</name>
            <value>uid={0},ou=people,dc=hadoop,dc=apache,dc=org</value>
        </param>
        <param>
            <name>main.ldapRealm.contextFactory.systemUsername</name>
            <value>uid=guest,ou=people,dc=hadoop,dc=apache,dc=org</value>
        </param>
        <param>
            <name>main.ldapRealm.contextFactory.systemPassword</name>
            <value>guest-password</value>
        </param>
        <param>
            <name>urls./**</name>
            <value>authcBasic</value>
        </param>
    </provider>


### Trying out caching ###

Knox bundles a template topology files that can be used to try out the caching functionality.
The template file located under `{GATEWAY_HOME}/templates` is `sandbox.knoxrealm.ehcache.xml`.

To try this out

    cd {GATEWAY_HOME}
    cp templates/sandbox.knoxrealm.ehcache.xml conf/topologies/sandbox.xml
    bin/ldap.sh start
    bin/gateway.sh start

The following call to WebHDFS should report: `{"Path":"/user/tom"}`

    curl  -i -v  -k -u tom:tom-password  -X GET https://localhost:8443/gateway/sandbox/webhdfs/v1?op=GETHOMEDIRECTORY

In order to see the cache working, LDAP can now be shutdown and the user will still authenticate successfully.

    bin/ldap.sh stop

and then the following should still return successfully like it did earlier.

    curl  -i -v  -k -u tom:tom-password  -X GET https://localhost:8443/gateway/sandbox/webhdfs/v1?op=GETHOMEDIRECTORY


#### Advanced Caching Config ####

By default the EhCache support in Shiro contains the ehcache.xml in its classpath which is the following

    <config xmlns="http://www.ehcache.org/v3">

        <persistence directory="${java.io.tmpdir}/shiro-ehcache"/>

        <cache alias="shiro-activeSessionCache">
            <key-type serializer="org.ehcache.impl.serialization.CompactJavaSerializer">
                java.lang.Object
            </key-type>
            <value-type serializer="org.ehcache.impl.serialization.CompactJavaSerializer">
                java.lang.Object
            </value-type>

            <resources>
                <heap unit="entries">10000</heap>
                <disk unit="GB">1</disk>
            </resources>
        </cache>

        <cache alias="org.apache.shiro.realm.text.PropertiesRealm-0-accounts">
            <key-type serializer="org.ehcache.impl.serialization.CompactJavaSerializer">
                java.lang.Object
            </key-type>
            <value-type serializer="org.ehcache.impl.serialization.CompactJavaSerializer">
                java.lang.Object
            </value-type>

            <resources>
                <heap unit="entries">1000</heap>
                <disk unit="GB">1</disk>
            </resources>
        </cache>

        <cache-template name="defaultCacheConfiguration">
            <expiry>
                <tti unit="seconds">120</tti>
            </expiry>
            <heap unit="entries">10000</heap>
        </cache-template>

    </config>

A custom configuration file (ehcache.xml) can be used in place of this in order to set specific caching configuration.

In order to set the ehcache.xml file to use for a particular topology, set the following parameter in the configuration
for the ShiroProvider:

    <param>
        <name>main.cacheManager.cacheManagerConfigFile</name>
        <value>classpath:ehcache.xml</value>
    </param>

In the above example, place the ehcache.xml file under `{GATEWAY_HOME}/conf` and restart the gateway server.
