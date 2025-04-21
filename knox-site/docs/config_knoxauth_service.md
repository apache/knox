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
## Knox Auth Service

### Introduction
With workloads moving to containers, it was necessary that Knox supports new ways of authentication needs of containers. As part of this effort, the Knox team developed a new internal service, called `KNOX-AUTH-SERVICE`. This service gathers a collection of public REST API endpoints that allows other developers to integrate Knox in their microservice/DEVOPS architectures using containers (such as docker or k9s).

### Configuration

This service can be added to any Knox topology as an internal service as follows:

    <service>
         <role>KNOX-AUTH-SERVICE</role>
         <param>
           <name>preauth.auth.header.actor.id.name</name>
           <value>X-Knox-Actor-ID</value>
         </param>
         <param>
           <name>preauth.auth.header.actor.groups.prefix</name>
           <value>X-Knox-Actor-Groups</value>
         </param>
         <param>
           <name>preauth.group.filter.pattern</name>
           <value>.*</value>
         </param>
         <param>
           <name>auth.bearer.token.env</name>
           <value>BEARER_AUTH_TOKEN</value>
         </param>
    </service>


### Available REST API endpoints

#### auth/api/v1/pre

This REST API endpoint has a very simple job: if a valid principal is found in the incoming request, a header is added to the response (by default `X-Knox-Actor-ID`) with the principal name. In addition, if the authenticated subject has group(s), it (they) will be added as comma-separated entries in the header(s) of the default form of `X-Knox-Actor-Groups-#num`. Each group header has a character limit of 1000 to keep them reasonably sized. The header names can be customized via the `preauth.auth.header.actor.id.name` and `preauth.auth.header.actor.groups.prefix` service parameters.

End users may filter user groups by setting the `preauth.group.filter.pattern` service parameter to a valid regular expression. By default, all the user gropus are added into the `X-Knox-Actor-Groups-#num` header.

Sample `curl` commands are available in this [GitHub Pull Request](https://github.com/apache/knox/pull/625).

#### auth/api/v1/bearer

This REST API enpoint populates the HTTP "Authorization" header with the `Bearer Token` in the HTTP response object obtained from an environment variable.  The current implementation assumes that the token is not rotated as it never gets exposed to the end-user. By default, the `BEARER_AUTH_TOKEN` environment variable is expected to hold the Bearer token. This can be customized by configuring the `auth.bearer.token.env` service parameter to the desired value.

Sample `curl` commands are available in this [GitHub Pull Request](https://github.com/apache/knox/pull/627).

