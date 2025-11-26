<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->
## Knox Helm Chart

This Helm chart is designed for deploying Knox.

### Installation

To install the chart, use the following command:

```shell
helm install knox . --namespace knox --create-namespace
```
### Additional Information

This repository employs Helm for templating both topologies and various Kubernetes objects. The `values.yaml` file
defines default parameters, which can be overridden during deployment using the `helm install` command.

- **Kubernetes Templates**:
    - `deployment.yaml`: Deploys the Knox service.
    - `hpa.yaml`: Configures the `HorizontalPodAutoscaler` for dynamic pod scaling based on resource utilization.
    - `ingress.yaml`: Manages external access to the services in a cluster.
    - `service.yaml`: Exposes an application running in pods as a network service.
    - `serviceaccount.yaml`: Provides an identity for processes running in a Pod.
