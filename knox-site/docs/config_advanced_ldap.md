### Advanced LDAP Authentication

The default configuration computes the bind DN for incoming user based on userDnTemplate.
This does not work in enterprises where users could belong to multiple branches of LDAP tree.
You could instead enable advanced configuration that would compute bind DN of incoming user with an LDAP search.

#### Problem with userDnTemplate based Authentication 

UserDnTemplate based authentication uses the configuration parameter `ldapRealm.userDnTemplate`.
Typical values of userDNTemplate would look like `uid={0},ou=people,dc=hadoop,dc=apache,dc=org`.
 
To compute bind DN of the client, we swap the place holder `{0}` with the login id provided by the client.
For example, if the login id provided by the client is  "guest',  
the computed bind DN would be `uid=guest,ou=people,dc=hadoop,dc=apache,dc=org`.
 
This keeps configuration simple.

However, this does not work if users belong to different branches of LDAP DIT.
For example, if there are some users under `ou=people,dc=hadoop,dc=apache,dc=org` 
and some users under `ou=contractors,dc=hadoop,dc=apache,dc=org`,  
we cannot come up with userDnTemplate that would work for all the users.

#### Using advanced LDAP Authentication

With advanced LDAP authentication, we find the bind DN of the user by searching the LDAP directory
instead of interpolating the bind DN from userDNTemplate. 


#### Example search filter to find the client bind DN
 
Assuming

* ldapRealm.userSearchAttributeName=uid
* ldapRealm.userObjectClass=person
* client specified login id = "guest"
 
The LDAP Filter for doing a search to find the bind DN would be

    (&(uid=guest)(objectclass=person))

This could find the bind DN to be 

    uid=guest,ou=people,dc=hadoop,dc=apache,dc=org

Please note that the `userSearchAttributeName` need not be part of bindDN.

For example, you could use 

* ldapRealm.userSearchAttributeName=email
* ldapRealm.userObjectClass=person
* client specified login id =  "bill.clinton@gmail.com"

The LDAP Filter for doing a search to find the bind DN would be

    (&(email=bill.clinton@gmail.com)(objectclass=person))

This could find the bind DN to be

    uid=billc,ou=contractors,dc=hadoop,dc=apache,dc=org

#### Advanced LDAP configuration parameters
The table below provides a brief description and sample of the available advanced bind and search configuration parameters.

| Parameter                   | Description                                                    | Default | Sample                                                             |
|-----------------------------|----------------------------------------------------------------|---------|--------------------------------------------------------------------|
| principalRegex              | Parses the principal for insertion into templates via regex.   | (.*)    | (.\*?)\\\\(.\*) _(e.g. match US\tom: {0}=US\tom, {1}=US, {2}=tom)_ |
| userDnTemplate              | Direct user bind DN template.                                  | {0}     | cn={2},dc={1},dc=qa,dc=company,dc=com                              |
| userSearchBase              | Search based template. Used with config below.                 | none    | dc={1},dc=qa,dc=company,dc=com                                     |
| userSearchAttributeName     | Attribute name for simplified search filter.                   | none    | sAMAccountName                                                     |
| userSearchAttributeTemplate | Attribute template for simplified search filter.               | {0}     | {2}                                                                |
| userSearchFilter            | Advanced search filter template. Note \& is \&amp; in XML.     | none    | (\&amp;(objectclass=person)(sAMAccountName={2}))                   |
| userSearchScope             | Search scope: subtree, onelevel, object.                       | subtree | onelevel                                                           |

#### Advanced LDAP configuration combinations
There are also only certain valid combinations of advanced LDAP configuration parameters.

* User DN Template
    * userDnTemplate (Required)
    * principalRegex (Optional)
* User Search by Attribute
    * userSearchBase (Required)
    * userAttributeName (Required)
    * userAttributeTemplate (Optional)
    * userSearchScope (Optional)
    * principalRegex (Optional)
* User Search by Filter
    * userSearchBase (Required)
    * userSearchFilter (Required)
    * userSearchScope (Optional)
    * principalRegex (Optional)

#### Advanced LDAP configuration precedence
The presence of multiple configuration combinations should be avoided.
The rules below clarify which combinations take precedence when present.

1. userSearchBase takes precedence over userDnTemplate
2. userSearchFilter takes precedence over userSearchAttributeName

#### Example provider configuration to use advanced LDAP authentication

