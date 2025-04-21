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

### X-Forwarded-* Headers Support ###
Out-of-the-box Knox provides support for some `X-Forwarded-*` headers through the use of a Servlet Filter. Specifically the
headers handled/populated by Knox are:

* X-Forwarded-For
* X-Forwarded-Proto
* X-Forwarded-Port
* X-Forwarded-Host
* X-Forwarded-Server
* X-Forwarded-Context

This functionality can be turned off by a configuration setting in the file gateway-site.xml and redeploying the
necessary topology/topologies.

The setting is (under the 'configuration' tag) :

    <property>
        <name>gateway.xforwarded.enabled</name>
        <value>false</value>
    </property>

If this setting is absent, the default behavior is that the `X-Forwarded-*` header support is on or in other words,
`gateway.xforwarded.enabled` is set to `true` by default.


#### Header population ####

The following are the various rules for population of these headers:

##### X-Forwarded-For #####

This header represents a list of client IP addresses. If the header is already present Knox adds a comma separated value
to the list. The value added is the client's IP address as Knox sees it. This value is added to the end of the list.

##### X-Forwarded-Proto #####

The protocol used in the client request. If this header is passed into Knox its value is maintained, otherwise Knox will
populate the header with the value 'https' if the request is a secure one or 'http' otherwise.

##### X-Forwarded-Port #####

The port used in the client request. If this header is passed into Knox its value is maintained, otherwise Knox will
populate the header with the value of the port that the request was made coming into Knox.

##### X-Forwarded-Host #####

Represents the original host requested by the client in the Host HTTP request header. The value passed into Knox is maintained
by Knox. If no value is present, Knox populates the header with the value of the HTTP Host header.

##### X-Forwarded-Server #####

The hostname of the server Knox is running on.

##### X-Forwarded-Context #####

This header value contains the context path of the request to Knox.



