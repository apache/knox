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

### Authorization ###

#### Service Level Authorization ####

The Knox Gateway has an out-of-the-box authorization provider that allows administrators to restrict access to the individual services within a Hadoop cluster.

This provider utilizes a simple and familiar pattern of using ACLs to protect Hadoop resources by specifying users, groups and ip addresses that are permitted access.

Note: This feature will not work as expected if 'anonymous' authentication is used. 

#### Configuration ####

ACLs are bound to services within the topology descriptors by introducing the authorization provider with configuration like:

    <provider>
        <role>authorization</role>
        <name>AclsAuthz</name>
        <enabled>true</enabled>
    </provider>

The above configuration enables the authorization provider but does not indicate any ACLs yet and therefore there is no restriction to accessing the Hadoop services. In order to indicate the resources to be protected and the specific users, groups or ip's to grant access, we need to provide parameters like the following:

    <param>
        <name>{serviceName}.acl</name>
        <value>username[,*|username...];group[,*|group...];ipaddr[,*|ipaddr...]</value>
    </param>
    
where `{serviceName}` would need to be the name of a configured Hadoop service within the topology.

NOTE: ipaddr is unique among the parts of the ACL in that you are able to specify a wildcard within an ipaddr to indicate that the remote address must being with the String prior to the asterisk within the ipaddr ACL. For instance:

    <param>
        <name>{serviceName}.acl</name>
        <value>*;*;192.168.*</value>
    </param>
    
This indicates that the request must come from an IP address that begins with '192.168.' in order to be granted access.

Note also that configuration without any ACLs defined is equivalent to:

    <param>
        <name>{serviceName}.acl</name>
        <value>*;*;*</value>
    </param>

meaning: all users, groups and IPs have access.
Each of the elements of the ACL parameter support multiple values via comma separated list and the `*` wildcard to match any.

For instance:

    <param>
        <name>webhdfs.acl</name>
        <value>hdfs;admin;127.0.0.2,127.0.0.3</value>
    </param>

this configuration indicates that ALL of the following have to be satisfied to be granted access:

1. The user name must be "hdfs" AND
2. the user must be in the group "admin" AND
3. the user must come from either 127.0.0.2 or 127.0.0.3

This allows us to craft policy that restricts the members of a large group to a subset that should have access.
The user being removed from the group will allow access to be denied even though their username may have been in the ACL.

An additional configuration element may be used to alter the processing of the ACL to be OR instead of the default AND behavior:

    <param>
        <name>{serviceName}.acl.mode</name>
        <value>OR</value>
    </param>

this processing behavior requires that the effective user satisfy one of the parts of the ACL definition in order to be granted access.
For instance:

    <param>
        <name>webhdfs.acl</name>
        <value>hdfs,guest;admin;127.0.0.2,127.0.0.3</value>
    </param>

You may also set the ACL processing mode at the top level for the topology. This essentially sets the default for the managed cluster.
It may then be overridden at the service level as well.

    <param>
        <name>acl.mode</name>
        <value>OR</value>
    </param>

this configuration indicates that ONE of the following must be satisfied to be granted access:

1. The user is "hdfs" or "guest" OR
2. the user is in "admin" group OR
3. the request is coming from 127.0.0.2 or 127.0.0.3


Following are a few concrete examples on how to use this feature.

Note: In the examples below `{serviceName}` represents a real service name (e.g. WEBHDFS) and would be replaced with these values in an actual configuration.

##### Usecases #####

###### USECASE-1: Restrict access to specific Hadoop services to specific Users

    <param>
        <name>{serviceName}.acl</name>
        <value>guest;*;*</value>
    </param>

###### USECASE-2: Restrict access to specific Hadoop services to specific Groups

    <param>
        <name>{serviceName}.acls</name>
        <value>*;admins;*</value>
    </param>

###### USECASE-3: Restrict access to specific Hadoop services to specific Remote IPs

    <param>
        <name>{serviceName}.acl</name>
        <value>*;*;127.0.0.1</value>
    </param>

###### USECASE-4: Restrict access to specific Hadoop services to specific Users OR users within specific Groups

    <param>
        <name>{serviceName}.acl.mode</name>
        <value>OR</value>
    </param>
    <param>
        <name>{serviceName}.acl</name>
        <value>guest;admin;*</value>
    </param>

###### USECASE-5: Restrict access to specific Hadoop services to specific Users OR users from specific Remote IPs

    <param>
        <name>{serviceName}.acl.mode</name>
        <value>OR</value>
    </param>
    <param>
        <name>{serviceName}.acl</name>
        <value>guest;*;127.0.0.1</value>
    </param>

