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

### PAM based Authentication ###

There is a large number of pluggable authentication modules available on many Linux installations and from vendors of authentication solutions that are great to leverage for authenticating access to Hadoop through the Knox Gateway. In addition to LDAP support described in this guide, the ShiroProvider also includes support for PAM based authentication for unix based systems.

This opens up the integration possibilities to many other readily available authentication mechanisms as well as other implementations for LDAP based authentication. More flexibility may be available through various PAM modules for group lookup, more complicated LDAP schemas or other areas where the KnoxLdapRealm is not sufficient.

#### Configuration ####
##### Overview #####
The primary motivation for leveraging PAM based authentication is to provide the ability to use the configuration provided by existing PAM modules that are available in a system's `/etc/pam.d/` directory. Therefore, the solution provided here is as simple as possible in order to allow the PAM module config itself to be the source of truth. What we do need to configure is the fact that we are using PAM through the `main.pamRealm` parameter and the KnoxPamRealm classname and the particular PAM module to use with the `main.pamRealm.service` parameter in the below example we have 'login'.

    <provider> 
       <role>authentication</role> 
       <name>ShiroProvider</name> 
       <enabled>true</enabled> 
       <param> 
            <name>sessionTimeout</name> 
            <value>30</value>
        </param>                                              
        <param>
            <name>main.pamRealm</name> 
            <value>org.apache.knox.gateway.shirorealm.KnoxPamRealm</value>
        </param> 
        <param>                                                    
           <name>main.pamRealm.service</name> 
           <value>login</value> 
        </param>
        <param>                                                    
           <name>urls./**</name> 
           <value>authcBasic</value> 
       </param>
    </provider>
  

As a non-normative example of a PAM config file see the below from my MacBook `/etc/pam.d/login`:

    # login: auth account password session
    auth       optional       pam_krb5.so use_kcminit
    auth       optional       pam_ntlm.so try_first_pass
    auth       optional       pam_mount.so try_first_pass
    auth       required       pam_opendirectory.so try_first_pass
    account    required       pam_nologin.so
    account    required       pam_opendirectory.so
    password   required       pam_opendirectory.so
    session    required       pam_launchd.so
    session    required       pam_uwtmp.so
    session    optional       pam_mount.so

The first four fields are: service-name, module-type, control-flag and module-filename. The fifth and greater fields are for optional arguments that are specific to the individual authentication modules.

The second field in the configuration file is the module-type, it indicates which of the four PAM management services the corresponding module will provide to the application. Our sample configuration file refers to all four groups:

* auth: identifies the PAMs that are invoked when the application calls pam_authenticate() and pam_setcred().
* account: maps to the pam_acct_mgmt() function.
* session: indicates the mapping for the pam_open_session() and pam_close_session() calls.
* password: group refers to the pam_chauthtok() function.

Generally, you only need to supply mappings for the functions that are needed by a specific application. For example, the standard password changing application, passwd, only requires a password group entry; any other entries are ignored.

The third field indicates what action is to be taken based on the success or failure of the corresponding module. Choices for tokens to fill this field are:

* requisite: Failure instantly returns control to the application indicating the nature of the first module failure.
* required: All these modules are required to succeed for libpam to return success to the application.
* sufficient: Given that all preceding modules have succeeded, the success of this module leads to an immediate and successful return to the application (failure of this module is ignored).
* optional: The success or failure of this module is generally not recorded.

The fourth field contains the name of the loadable module, pam_*.so. For the sake of readability, the full pathname of each module is not given. Before Linux-PAM-0.56 was released, there was no support for a default authentication-module directory. If you have an earlier version of Linux-PAM installed, you will have to specify the full path for each of the modules. Your distribution most likely placed these modules exclusively in one of the following directories: /lib/security/ or /usr/lib/security/.

Also, find below a non-normative example of a PAM config file(/etc/pam.d/login) for Ubuntu:

    #%PAM-1.0
    
    auth       required     pam_sepermit.so
    # pam_selinux.so close should be the first session rule
    session    required     pam_selinux.so close
    session    required     pam_loginuid.so
    # pam_selinux.so open should only be followed by sessions to be executed in the user context
    session    required     pam_selinux.so open env_params
    session    optional     pam_keyinit.so force revoke
    
    session    required     pam_env.so user_readenv=1 envfile=/etc/default/locale
    @include password-auth
