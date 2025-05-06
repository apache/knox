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

## Webshell ##

### Introduction
This feature enables shell access to the machine running Apache Knox. Users can SSO into Knox and then access shell using the Knox WebShell URL on knox homepage. There are some out of band configuration changes that are required for the feature to work. 

### Prerequisite ###
This feature works only on *nix systems given it relies on sudoers file. Make sure following prerequisite are met before turning on the feature.

* Make sure Knox process user (user under which knox process runs) exists on local machine.
* Make sure the user used to login exists on local machine.
* Make sure you have proper rights to create/update sudoers file descibed in "Configuration" section below.

### Configuration ###

Webshell is not turned on by default. To enable Webshell following properties needs to be changed in `gateway-site.xml`

    property>
        <name>gateway.websocket.feature.enabled</name>
        <value>true</value>
        <description>Enable/Disable websocket feature.</description>
    </property>

    <property>
        <name>gateway.webshell.feature.enabled</name>
        <value>true</value>
        <description>Enable/Disable webshell feature.</description>
    </property>
    <!-- in case JWT cookie validation for websockets is needed -->
    <property>
        <name>gateway.websocket.JWT.validation.feature.enabled</name>
        <value>true</value>
        <description>Enable/Disable websocket JWT validation feature.</description>
    </property>

Create a sudoers file `/etc/sudoers.d/knox` (assuming, Apache Knox process is running as user `knox`) with mappings for all the users that need WebShell acess on the machine running Apache Knox. 

e.g. the following settings in `sudoers` file let's user `sam` and `knoxui` login to WebShell. Further restrictions on user `sam` and `knoxui` can be applied in `sudoers` file. More info: https://linux.die.net/man/5/sudoers. Here users `sam` and `knoxui` are SSO users that login using Knox authentication providers such as LDAP, PAM etc.

    Defaults env_keep += JAVA_HOME
    Defaults always_set_home
    knox ALL=(sam:ALL) NOPASSWD: /bin/bash
    knox ALL=(knoxui:ALL) NOPASSWD: /bin/bash