###### USECASE-6: Restrict access to specific Hadoop services to users within specific Groups OR from specific Remote IPs

    <param>
        <name>{serviceName}.acl.mode</name>
        <value>OR</value>
    </param>
    <param>
        <name>{serviceName}.acl</name>
        <value>*;admin;127.0.0.1</value>
    </param>

###### USECASE-7: Restrict access to specific Hadoop services to specific Users OR users within specific Groups OR from specific Remote IPs

    <param>
        <name>{serviceName}.acl.mode</name>
        <value>OR</value>
    </param>
    <param>
        <name>{serviceName}.acl</name>
        <value>guest;admin;127.0.0.1</value>
    </param>

###### USECASE-8: Restrict access to specific Hadoop services to specific Users AND users within specific Groups

    <param>
        <name>{serviceName}.acl</name>
        <value>guest;admin;*</value>
    </param>

###### USECASE-9: Restrict access to specific Hadoop services to specific Users AND users from specific Remote IPs

    <param>
        <name>{serviceName}.acl</name>
        <value>guest;*;127.0.0.1</value>
    </param>

###### USECASE-10: Restrict access to specific Hadoop services to users within specific Groups AND from specific Remote IPs

    <param>
        <name>{serviceName}.acl</name>
        <value>*;admins;127.0.0.1</value>
    </param>

###### USECASE-11: Restrict access to specific Hadoop services to specific Users AND users within specific Groups AND from specific Remote IPs

    <param>
        <name>{serviceName}.acl</name>
        <value>guest;admins;127.0.0.1</value>
    </param>

###### USECASE-12: Full example including identity assertion/principal mapping ######

The principal mapping aspect of the identity assertion provider is important to understand in order to fully utilize the authorization features of this provider.

This feature allows us to map the authenticated principal to a runAs or impersonated principal to be asserted to the Hadoop services in the backend. It is fully documented in the Identity Assertion section of this guide.

These additional mapping capabilities are used together with the authorization ACL policy.
An example of a full topology that illustrates these together is below.

    <topology>
        <gateway>
            <provider>
                <role>authentication</role>
                <name>ShiroProvider</name>
                <enabled>true</enabled>
                <param>
                    <name>main.ldapRealm</name>
                    <value>org.apache.shiro.realm.ldap.JndiLdapRealm</value>
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
                <role>identity-assertion</role>
                <name>Default</name>
                <enabled>true</enabled>
                <param>
                    <name>principal.mapping</name>
                    <value>guest=hdfs;</value>
                </param>
                <param>
                    <name>group.principal.mapping</name>
                    <value>*=users;hdfs=admin</value>
                </param>
            </provider>
            <provider>
                <role>authorization</role>
                <name>AclsAuthz</name>
                <enabled>true</enabled>
                <param>
                    <name>acl.mode</name>
                    <value>OR</value>
                </param>
                <param>
                    <name>webhdfs.acl.mode</name>
                    <value>AND</value>
                </param>
                <param>
                    <name>webhdfs.acl</name>
                    <value>hdfs;admin;127.0.0.2,127.0.0.3</value>
                </param>
                <param>
                    <name>webhcat.acl</name>
                    <value>hdfs;admin;127.0.0.2,127.0.0.3</value>
                </param>
            </provider>
            <provider>
                <role>hostmap</role>
                <name>static</name>
                <enabled>true</enabled>
                <param>
                    <name>localhost</name>
                    <value>sandbox,sandbox.hortonworks.com</value>
                </param>
            </provider>
        </gateway>

        <service>
            <role>JOBTRACKER</role>
            <url>rpc://localhost:8050</url>
        </service>

        <service>
            <role>WEBHDFS</role>
            <url>http://localhost:50070/webhdfs</url>
        </service>

        <service>
            <role>WEBHCAT</role>
            <url>http://localhost:50111/templeton</url>
        </service>

        <service>
            <role>OOZIE</role>
            <url>http://localhost:11000/oozie</url>
        </service>

        <service>
            <role>WEBHBASE</role>
            <url>http://localhost:8080</url>
        </service>

        <service>
            <role>HIVE</role>
            <url>http://localhost:10001/cliservice</url>
        </service>
    </topology>

### Composite Authorization Provider ###



By providing a composite authz provider, we are able to configure multiple authz providers in a single topology. 
This allows the use of both the AclsAuthz provider and something like the Ranger Knox plugin where available.

