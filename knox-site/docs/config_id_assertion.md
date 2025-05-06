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

### Identity Assertion ###
The identity assertion provider within Knox plays the critical role of communicating the identity principal to be used within the Hadoop cluster to represent the identity that has been authenticated at the gateway.

The general responsibilities of the identity assertion provider is to interrogate the current Java Subject that has been established by the authentication or federation provider and:

1. determine whether it matches any principal mapping rules and apply them appropriately
2. determine whether it matches any group principal mapping rules and apply them
3. if it is determined that the principal will be impersonating another through a principal mapping rule then a Subject.doAS is required so providers farther downstream can determine the appropriate effective principal name and groups for the user

###### Default Identity Assertion Provider ######
The following configuration is required for asserting the users identity to the Hadoop cluster using Pseudo or Simple "authentication" and for using Kerberos/SPNEGO for secure clusters.

    <provider>
        <role>identity-assertion</role>
        <name>Default</name>
        <enabled>true</enabled>
    </provider>

This particular configuration indicates that the Default identity assertion provider is enabled and that there are no principal mapping rules to apply to identities flowing from the authentication in the gateway to the backend Hadoop cluster services. The primary principal of the current subject will therefore be asserted via a query parameter or as a form parameter - ie. `?user.name={primaryPrincipal}`

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
        <param>
           <name>hadoop.proxyuser.impersonation.enabled</name>
           <value>false</value>
         </param>
         <param>
           <name>hadoop.proxyuser.admin.users</name>
           <value>*</value>
         </param>
         <param>
           <name>hadoop.proxyuser.admin.groups</name>
           <value>*</value>
         </param>
         <param>
           <name>hadoop.proxyuser.admin.hosts</name>
           <value>*</value>
         </param>
    </provider>

This configuration identifies the same identity assertion provider but does provide principal and group mapping rules. In this case, when a user is authenticated as "guest" his identity is actually asserted to the Hadoop cluster as "hdfs". In addition, since there are group principal mappings defined, he will also be considered as a member of the groups "users" and "admin". In this particular example the wildcard "*" is used to indicate that all authenticated users need to be considered members of the "users" group and that only the user "hdfs" is mapped to be a member of the "admin" group.

**NOTE: These group memberships are currently only meaningful for Service Level Authorization using the AclsAuthorization provider. The groups are not currently asserted to the Hadoop cluster at this time. See the Authorization section within this guide to see how this is used.**

The principal mapping aspect of the identity assertion provider is important to understand in order to fully utilize the authorization features of this provider.

This feature allows us to map the authenticated principal to a runAs or impersonated principal to be asserted to the Hadoop services in the backend.

When a principal mapping is defined that results in an impersonated principal, this impersonated principal is then the effective principal.

If there is no mapping to another principal then the authenticated or primary principal is the effective principal.

Another way to impersonate principals is to apply Hadoop Proxyuser-based impersonations as described in the next section.

##### Hadoop Proxyuser impersonation

From v2.0.0, an authenticated user can impersonate other user(s) leveraging Hadoop's proxuyser configuration mechanism. This feature was implemented in [KNOX-2839](https://issues.apache.org/jira/browse/KNOX-2839) and requires the following configuration to work:

* `hadoop.proxyuser.impersonation.enabled` - a `boolean` flag indicates if token impersonation is enabled. Defaults to `true`
* `hadoop.proxyuser.$username.users`  - indicates the list of users for whom `$username` is allowed to impersonate. It is possible to set this to a 1-element list using the `*` wildcard which means `$username` can impersonate everyone. Defaults to an empty list that is equivalent to  `$username` is not allowed to impersonate anyone.
* `hadoop.proxyuser.$username.groups`  - indicates the list of group names for whose members `$username` is allowed to impersonate. It is possible to set this to a 1-element list using the `*` wildcard which means `$username` can impersonate members of any group. Defaults to an empty list that is equivalent to `$username` is not allowed to impersonate members from any group.
* `hadoop.proxyuser.$username.hosts`  - indicates a list of hostnames from where the requests are allowed to be accepted in case the `doAs` parameter is used when impersonating requests. It is possible to set this to a 1-element list using the `*` wildcard which means `$username` can impersonate incoming requests from any host. Defaults to an empty list that is equivalent to `$username` is not allowed to impersonate requests from any host.

