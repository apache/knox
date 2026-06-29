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

### Preauthenticated SSO Provider ###

A number of SSO solutions provide mechanisms for federating an authenticated identity across applications. These mechanisms are at times simple HTTP Header type tokens that can be used to propagate the identity across process boundaries.

Knox Gateway needs a pluggable mechanism for consuming these tokens and federating the asserted identity through an interaction with the Hadoop cluster. 

**CAUTION: The use of this provider requires that proper network security and identity provider configuration and deployment does not allow requests directly to the Knox gateway. Otherwise, this provider will leave the gateway exposed to identity spoofing.**

#### Configuration ####
##### Overview #####
This provider was designed for use with identity solutions such as those provided by CA's SiteMinder and IBM's Tivoli Access Manager. While direct testing with these products has not been done, there has been extensive unit and functional testing that ensure that it should work with such providers.

The HeaderPreAuth provider is configured within the topology file and has a minimal configuration that assumes SM_USER for CA SiteMinder. The following example is the bare minimum configuration for SiteMinder (with no IP address validation).

    <provider>
        <role>federation</role>
        <name>HeaderPreAuth</name>
        <enabled>true</enabled>
    </provider>

The following table describes the configuration options for the web app security provider:

##### Descriptions #####

Name | Description | Default
---------|-----------|--------
preauth.validation.method   | Optional parameter that indicates the types of trust validation to perform on incoming requests. There could be one or more comma-separated validators defined in this property. If there are multiple validators, Apache Knox validates each validator in the same sequence as it is configured. This works similar to short-circuit AND operation i.e. if any validator fails, Knox does not perform further validation and returns overall failure immediately. Possible values are: null, preauth.default.validation, preauth.ip.validation, preauth.k8s.service.account.validation (see [Kubernetes ServiceAccount Validator](#kubernetes-serviceaccount-validator)), custom validator (details described in [Custom Validator](dev-guide.html#Validator)). Failure results in a 403 forbidden HTTP status response.| null - which means 'preauth.default.validation' that is  no validation will be performed and that we are assuming that the network security and external authentication system is sufficient. 
preauth.ip.addresses        | Optional parameter that indicates the list of trusted ip addresses. When preauth.ip.validation is indicated as the validation method this parameter must be provided to indicate the trusted ip address set. Wildcarded IPs may be used to indicate subnet level trust. ie. 127.0.* | null - which means that no validation will be performed.
preauth.custom.header       | Required parameter for indicating a custom header to use for extracting the preauthenticated principal. The value extracted from this header is utilized as the PrimaryPrincipal within the established Subject. An incoming request that is missing the configured header will be refused with a 401 unauthorized HTTP status. | SM_USER for SiteMinder usecase
preauth.custom.group.header | Optional parameter for indicating a HTTP header name that contains a comma separated list of groups. These are added to the authenticated Subject as group principals. A missing group header will result in no groups being extracted from the incoming request and a log entry but processing will continue. | null - which means that there will be no group principals extracted from the request and added to the established Subject.

NOTE: Mutual authentication can be used to establish a strong trust relationship between clients and servers while using the Preauthenticated SSO provider. See the configuration for Mutual Authentication with SSL in this document.

##### Configuration for SiteMinder
The following is an example of a configuration of the preauthenticated SSO provider that leverages the default SM_USER header name - assuming use with CA SiteMinder. It further configures the validation based on the IP address from the incoming request.

    <provider>
        <role>federation</role>
        <name>HeaderPreAuth</name>
        <enabled>true</enabled>
        <param><name>preauth.validation.method</name><value>preauth.ip.validation</value></param>
        <param><name>preauth.ip.addresses</name><value>127.0.0.2,127.0.0.1</value></param>
    </provider>

##### REST Invocation for SiteMinder
The following curl command can be used to request a directory listing from HDFS while passing in the expected header SM_USER.

    curl -k -i --header "SM_USER: guest" -v https://localhost:8443/gateway/sandbox/webhdfs/v1/tmp?op=LISTSTATUS

Omitting the `--header "SM_USER: guest"` above will result in a rejected request.

##### Configuration for IBM Tivoli AM
As an example for configuring the preauthenticated SSO provider for another SSO provider, the following illustrates the values used for IBM's Tivoli Access Manager:

    <provider>
        <role>federation</role>
        <name>HeaderPreAuth</name>
        <enabled>true</enabled>
        <param><name>preauth.custom.header</name><value>iv_user</value></param>
        <param><name>preauth.custom.group.header</name><value>iv_group</value></param>
        <param><name>preauth.validation.method</name><value>preauth.ip.validation</value></param>
        <param><name>preauth.ip.addresses</name><value>127.0.0.2,127.0.0.1</value></param>
    </provider>

##### REST Invocation for Tivoli AM
The following curl command can be used to request a directory listing from HDFS while passing in the expected headers of iv_user and iv_group. Note that the iv_group value in this command matches the expected ACL for webhdfs in the above topology file. Changing this from "admin" to "admin2" should result in a 401 unauthorized response.

    curl -k -i --header "iv_user: guest" --header "iv_group: admin" -v https://localhost:8443/gateway/sandbox/webhdfs/v1/tmp?op=LISTSTATUS

Omitting the `--header "iv_user: guest"` above will result in a rejected request.

#### Kubernetes ServiceAccount Validator ####

The Kubernetes ServiceAccount Validator (`preauth.k8s.service.account.validation`) is a `PreAuthValidator` implementation shipped in the `gateway-provider-security-k8s` module. It is intended for deployments where Knox runs inside a Kubernetes cluster and accepts pre-authenticated requests from workloads whose identity is asserted via a [SPIFFE](https://spiffe.io/) ID.

The validator enforces a binding between:

1. the calling workload's SPIFFE identity (its Kubernetes namespace and ServiceAccount), and
2. the user the caller is asserting in the `HeaderPreAuth` custom user header.

The asserted user is only accepted if the corresponding ServiceAccount in Kubernetes carries an annotation that explicitly authorizes that ServiceAccount to assert that user. This prevents a compromised or misconfigured workload from spoofing arbitrary identities through the pre-auth header.

##### How it works #####

For each incoming request the validator:

1. Reads the SPIFFE header (default `x-spiffe-id`). Its value must be a Kubernetes-style SPIFFE ID of the form `spiffe://<trust-domain>/ns/<namespace>/sa/<service-account>`. Anything else is rejected.
2. Reads the asserted user header (default `x-knoxidf-obo.username`). A missing or empty value is rejected.
3. Looks up the `ServiceAccount` named `<service-account>` in namespace `<namespace>` via the Kubernetes API and reads the annotation configured by `preauth.k8s.sa.user.annotation` (default `knox.apache.org/owner-username`).
4. Accepts the request only if the annotation is present and its value equals the asserted user. Otherwise the request is rejected and `HeaderPreAuth` returns 403.

ServiceAccount annotation lookups are cached in-memory (Caffeine, write-expiring). Successful lookups (including "annotation missing") are cached; transient errors talking to the Kubernetes API are not cached so they will be retried on the next request.

##### Prerequisites #####

- Knox must run inside a Kubernetes pod (or otherwise have a kubeconfig discoverable by the fabric8 `KubernetesClient`).
- The ServiceAccount Knox runs as must have permission to `get` `serviceaccounts` in any namespace whose workloads it must authorize. Grant this with a `ClusterRole`/`ClusterRoleBinding` (or namespace-scoped `Role`/`RoleBinding`).
- A trust path must inject the SPIFFE header into incoming requests. The network/mesh configuration MUST prevent clients from setting this header themselves; otherwise this validator can be bypassed.
- Each ServiceAccount permitted to assert a user MUST be annotated with the configured annotation key, e.g.:

        apiVersion: v1
        kind: ServiceAccount
        metadata:
          name: my-workload-sa
          namespace: analytics
          annotations:
            knox.apache.org/owner-username: alice

##### Configuration parameters #####

All parameters are set as `<param>` entries on the `HeaderPreAuth` provider.

Name | Description | Default
---------|-----------|--------
preauth.validation.method            | Must include `preauth.k8s.service.account.validation` to enable this validator. May be combined with other validators (comma-separated, short-circuit AND). | n/a
preauth.k8s.sa.spiffe.header         | HTTP header carrying the caller's SPIFFE ID. Must be set/overwritten by a trusted hop — never by clients directly. | `x-spiffe-id`
preauth.k8s.sa.custom.header         | HTTP header carrying the asserted user that the caller is requesting to act as. This is the value validated against the ServiceAccount annotation. | `x-knoxidf-obo.username`
preauth.k8s.sa.user.annotation       | Annotation key on the `ServiceAccount` whose value, when equal to the asserted user, authorizes the assertion. | `knox.apache.org/owner-username`
preauth.k8s.sa.cache.ttl.seconds     | Write-expiry TTL (in seconds) for cached ServiceAccount annotation lookups. Must be `> 0`. Lower values pick up annotation changes faster at the cost of more API calls. | `60`
preauth.k8s.sa.cache.max.size        | Maximum number of distinct `(namespace, service-account)` entries kept in the lookup cache. Must be `> 0`. | `1000`

Note: the user header default (`x-knoxidf-obo.username`) is independent of the `HeaderPreAuth` `preauth.custom.header` parameter — `preauth.custom.header` controls which header `HeaderPreAuth` extracts as the primary principal, while `preauth.k8s.sa.custom.header` controls which header this validator checks against the ServiceAccount annotation. Configure both to the same header name in deployments where the same value plays both roles.

##### Example topology #####

    <provider>
        <role>federation</role>
        <name>HeaderPreAuth</name>
        <enabled>true</enabled>
        <param>
            <name>preauth.validation.method</name>
            <value>preauth.k8s.service.account.validation</value>
        </param>
        <param>
            <name>preauth.custom.header</name>
            <value>x-knoxidf-obo.username</value>
        </param>
        <param>
            <name>preauth.k8s.sa.spiffe.header</name>
            <value>x-spiffe-id</value>
        </param>
        <param>
            <name>preauth.k8s.sa.custom.header</name>
            <value>x-knoxidf-obo.username</value>
        </param>
        <param>
            <name>preauth.k8s.sa.user.annotation</name>
            <value>knox.apache.org/owner-username</value>
        </param>
        <param>
            <name>preauth.k8s.sa.cache.ttl.seconds</name>
            <value>60</value>
        </param>
        <param>
            <name>preauth.k8s.sa.cache.max.size</name>
            <value>1000</value>
        </param>
    </provider>

##### Example request #####

Assuming the namespace `analytics` contains a ServiceAccount `my-workload-sa` annotated with `knox.apache.org/owner-username: alice`, and the service mesh injects `x-spiffe-id` for that workload:

    curl -k -i \
        --header "x-spiffe-id: spiffe://example.org/ns/analytics/sa/my-workload-sa" \
        --header "x-knoxidf-obo.username: alice" \
        -v https://knox.example.org:8443/gateway/sandbox/webhdfs/v1/tmp?op=LISTSTATUS

The request is accepted and processed as user `alice`. Sending the same request with `x-knoxidf-obo.username: bob` (or with a SPIFFE ID pointing to a ServiceAccount that is not annotated for `alice`) yields a 403 response.

##### Failure modes #####

The validator rejects the request (causing `HeaderPreAuth` to return 403) and emits a WARN log entry when:

- the SPIFFE header is missing or empty;
- the user header is missing or empty;
- the SPIFFE header value is not a parseable Kubernetes SPIFFE ID (`spiffe://.../ns/<ns>/sa/<sa>`);
- the target `ServiceAccount` does not exist or carries no annotation under the configured key;
- the annotation value does not match the asserted user.

Errors talking to the Kubernetes API are logged at ERROR and result in rejection but are not cached, so transient API outages do not produce sticky 403s once the API recovers.