All authorization providers used within the CompositeAuthz provider will need to grant access for the request
processing to continue to the protected resource. This is a logical AND across all the providers.

The following is an example of what configuration of the CompositeAuthz provider is like.

        <provider>
            <role>authorization</role>
            <name>CompositeAuthz</name>
            <enabled>true</enabled>
            <param>
                <name>composite.provider.names</name>
                <value>AclsAuthz,SomeOther</value>
            </param>
            <param>
                <name>AclsAuthz.webhdfs.acl</name>
                <value>admin;*;*</value>
            </param>
            <param>
                <name>SomeOther.provider.specific.param</name>
                <value>provider.specific-value</value>
            </param>
        </provider>

Note the comma separated list of provider names in composite.provider.names param.

Also Note the use of those names as prefixes to the params to be set on the respective providers.

The prefixes are removed and the expected param names are set on the actual providers as appropriate.

### Path Based Authorization ###

Path based authorization (`PathAclsAuthz`) enforces Acls authorization on a configured path.  The semantics of Path based authorization are similar to Acls authz. Authorization is done based on path matching similar to rewrite rules. 

Format is very similar to AclsAuthz provider with an addition of path argument. The format is
`{path};{users};{groups};{ips}`. For details on the format please see #[Service Level Authorization].
One important thing to note here is that the path is not plural, there has to be one and only one path defined.

In case one wants multiple paths they can define multiple rules with rule name as a parameter e.g.
`KNOXTOKEN.{rule_name}.path.acl`

#### Which rules apply to a service ####
The provider is configured once per topology, but it is evaluated separately for every service in it.
A service picks up:

* the topology-wide `path.acl` rule, if present,
* every parameter whose name ends with `.path.acl` and contains the service role, compared case-insensitively
  (for example `KNOXTOKEN.path.acl` or `knoxtoken.rule_1.path.acl` for the `KNOXTOKEN` service).

A `.path.acl` parameter whose name does not contain the service role is ignored for that service without any warning,
so a misspelled role silently removes the rule. Because the check is a substring match, a service also picks up
rules written for any service whose role contains its own; for example the `KNOXIDF` service also applies
`KNOXIDF_ADMIN.*.path.acl` rules.

If a service ends up with no rules at all, every request to it is allowed.

#### How a request is matched and authorized ####
Once a service has at least one rule:

* A request that matches **no** rule is denied with `403`. Rules work as an allow list: only paths covered by a
  rule are reachable.
* A request that matches **one or more** rules is allowed only if **every** matching rule grants access,
  regardless of the order the rules are declared in. A narrower rule therefore cannot grant more access than a
  broader rule that also matches: with `**;admin;*;*` and `**/public/**;*;*;*`, `/public` is reachable only by
  `admin`. To open a sub-path to more users, write rules that do not overlap.
* To grant several users or groups on the same path, list them comma-separated in one rule. Two rules on the
  same path with different users deny both.
* Each rule is matched against the request URL as received and against its canonical form. The canonical form
  percent-decodes the path once, drops `;` matrix parameters, treats encoded `/` and `\` as separators and resolves
  `.` and `..` segments.
* A request whose path cannot be canonicalized is denied: an invalid `%` escape, invalid UTF-8, a `.` or `..`
  segment carrying an encoded `;`, or `..` segments that climb above the root.

Path patterns use the same syntax as rewrite rules:

* `**` as a whole segment matches any number of segments, e.g. `https://*:*/**/api/**`.
* `*` or `**` inside a segment matches within that segment only. `/v1/issuers**` matches `/v1/issuers`,
  `/v1/issuers/` and also sibling names such as `/v1/issuers-archive`, but not `/v1/issuers/abc`.
* A trailing `/**` requires at least one more segment. `/v1/issuers/**` matches `/v1/issuers/abc` but not `/v1/issuers`.

To cover both a collection and its sub-paths, define two rules, one exact (`.../v1/issuers`) and one with a
trailing `/**` (`.../v1/issuers/**`).

#### Processing mode ####
Within a single rule, users, groups and IP addresses are combined according to the processing mode, set with
`{service_name}.path.acl.mode` or, for all services, `path.acl.mode`. The value is `AND` (the default) or `OR`.

