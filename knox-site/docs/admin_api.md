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

| Name       | URL                                                                                     |
|------------|-----------------------------------------------------------------------------------------|
| GatewayAPI | `https://{gateway-host}:{gateway-port}/{gateway-path}/admin/api/v1`                     |

Please note that to access this API, the user attempting to connect must have admin credentials configured on the LDAP Server.

##### API Documentation

| Resource         | Operation | Description                                                   |
|------------------|-----------|---------------------------------------------------------------|
| `version`        | GET       | Get the gateway version and the associated version hash       |
|                  | Example Request | `curl -iku admin:admin-password {GatewayAPI}/version -H Accept:application/json` |
|                  | Example Response | ```json { "ServerVersion": { "version": "VERSION_ID", "hash": "VERSION_HASH" } } ``` |
| `topologies`     | GET       | Get an enumeration of the topologies currently deployed.      |
|                  | Example Request | `curl -iku admin:admin-password {GatewayAPI}/topologies -H Accept:application/json` |
|                  | Example Response | ```json { "topologies": { "topology": [ { "name": "admin", "timestamp": "1501508536000", "uri": "https://localhost:8443/gateway/admin", "href": "https://localhost:8443/gateway/admin/api/v1/topologies/admin" }, { "name": "sandbox", "timestamp": "1501508536000", "uri": "https://localhost:8443/gateway/sandbox", "href": "https://localhost:8443/gateway/admin/api/v1/topologies/sandbox" } ] } } ``` |