The example configuration appears verbose due to the presence of liberal comments 
and illustration of optional parameters and default values.
The configuration that you would use could be much shorter if you rely on default values.

    <provider>
    
        <role>authentication</role>
        <name>ShiroProvider</name>
        <enabled>true</enabled>
        
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
        
        <!-- update the value based on your ldap directory protocol, host and port -->
        <param>
            <name>main.ldapRealm.contextFactory.url</name>
            <value>ldap://hdp.example.com:389</value>
        </param>
        
        <!-- optional, default value: simple
             Update the value based on mechanisms supported by your ldap directory -->
        <param>
            <name>main.ldapRealm.contextFactory.authenticationMechanism</name>
            <value>simple</value>
        </param>
        
        <!-- optional, default value: {0}
             update the value based on your ldap DIT(directory information tree).
             ignored if value is defined for main.ldapRealm.userSearchAttributeName -->
        <param>
            <name>main.ldapRealm.userDnTemplate</name>
            <value>uid={0},ou=people,dc=hadoop,dc=apache,dc=org</value>
        </param>
        
        <!-- optional, default value: null
             If you specify a value for this attribute, useDnTemplate 
             specified above would be ignored and user bind DN would be computed using
             ldap search
             update the value based on your ldap DIT(directory information layout)
             value of search attribute should identity the user uniquely -->
        <param>
            <name>main.ldapRealm.userSearchAttributeName</name>
            <value>uid</value>
        </param>
        
        <!-- optional, default value: false  
             If the value is true, groups in which user is a member are looked up 
             from LDAP and made available  for service level authorization checks -->
        <param>
            <name>main.ldapRealm.authorizationEnabled</name>
            <value>true</value>
        </param>
        
        <!-- bind DN used to search for groups and user bind DN.  
             Required if a value is defined for main.ldapRealm.userSearchAttributeName
             or if the value of main.ldapRealm.authorizationEnabled is true -->
        <param>
            <name>main.ldapRealm.contextFactory.systemUsername</name>
            <value>uid=guest,ou=people,dc=hadoop,dc=apache,dc=org</value>
        </param>
        
        <!-- password for systemUserName.
             Required if a value is defined for main.ldapRealm.userSearchAttributeName
             or if the value of main.ldapRealm.authorizationEnabled is true -->
        <param>
            <name>main.ldapRealm.contextFactory.systemPassword</name>
            <value>${ALIAS=ldcSystemPassword}</value>
        </param>
        
        <!-- optional, default value: simple
             Update the value based on mechanisms supported by your ldap directory -->
        <param>
            <name>main.ldapRealm.contextFactory.systemAuthenticationMechanism</name>
            <value>simple</value>
        </param>
        
        <!-- optional, default value: person
             Objectclass to identify user entries in ldap, used to build search 
             filter to search for user bind DN -->
        <param>
            <name>main.ldapRealm.userObjectClass</name>
            <value>person</value>
        </param>
        
        <!-- search base used to search for user bind DN and groups -->
        <param>
            <name>main.ldapRealm.searchBase</name>
            <value>dc=hadoop,dc=apache,dc=org</value>
        </param>
        
        <!-- search base used to search for user bind DN.
             Defaults to the value of main.ldapRealm.searchBase. 
             If main.ldapRealm.userSearchAttributeName is defined, 
             value for main.ldapRealm.searchBase  or main.ldapRealm.userSearchBase 
             should be defined -->
        <param>
            <name>main.ldapRealm.userSearchBase</name>
            <value>dc=hadoop,dc=apache,dc=org</value>
        </param>
        
        <!-- search base used to search for groups.
             Defaults to the value of main.ldapRealm.searchBase.
             If value of main.ldapRealm.authorizationEnabled is true,
             value for main.ldapRealm.searchBase  or main.ldapRealm.groupSearchBase should be defined -->
        <param>
            <name>main.ldapRealm.groupSearchBase</name>
            <value>dc=hadoop,dc=apache,dc=org</value>
        </param>
        
        <!-- optional, default value: groupOfNames
             Objectclass to identify group entries in ldap, used to build search 
             filter to search for group entries --> 
        <param>
            <name>main.ldapRealm.groupObjectClass</name>
            <value>groupOfNames</value>
        </param>
        
        <!-- optional, default value: member
             If value is memberUrl, we treat found groups as dynamic groups -->
        <param>
            <name>main.ldapRealm.memberAttribute</name>
            <value>member</value>
        </param>
        
        <!-- optional, default value: uid={0}
             Ignored if value is defined for main.ldapRealm.userSearchAttributeName -->
        <param>
            <name>main.ldapRealm.memberAttributeValueTemplate</name>
            <value>uid={0},ou=people,dc=hadoop,dc=apache,dc=org</value>
        </param>
        
        <!-- optional, default value: cn -->
        <param>
            <name>main.ldapRealm.groupIdAttribute</name>
            <value>cn</value>
        </param>
        
        <param>
            <name>urls./**</name>
            <value>authcBasic</value>
        </param>
        
        <!-- optional, default value: 30min -->
        <param>
            <name>sessionTimeout</name>
            <value>30</value>
        </param>
        
    </provider>
        
#### Special note on parameter main.ldapRealm.contextFactory.systemPassword

The value for this could have one of the following 2 formats

* plaintextpassword
* ${ALIAS=ldcSystemPassword}

The first format specifies the password in plain text in the provider configuration.
Use of this format should be limited for testing and troubleshooting.

We strongly recommend using the second format `${ALIAS=ldcSystemPassword}` in production.
This format uses an alias for the password stored in credential store.
In the example `${ALIAS=ldcSystemPassword}`, 
ldcSystemPassword is the alias for the password stored in credential store.

Assuming the plain text password is "hadoop", and your topology file name is "hdp.xml",
you would use following command to create the right password alias in credential store.

    {GATEWAY_HOME}/bin/knoxcli.sh  create-alias ldcSystemPassword --cluster hdp --value hadoop