* `AND`: the user, group and IP address checks must all pass. `*` means any.
* `OR`: at least one of the checks must pass. To avoid granting everyone access by accident, a `*` field is treated
  as not granting access. As a result, a `*;*;*` rule grants access to nobody in `OR` mode. Name a group or user to
  open a path in `OR` mode.

        <provider>
            <role>authorization</role>
            <name>PathAclsAuthz</name>
            <enabled>true</enabled>
            <param>
                <name>KNOXTOKEN.path.acl.mode</name>
                <value>OR</value>
            </param>
            <param>
                <name>KNOXTOKEN.rule_1.path.acl</name>
                <value>https://*:*/**/knoxtoken/api/**;admin;admins;*</value>
            </param>
        </provider>

Following are special cases for rule names:
#### This rule will be applied to ALL services defined in the topology ####
This rule be applied to all services in the topology. Which means any service that has `api` 
as a context path needs the user to be `admin` for successful authorization. 

        <provider>
            <role>authorization</role>
            <name>PathAclsAuthz</name>
            <enabled>true</enabled>
            <param>
                <name>path.acl</name>
                <value>https://*:*/**/api/**;admin;*;*</value> 
            </param>
        </provider>

#### This rule will be applied to only the service {service_name} ####
This rule be applied to only `{service_name}` services in the topology. Any request for `{service_name}` that has `api` 
as a context path needs the user to be `admin` for successful authorization. 

        <provider>
            <role>authorization</role>
            <name>PathAclsAuthz</name>
            <enabled>true</enabled>
            <param>
                <name>{service_name}.path.acl</name>
                <value>https://*:*/**/api/**;admin;*;*</value> 
            </param>
        </provider>

#### ALL of these rules will be applied to service {service_name} ####
*NOTE:* {rule_1} and {rule_2} should be any unique names. 
Similar to previous cases for a service `{service_name}`, for any 
request to be successful with `api` and `api2` as context paths, it needs to have user `admin`. 

        <provider>
            <role>authorization</role>
            <name>PathAclsAuthz</name>
            <enabled>true</enabled>
            <param>
                <name>{service_name}.{rule_1}.path.acl</name>
                <value>https://*:*/**/api/**;admin;*;*</value> 
            </param>
            <param>
                <name>{service_name}.{rule_2}.path.acl</name>
                <value>https://*:*/**/api2/**;admin;*;*</value> 
            </param>
        </provider>

#### Examples ####
Following are concrete examples of the the above rules:

##### This rule will be applied to ALL services defined in the topology #####

        <provider>
            <role>authorization</role>
            <name>PathAclsAuthz</name>
            <enabled>true</enabled>
            <param>
                <name>path.acl</name>
                <value>https://*:*/**/knoxtoken/api/**;admin;*;*</value> 
            </param>
        </provider>

##### This rule will be applied to only to KNOXTOKEN service #####

        <provider>
            <role>authorization</role>
            <name>PathAclsAuthz</name>
            <enabled>true</enabled>
            <param>
                <name>KNOXTOKEN.path.acl</name>
                <value>https://*:*/**/knoxtoken/api/**;admin;*;*</value> 
            </param>
        </provider>

##### All of these rules will be applied to only to KNOXTOKEN service #####

        <provider>
            <role>authorization</role>
            <name>PathAclsAuthz</name>
            <enabled>true</enabled>
            <param>
                <name>KNOXTOKEN.rule_1.path.acl</name>
                <value>https://*:*/**/knoxtoken/api/**;admin;*;*</value> 
            </param>
            <param>
                <name>KNOXTOKEN.rule_2.path.acl</name>
                <value>https://*:*/**/knoxtoken/foo/**;knox;*;*</value> 
            </param>
            <param>
                <name>KNOXTOKEN.rule_3.path.acl</name>
                <value>https://*:*/**/knoxtoken/bar/**;sam;admin;*</value> 
            </param>
        </provider>  

##### Allow every authenticated user except on one sub-path #####
The broad rule opens the whole `KNOXTOKEN` service. The second rule overlaps it on `/knoxtoken/api/v1/admin/**`.
Both rules match there, and both must grant access, so only `admin` gets through. Everywhere else only the
broad rule matches. This configuration relies on `AND` mode, the default, because `*;*;*` grants nobody in `OR` mode.

        <provider>
            <role>authorization</role>
            <name>PathAclsAuthz</name>
            <enabled>true</enabled>
            <param>
                <name>KNOXTOKEN.all.path.acl</name>
                <value>https://*:*/**;*;*;*</value>
            </param>
            <param>
                <name>KNOXTOKEN.admin.path.acl</name>
                <value>https://*:*/**/knoxtoken/api/v1/admin/**;admin;*;*</value>
            </param>
        </provider>
