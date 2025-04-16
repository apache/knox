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

### Metrics ###

See the KIP for details on the implementation of metrics available in the gateway.

[Metrics KIP](https://cwiki.apache.org/confluence/display/KNOX/KIP-2+Metrics)

#### Metrics Configuration ####

Metrics configuration can be done in `gateway-site.xml`.

The initial configuration is mainly for turning on or off the metrics collection and then enabling reporters with their required config.

The two initial reporters implemented are JMX and Graphite.

    gateway.metrics.enabled 

Turns on or off the metrics, default is 'true'
 
    gateway.jmx.metrics.reporting.enabled

Turns on or off the jmx reporter, default is 'true'

    gateway.graphite.metrics.reporting.enabled

Turns on or off the graphite reporter, default is 'false'

    gateway.graphite.metrics.reporting.host
    gateway.graphite.metrics.reporting.port
    gateway.graphite.metrics.reporting.frequency

The above are the host, port and frequency of reporting (in seconds) parameters for the graphite reporter.

