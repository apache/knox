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

### Request Audience Validator ###

The `JWTProvider` federation provider (`org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter`) accepts a bearer JWT and, among other checks, validates the token's `aud` claim. By default this is a fixed-list check: the claim must contain at least one of the audiences configured in `knox.token.audiences`. That fixed-list check is the right model for a token that was minted for a known, static set of consumers, but it says nothing about *where* the token is actually being presented, so it cannot express a requirement like "this token may only be used against the destination it was issued for."

`request.audience.validator` selects a pluggable, alternate audience check in its place:

    <param>
        <name>request.audience.validator</name>
        <value>request.audience.k8s.destination.validation</value>
    </param>

Leaving `request.audience.validator` unset keeps the fixed-list behavior described above for every token that reaches this filter. `gateway-provider-security-jwt` ships one alternate implementation, described below.

#### Kubernetes Destination Audience Validator ####

The Kubernetes Destination Audience Validator (`request.audience.k8s.destination.validation`) checks a token's `aud` claim against the request's actual destination rather than against a fixed list. It only ever replaces the audience check for a token that carries a delegation `act` claim, i.e. a token that was produced by [RFC 8693](https://www.rfc-editor.org/rfc/rfc8693) token exchange; a token with no `act` claim still goes through the fixed-list check regardless of whether this validator is selected.

##### Audience format #####

For a token in scope, each entry in its `aud` claim is expected to be a URL of the form:

    https://cluster-domain[:port]/namespace/service-name[/resource-path]

This format is a deployment convention, not something any RFC prescribes or anything else in Knox validates on the token-minting side — an operator's delegation policies and the callers requesting a delegated token are what make the `aud` values actually take this shape. An entry that does not parse as this shape (wrong scheme, missing authority, userinfo present, fewer than two path segments, an unnormalized or empty path segment, and so on) is never treated as a match candidate, even for a check this validator is not currently configured to perform.

##### How it works #####

Which of the URL's segments are actually compared against the request depends entirely on which of this validator's header parameters are configured — a segment with no configured source is not compared at all, so an `aud` entry that differs from the request only in an unconfigured segment is accepted:

* **cluster-domain** — always compared, against the allow-list in `request.audience.k8s.cluster-domains`. This is the only segment with no way to disable the check.
* **namespace** — compared when `request.audience.k8s.namespace.from.destination.spiffe-id.header.name` and/or `request.audience.k8s.server.name.header.name` is configured. Configuring both is supported and gives two independent sources for the same value; when both are configured, both are read and their namespaces must agree, or the request is rejected.
* **service-name** — compared only when `request.audience.k8s.server.name.header.name` is configured.
* **resource-path** — compared when `request.audience.k8s.path.header.name` is configured.

A configuration that sets a SPIFFE-id header and a path header but no server-name header — validating cluster-domain, namespace, and path, but not service-name — is a supported and expected configuration. Under it, a delegation token minted to authorize calling one service in a namespace at a given path is also accepted when presented to a *different* service in that same namespace at that same path. The token is still bound to one cluster, one namespace, and one path, which is a large reduction from no destination binding at all, but it is not a binding to one specific service; closing that gap requires configuring the server-name header as well, which requires a trusted source for it (see Prerequisites below).

At least one of the three header parameters must be configured, or the topology fails to start.

Namespace and service-name segments are compared case-insensitively, as DNS labels. The resource path is compared case-sensitively, and never percent-decoded on either side — a raw and a percent-encoded candidate are both derived from the `aud` entry's path and either is accepted, but an escaped `%2F` in either value is never treated as equal to a literal `/`. A path taken from a header is required to already be free of `.` and `..` segments and of empty segments; such a value is rejected rather than normalized, since normalizing it here could disagree with however the component that actually routes the request resolves it.

##### Prerequisites #####

Each of the three header parameters below names a header this validator trusts at face value, with no independent verification. Every one of them **must** be populated only by a component upstream of Knox that is trusted to set or overwrite it correctly with a value it independently determined — never a header an untrusted caller could set or influence. If that guarantee does not hold for a configured header, an untrusted caller controls the corresponding side of the comparison, and the destination binding this validator provides is defeated. Establishing that guarantee is the responsibility of the deployment's network/ingress layer, not something this validator can check.

If the path this validator checks can be changed by the surrounding infrastructure after this validator runs (for example, a routing rule that rewrites the request path), configure the path header to carry the path as it was *before* any such rewrite — the audience being validated describes the resource the caller requested, not the path an internal rewrite happens to route it to.

##### Configuration parameters #####

All parameters are set as `<param>` entries on the `JWTProvider` provider, alongside `request.audience.validator`.

Name | Description | Default
---------|-----------|--------
request.audience.k8s.namespace.from.destination.spiffe-id.header.name | HTTP header carrying the destination workload's SPIFFE id, e.g. `spiffe://trust-domain/ns/namespace/sa/service-account`. When set, the namespace inside the SPIFFE id is compared against the `aud` entry's namespace segment. Trusted-header parameter — see Prerequisites. | n/a (unset; namespace is then not matched from this source)
request.audience.k8s.server.name.header.name | HTTP header carrying the destination workload's FQDN, in the form `server-name.namespace<cluster-suffix>`, optionally followed by `:port`. When set, both the server-name and namespace segments are compared. Trusted-header parameter — see Prerequisites. | n/a (unset; this header is then not read at all)
request.audience.k8s.server.name.cluster-suffix | Suffix that terminates the FQDN read from the header above, e.g. `.svc.cluster.local`. Only read when that header parameter is itself configured. | `.svc.cluster.local`
request.audience.k8s.path.header.name | HTTP header carrying the path to match against the `aud` entry's resource-path segment. Trusted-header parameter — see Prerequisites. | n/a (unset; resource path is then not matched)
request.audience.k8s.path.header.from.url | Whether the header above carries a full URL whose path component should be extracted, rather than already being the bare path to match. Only read when that header parameter is itself configured. | `false`
request.audience.k8s.cluster-domains | Comma-separated allow-list of cluster domains (host, with an optional `:port`, defaulting to `443`) an `aud` entry's authority may match. Always enforced; there is no way to disable this check. | `service.local` (a placeholder that fails closed until set to the deployment's own cluster domain(s))
request.audience.k8s.require-all-audiences-match | Whether at least one `aud` entry matching the destination is sufficient (`false`, the ordinary "am I an intended audience" semantic of [RFC 7519 §4.1.3](https://www.rfc-editor.org/rfc/rfc7519#section-4.1.3)), or whether the claim must be non-empty and every entry must match (`true`). See the note below on combining this with the minting-side flags. | `false`

`request.audience.k8s.require-all-audiences-match=true` is independent of, and does not by itself prevent, a delegation token being minted with more than one audience in the first place — it only changes how this validator reacts to one once presented. `JWTFederationFilter` separately exposes `delegation.enforce.requested.audience.required` and `delegation.enforce.requested.audience.max.one`, which constrain what a delegation token may be minted with. A deployment that wants every delegation token to carry exactly one audience should set all three: the two minting-side flags stop a multi-audience token from being issued, and `require-all-audiences-match` independently rejects one at the destination even if it is minted anyway, for example by an older or misconfigured issuer.

##### Example topology #####

    <provider>
        <role>federation</role>
        <name>JWTProvider</name>
        <enabled>true</enabled>
        <param>
            <name>request.audience.validator</name>
            <value>request.audience.k8s.destination.validation</value>
        </param>
        <param>
            <!-- Must be set by a trusted upstream component; see Prerequisites -->
            <name>request.audience.k8s.namespace.from.destination.spiffe-id.header.name</name>
            <value>x-destination-spiffe-id</value>
        </param>
        <param>
            <!-- Must be set by a trusted upstream component; see Prerequisites -->
            <name>request.audience.k8s.path.header.name</name>
            <value>x-destination-path</value>
        </param>
        <param>
            <name>request.audience.k8s.cluster-domains</name> <!-- default = service.local -->
            <value>my-cluster.example.org</value>
        </param>
        <param>
            <name>request.audience.k8s.require-all-audiences-match</name> <!-- default = false -->
            <value>false</value>
        </param>
    </provider>

This example configures the SPIFFE-id and path headers but not the server-name header, so cluster-domain, namespace, and path are validated but service-name is not — the supported first-release shape described under How it works above. Adding `request.audience.k8s.server.name.header.name` (and, if the deployment's cluster FQDN suffix differs from the Kubernetes default, `request.audience.k8s.server.name.cluster-suffix`) additionally binds the token to one specific service.

##### Example request #####

Given the topology above, and a delegation token whose `aud` claim contains `https://my-cluster.example.org/analytics/reporting-svc/v1/reports`, presented alongside headers set by a trusted upstream component:

    curl -k -i \
        --header "Authorization: Bearer <delegation-token>" \
        --header "x-destination-spiffe-id: spiffe://example.org/ns/analytics/sa/reporting-svc" \
        --header "x-destination-path: /v1/reports" \
        -v https://knox.example.org:8443/gateway/sandbox/webhdfs/v1/tmp?op=LISTSTATUS

The audience is accepted because the namespace (`analytics`) and path (`/v1/reports`) taken from the trusted headers match the `aud` entry, and the entry's authority matches the configured `cluster-domains` allow-list. Presenting the same token with `x-destination-spiffe-id` naming a different namespace, or `x-destination-path` naming a different path, is rejected.

##### Failure modes #####

The validator rejects the request (a bad-request response, since audience validation runs as part of token validation) and logs a diagnostic message when:

* a token carrying an `act` claim has no `aud` claim at all;
* a configured header is missing, empty, or fails to parse in the shape this validator requires for it (an unparseable SPIFFE id, a server-name header that doesn't end in the configured cluster-suffix with exactly two labels before it, and so on);
* both namespace sources are configured and disagree;
* no `aud` entry both parses as the documented URL shape and matches every segment this validator is configured to check (or, with `require-all-audiences-match=true`, any entry fails to).

A token with no `act` claim is unaffected by any of the above and is validated against the fixed `knox.token.audiences` list as usual.