Please note this configuration is applied **iff** the `doAs` query parameter is present in the incoming request and impersonation is enabled in the affected topology.

_**Important note:**_ this new-type impersonation support on the identity assertion layer is ignored if the topology uses the `HadoopAuth` authentication provider because the `doAs` support is working OOTB there, therefore a second authorization is useless going forward.

It's also worth articulating that Hadoop Proxyuser-based impersonation works together with the already existing principal mapping (see below). At first, Knox applies the Hadoop Proxyuser impersonation, then it proceeds with principal mappings (if any). Let see a sample:

 * `hadoop.proxyuser.admin.users` is set to `bob` (`admin` is allowed to impersonate `bob`)
 * `principal.mapping` is set to `bob=tom` (`bob` is mapped as `tom` )
 
The `admin` user sends the following request:

    curl https://KNOX_HOST:8443/gateway/sandbox/service/path?doAs=bob

In the request processing flow, after the identity assertion phase is completed, `tom` will be the effective user. As you can see, the rules were applied transitively.

For other use cases you may want to check out [GitHub Pull Request #681](https://github.com/apache/knox/pull/681).

###### Principal Mapping ######

    <param>
        <name>principal.mapping</name>
        <value>{primaryPrincipal}[,...]={impersonatedPrincipal}[;...]</value>
    </param>

For instance:

    <param>
        <name>principal.mapping</name>
        <value>guest=hdfs</value>
    </param>

For multiple mappings:

    <param>
        <name>principal.mapping</name>
        <value>guest,alice=hdfs;mary=alice2</value>
    </param>

###### Expression-Based Principal Mapping ######

Alternatively, you can use an expression language to define principal mappings.

    <param>
      <name>expression.principal.mapping</name>
      <!-- expression that returns the new principal -->
      <value>...</value>
    </param>

The value of `expression.principal.mapping` must be a valid expression that evaluates to a string. 

For example, the following expression will map all users to one constant user, 'bob'.

    <param>
      <name>expression.principal.mapping</name>
      <value>'bob'</value>
    </param>

By adding a conditional you can selectively apply the mapping to specific users.

    <param>
      <name>expression.principal.mapping</name>
      <!-- Only map sam/tom to bob -->
      <value>
        (if (or (= username 'sam') 
                (= username 'tom')) 
            'bob')
      </value>
    </param>

The `if` expression expects ether 2 or 3 parameters. The first one is always a conditional that should return a boolean value.
The second parameter is the consequent branch that is only evaluated if the conditional is true.
The third, optional part is the alternative branch that is evaluated if the conditional is false.

    (if (< (strlen username) 5)
        (concat username '_suffix') 
        (concat 'prefix_' username))

Here the user `admin` will be mapped to `prefix_admin`, while `sam` will be mapped to `sam_suffix`.

In an XML topology, the less than and greater than operators should be either encoded as `&lt;` `&gt;`,
or the expression should be put inside a CDATA section.

    (&lt; (strlen username) 5)

The following expression capitalizes the principal:

    (concat
      (uppercase (substr username 0 1))
      (lowercase (substr username 1)))

The functionality of the Regex-based identity assertion provider is exposed via the `regex-template` function.

    (regex-template  username '(.*)@(.*?)\..*' '{1}_{[2]}' (hash 'us' 'USA' 'ca' 'CANADA') true)

The above expression turns `nobody@us.imaginary.tld` to `nobody_USA`.

See [KNOX-2983](https://issues.apache.org/jira/browse/KNOX-2983) for the complete list of functions.


###### Group Principal Mapping ######

    <param>
        <name>group.principal.mapping</name>
        <value>{userName[,*|userName...]}={groupName[,groupName...]}[,...]</value>
    </param>

For instance:

    <param>
        <name>group.principal.mapping</name>
        <value>*=users;hdfs=admin</value>
    </param>

this configuration indicates that all (*) authenticated users are members of the "users" group and that user "hdfs" is a member of the admin group. Group principal mapping has been added along with the authorization provider described in this document.


###### Group Mapping Based on Predicates ######

    <param>
        <name>group.mapping.{mappedGroupName}</name>
        <value>{predicate}</value>
    </param>


The predicate-based group mapping offers more flexibility by allowing the use of arbitrary logical expressions to define the mappings. The predicate expression must evaluate to a boolean result, true or false. The syntax is inspired by the Lisp language, but it is limited to boolean expressions and comparisons.

For instance:

    <param>
        <name>group.mapping.admin</name>
        <value>(or (username 'guest') (member 'analyst'))</value>
    </param>

This configuration maps the user to the "admin" group if either the authenticated user is "guest" or the authenticated user is a member of the "analyst" group.

The syntax of the language is based on parenthesized prefix notation, meaning that the operators precede their operands.

The language is made up of two basic building blocks: atoms (boolean, string, number, symbol) and lists. A list is written with its elements separated by whitespace, and surrounded by parentheses. A list can contain other lists or atoms.

A function call or an operator is written as a list with the function or operator's name first, and the arguments following. For instance, a function f that takes three arguments would be called as (f arg1 arg2 arg3).


Lists are of arbitrary length and can be nested to express more complex conditionals.

    (or 
        (and
            (member 'admin')
            (member 'scientist'))
        (or
            (username 'tom')
            (username 'sam')))

Returns:

1. True if the user is either 'tom' or 'sam'
2. True if the user is both in the 'admin' and the 'scientist' group.
3. False otherwise.

The following predicate checks if the user is a member of any group:

    (!= (size groups) 0)

    (not (empty groups))

    (match groups '.*')


This predicate checks if the username is either "tom" or "sam":

    (match username 'tom|sam')

This checks the username in a case insensitive manner:

    (= (lowercase username) 'bob')


##### Supported functions and operators #####

###### or ######
Evaluates true if one or more of its operands is true. Supports short-circuit evaluation and variable number of arguments.

Number of arguments: 1..N

    (or bool1 bool2 ... boolN)
    
Example

    (or true false true)

###### and ######
Evaluates true if all of its operands are true. Supports short-circuit evaluation and variable number of arguments.

Number of arguments: 1..N

    (and bool1 bool2 ... boolN)

Example

     (and true false true)

###### not ######
Negates the operand.

Number of arguments: 1

     (not aBool)

Example

     (not true)

###### = ######
Evaluates true if the two operands are equal.

Number of arguments: 2

     (= op1 op2)

Example

     (= 'apple' 'orange')

###### != ######
Evaluates true if the two operands are not equal.

Number of arguments: 2

     (!= op1 op2)

Example

      (!= 'apple' 'orange')

###### member ######
Evaluates true if the current user is a member of the given group

Number of arguments: 1

     (member aString)

Example

     (member 'analyst')

###### username ######
Evaluates true if the current user has the given username

Number of arguments: 1

    (username aString)

Example

    (username 'admin')
    
This is a shorter version of (= username 'admin')

###### size ######
Gets the size of a list

Number of arguments: 1

     (size alist)

Example

     (size groups)

###### empty ######
Evaluates to true if the given list is empty

Number of arguments: 1

     (empty alist)

Example

     (empty groups)

###### match ######
Evaluates true if the given string matches to the given regexp. Or any items of the given list matches the given regexp.

Number of arguments: 2

    (match aString aRegExpString)
    
    (match aList aRegExpString)

Example

    (match username 'tom|sam')

This function can also take a list as a first argument. In this case it will return true if the regexp matches to any of the items in the list.

    (match groups 'analyst|scientist')
    
This returns true if the user is either in the 'analyst' group or in the 'scientist' group. The same can be expressed by combining 2 member functions with an or expression.

###### request-header ######
Returns the value of the specified request header as a String. If the given key doesn't exist empty string is returned.

Number of arguments: 1

    (request-header aString)

Example

    (request-header 'User-Agent')

###### request-attribute ######
Returns the value of the specified request attribute as a String. If the given key doesn't exist empty string is returned.

Number of arguments: 1

    (request-attribute aString)

Example

    (request-attribute 'sourceRequestUrl')
    
###### session ######

Returns the value of the specified session attribute as a String. If the given key doesn't exist empty string is returned.

Number of arguments: 1

     (session aString)
     
Example

     (session 'subject.userRoles')

###### lowercase ######
Converts the given string to lowercase.

Number of arguments: 1

     (lowercase aString)

Example

     (lowercase 'KNOX')

###### uppercase ######
Converts the given string to uppercase.

Number of arguments: 1

     (uppercase aString)

Example

    (uppercase 'knox')


### Constants ###
The following constants are populated automatically from the current security context.

###### username ######
The username (principal) of the current user, derived from javax.security.auth.Subject.

###### groups ######
The groups of the current user (as determined by the authentication provider), derived from subject.getPrincipals(GroupPrincipal.class).

###### Concat Identity Assertion Provider ######
The Concat identity assertion provider allows for composition of a new user principal through the concatenation of optionally configured prefix and/or suffix provider parameters. This is a useful assertion provider for converting an incoming identity into a disambiguated identity within the Hadoop cluster based on what topology is used to access Hadoop.

The following configuration would convert the user principal into a value that represents a domain specific identity where the identities used inside the Hadoop cluster represent this same separation.

    <provider>
        <role>identity-assertion</role>
        <name>Concat</name>
        <enabled>true</enabled>
        <param>
            <name>concat.suffix</name>
            <value>_domain1</value>
        </param>
    </provider>

The above configuration will result in all user interactions through that topology to have their principal communicated to the Hadoop cluster with a domain designator concatenated to the username. Possibly useful for multi-tenant deployment scenarios.

In addition to the concat.suffix parameter, the provider supports the setting of a prefix through a `concat.prefix` parameter.

###### SwitchCase Identity Assertion Provider ######
The SwitchCase identity assertion provider solves issues where down stream ecosystem components require user and group principal names to be a specific case.
An example of how this provider is enabled and configured within the `<gateway>` section of a topology file is shown below.
This particular example will switch user principals names to lower case and group principal names to upper case.

    <provider>
        <role>identity-assertion</role>
        <name>SwitchCase</name>
        <param>
            <name>principal.case</name>
            <value>lower</value>
        </param>
        <param>
            <name>group.principal.case</name>
            <value>upper</value>
        </param>
        <enabled>true</enabled>
    </provider>

These are the configuration parameters used to control the behavior of the provider.

Param                | Description
---------------------|------------
principal.case       | The case mapping of user principal names. Choices are: lower, upper, none.  Defaults to lower.
group.principal.case | The case mapping of group principal names. Choices are: lower, upper, none. Defaults to setting of principal.case.

If no parameters are provided the full defaults will results in both user and group principal names being switched to lower case.
A setting of "none" or anything other than "upper" or "lower" leaves the case of the principal name unchanged.

###### Regular Expression Identity Assertion Provider ######
The regular expression identity assertion provider allows incoming identities to be translated using a regular expression, template and lookup table.
This will probably be most useful in conjunction with the HeaderPreAuth federation provider.

There are three configuration parameters used to control the behavior of the provider.

Param | Description
------|-----------
input | This is a regular expression that will be applied to the incoming identity. The most critical part of the regular expression is the group notation within the expression. In regular expressions, groups are expressed within parenthesis. For example in the regular expression "`(.*)@(.*?)\..*`" there are two groups. When this regular expression is applied to "nobody@us.imaginary.tld" group 1 matches "nobody" and group 2 matches "us". 
output| This is a template that assembles the result identity. The result is assembled from the static text and the matched groups from the input regular expression. In addition, the matched group values can be looked up in the lookup table. An output value of "`{1}_{2}`" of will result in "nobody_us".                 
lookup| This lookup table provides a simple (albeit limited) way to translate text in the incoming identities. This configuration takes the form of "=" separated name values pairs separated by ";". For example a lookup setting is "us=USA;ca=CANADA". The lookup is invoked in the output setting by surrounding the desired group number in square brackets (i.e. []). Putting it all together, output setting of "`{1}_[{2}]`" combined with input of "`(.*)@(.*?)\..*`" and lookup of "us=USA;ca=CANADA" will turn "nobody@us.imaginary.tld" into "nobody@USA".
use.original.on.lookup.failure | (Optional) Default value is false. If set to true, it will preserve the original string if there is no match. e.g. In the above lookup case for email nobody@uk.imaginary.tld, it will be transformed to nobody@ , if this property is set to true it will be transformed to  nobody@uk.  

Within the topology file the provider configuration might look like this.

    <provider>
        <role>identity-assertion</role>
        <name>Regex</name>
        <enabled>true</enabled>
        <param>
            <name>input</name>
            <value>(.*)@(.*?)\..*</value>
        </param>
        <param>
            <name>output</name>
            <value>{1}_{[2]}</value>
        </param>
        <param>
            <name>lookup</name>
            <value>us=USA;ca=CANADA</value>
        </param>
    </provider>  

Using curl with this type of configuration might produce the following results. 

    curl -k --header "SM_USER: nobody@us.imaginary.tld" 'https://localhost:8443/gateway/sandbox/webhdfs/v1?op=GETHOMEDIRECTORY'
    
    {"Path":"/user/member_USA"}
    
    url -k --header "SM_USER: nobody@ca.imaginary.tld" 'https://localhost:8443/gateway/sandbox/webhdfs/v1?op=GETHOMEDIRECTORY'
    
    {"Path":"/user/member_CANADA"}

### Hadoop Group Lookup Provider ###

An identity assertion provider that looks up user's 'group membership' for authenticated users using Hadoop's group mapping service (GroupMappingServiceProvider).

This allows existing investments in the Hadoop to be leveraged within Knox and used within the access control policy enforcement at the perimeter.

The 'role' for this provider is 'identity-assertion' and name is 'HadoopGroupProvider'.

        <provider>
            <role>identity-assertion</role>
            <name>HadoopGroupProvider</name>
            <enabled>true</enabled>
            <<param> ... </param>
        </provider>

### Configuration ###

All the configuration for 'HadoopGroupProvider' resides in the provider section in a gateway topology file.
The 'hadoop.security.group.mapping' property determines the implementation. This configuration may be centralized within the gateway-site.xml through the use of a special param to this provider called CENTRAL_GROUP_CONFIG_PREFIX. This indicates to the provider that the required configuration can be found within the gateway-site.xml file with the provided prefix.

         <param>
            <name>CENTRAL_GROUP_CONFIG_PREFIX</name>
            <value>gateway.group.config.</value>
         </param>

 Some of the valid implementations are as follows: 
###### org.apache.hadoop.security.JniBasedUnixGroupsMappingWithFallback

This is the default implementation and will be picked up if 'hadoop.security.group.mapping' is not specified. This implementation will determine if the Java Native Interface (JNI) is available. If JNI is available, the implementation will use the API within Hadoop to resolve a list of groups for a user. If JNI is not available then the shell implementation, `org.apache.hadoop.security.ShellBasedUnixGroupsMapping`, is used, which shells out with the `bash -c id -gn <user> ; id -Gn <user>` command (for a Linux/Unix environment) or the `groups -F <user>` command (for a Windows environment) to resolve a list of groups for a user.

###### org.apache.hadoop.security.JniBasedUnixGroupsNetgroupMappingWithFallback

As above, if JNI is available then we get the netgroup membership using Hadoop native API, else fallback on ShellBasedUnixGroupsNetgroupMapping to resolve list of groups for a user.

###### org.apache.hadoop.security.ShellBasedUnixGroupsMapping

Uses the `bash -c id -gn <user> ; id -Gn <user>` command (for a Linux/Unix environment) or the `groups -F <user>` command (for a Windows environment) to resolve list of groups for a user.

###### org.apache.hadoop.security.ShellBasedUnixGroupsNetgroupMapping

Similar to `org.apache.hadoop.security.ShellBasedUnixGroupsMapping` except it uses `getent netgroup` command to get netgroup membership.

###### org.apache.hadoop.security.LdapGroupsMapping

This implementation connects directly to an LDAP server to resolve the list of groups. However, this should only be used if the required groups reside exclusively in LDAP, and are not materialized on the Unix servers.

###### org.apache.hadoop.security.CompositeGroupsMapping

This implementation asks multiple other group mapping providers for determining group membership, see [Composite Groups Mapping](https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-common/GroupsMapping.html#Composite_Groups_Mapping) for more details.

For more information on the implementation and properties refer to Hadoop Group Mapping.

### Example ###

The following example snippet works with the demo ldap server that ships with Apache Knox. Replace the existing 'Default' identity-assertion provider with the one below (HadoopGroupProvider).

        <provider>
            <role>identity-assertion</role>
            <name>HadoopGroupProvider</name>
            <enabled>true</enabled>
            <param>
                <name>hadoop.security.group.mapping</name>
                <value>org.apache.hadoop.security.LdapGroupsMapping</value>
            </param>
            <param>
                <name>hadoop.security.group.mapping.ldap.bind.user</name>
                <value>uid=tom,ou=people,dc=hadoop,dc=apache,dc=org</value>
            </param>
            <param>
                <name>hadoop.security.group.mapping.ldap.bind.password</name>
                <value>tom-password</value>
            </param>
            <param>
                <name>hadoop.security.group.mapping.ldap.url</name>
                <value>ldap://localhost:33389</value>
            </param>
            <param>
                <name>hadoop.security.group.mapping.ldap.base</name>
                <value></value>
            </param>
            <param>
                <name>hadoop.security.group.mapping.ldap.search.filter.user</name>
                <value>(&amp;(|(objectclass=person)(objectclass=applicationProcess))(cn={0}))</value>
            </param>
            <param>
                <name>hadoop.security.group.mapping.ldap.search.filter.group</name>
                <value>(objectclass=groupOfNames)</value>
            </param>
            <param>
                <name>hadoop.security.group.mapping.ldap.search.attr.member</name>
                <value>member</value>
            </param>
            <param>
                <name>hadoop.security.group.mapping.ldap.search.attr.group.name</name>
                <value>cn</value>
            </param>
        </provider>


Here, we are working with the demo LDAP server running at 'ldap://localhost:33389' which populates some dummy users for testing that we will use in this example. This example uses the user 'tom' for LDAP binding. If you have different LDAP/AD settings, you will have to update the properties accordingly. 

Let's test our setup using the following command (assuming the gateway is started and listening on localhost:8443). Note that we are using credentials for the user 'sam' along with the command. 

        curl -i -k -u sam:sam-password -X GET 'https://localhost:8443/gateway/sandbox/webhdfs/v1/?op=LISTSTATUS' 

The command should be executed successfully and you should see the groups 'scientist' and 'analyst' to which user 'sam' belongs to in gateway-audit.log i.e.

        ||a99aa0ab-fc06-48f2-8df3-36e6fe37c230|audit|WEBHDFS|sam|||identity-mapping|principal|sam|success|Groups: [scientist, analyst]
