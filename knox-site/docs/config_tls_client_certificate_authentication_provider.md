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

## TLS Client Certificate Provider ##

The TLS client certificate authentication provider enables establishing the user based on the client provided TLS certificate. The user will be the DN from the certificate. This provider requires that the gateway is configured to require client authentication with either `gateway.client.auth.wanted` or `gateway.client.auth.needed` ( #[Mutual Authentication with SSL] ).

### Configuration ###

```xml
<provider>
    <role>authentication</role>
    <name>ClientCert</name>
    <enabled>true</enabled>
</provider>
```

