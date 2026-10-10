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

The Kubernetes Destination Audience Validator (`request.audience.k8s.destination.validation`) checks a token's `aud` claim against the request's actual destination rather than against a fixed list.

It always validates a token carrying a delegation `act` claim, i.e. a token that was produced by [RFC 8693](https://www.rfc-editor.org/rfc/rfc8693) token exchange. A token with no `act` claim is validated the same way by default, as described in Non-delegated tokens below; setting `request.audience.k8s.validate.audiences.without.act.claim` to `false` sends such a token to the fixed `knox.token.audiences` check instead.

##### Audience format #####

Each entry in the `aud` claim of a token in scope is expected to be a URL in one of two forms. By default, both forms are tried for every entry, in this order:

1. The **DNS form**, the Kubernetes service DNS name a local caller uses to reach the destination:

       https://<service>[.<namespace>[.svc[.<cluster-domain>]]][:port][/resource-path]

2. The **custom form**:

       https://cluster-domain[:port]/[skipped-segments/]namespace/service-name[/resource-path]

An entry is accepted as a match candidate if either form accepts it, and is rejected only if neither does. `request.audience.k8s.dns.format.enabled` (default `true`) controls whether the DNS form is tried at all; see DNS form below for why a deployment might turn it off. When it is `false`, every entry is evaluated only as the custom form, and `request.audience.k8s.dns.format.cluster.domain` is unused.

Neither form is validated on the token-minting side by Knox itself or by any RFC; it is a deployment convention that an operator's delegation policies and the callers requesting a delegated token are responsible for following. An entry that does not parse as either shape it is tried against fails validation.

###### DNS form ######

The host may be shortened from the right, the same way an ordinary DNS resolver search path allows. With a service named `reporting-svc`, a namespace of `analytics`, and `request.audience.k8s.dns.format.cluster.domain` left at its default `cluster.local`, all of the following hosts parse:

Host | Meaning
---------|-----------
`reporting-svc` | Service name only. The entry carries no namespace of its own; see below.
`reporting-svc.analytics` | Service and namespace.
`reporting-svc.analytics.svc` | Service and namespace, with the literal `svc` label.
`reporting-svc.analytics.svc.cluster` | As above, plus the first label of the cluster domain.
`reporting-svc.analytics.svc.cluster.local` | The full cluster domain.

A host of three or more labels is rejected unless its third label is literally `svc`, and the labels after `svc`, if any, must be a label-boundary prefix of `dns.format.cluster.domain`'s own labels, taken from the left. For example, with `dns.format.cluster.domain` left at `cluster.local`, a host ending in `.svc.local` alone (skipping `cluster`) does not parse, even though `local` is itself one of `cluster.local`'s labels. A trailing dot on the host, or an empty label anywhere in it, is also rejected; a trailing dot is deliberately not accepted, since no normal client request produces one.

A one-label host carries no namespace. For such an entry, the namespace instead comes from the SOURCE workload's SPIFFE id (see `request.audience.k8s.source.spiffe-id.header.name` below), and that value must equal the destination namespace. If no destination namespace is configured at all, or no source SPIFFE id is available, the entry does not match. This is the one case in this validator where a segment with no configured source is still compared against, rather than being skipped.

A port in the DNS-form entry is parsed only so it does not break parsing of the rest of the host, then discarded: it is never compared against anything, since a mesh or sidecar in front of the destination may rewrite the port in flight.

A deployment that fronts more than one Kubernetes cluster, or more than one trust domain, behind a single validator instance should set `request.audience.k8s.dns.format.enabled` to `false`. A DNS-form host carries no cluster or trust-domain identifier of its own, so there is no way to tell, from the entry alone, which cluster or trust domain a bare `<service>.<namespace>` host belongs to. The custom form does not have this ambiguity, since its cluster-domain segment is checked against an explicit allow-list.

###### Custom form ######

    https://cluster-domain[:port]/namespace/service-name[/resource-path]

This is the simple case, and applies whenever `request.audience.k8s.audience.path.prefix` (see below) is left unset: namespace and service-name must begin straight after the authority. The cluster-domain segment (host and effective port) is always checked against the allow-list in `request.audience.k8s.cluster-domains`, with no way to disable the check. This is a separate check from the DNS form's `request.audience.k8s.dns.format.cluster.domain` above, not the same check applied to both forms: the custom form matches its authority segment literally against this allow-list, while the DNS form instead matches a label-boundary suffix of the whole host against a single configured domain. See How this validator matches a request below for both side by side.

###### Optional path prefix (custom form only) ######

`request.audience.k8s.audience.path.prefix` is an optional parameter for deployments that need a custom-form `aud` entry's path to carry extra leading segments before namespace and service-name, for example for network routing. Left unset (the default), a custom-form entry must take the simple form shown above. When set, a custom-form entry instead takes the form:

    https://cluster-domain[:port]/[skipped-segments/]path-prefix/namespace/service-name[/resource-path]

The path is searched for the first (leftmost) occurrence of `/path-prefix/`; namespace, service-name, and resource-path are then parsed starting right after it. Everything before that point, the `skipped-segments` if any, is skipped over unparsed, not validated: `audience.path.prefix` only locates where parsing resumes. It is ordinarily a single path segment, but it may itself contain `/` to require several contiguous segments to appear together, as one indivisible token, before parsing resumes. An entry whose path never contains `/path-prefix/` does not parse, the same as any other ill-shaped entry: this parameter relocates where namespace and service-name are read from, it does not make the check more permissive.

A DNS-form entry has no leading segments to skip, since its whole path is always the resource path, so `audience.path.prefix` plays no part in matching a DNS-form entry.

##### How this validator matches a request to an audience entry #####

Which of the URL's segments are actually compared against the request depends entirely on which of this validator's header parameters are configured. A segment with no configured source is not compared at all (the DNS form's namespace-less host is the one exception, described above), so an `aud` entry that differs from the request only in an unconfigured segment is accepted. At least one of the destination SPIFFE-id header, the server-name header, or the path header must be configured, or the topology fails to start.

Cluster domain is checked differently for each form, since the two forms carry it differently:

* **Cluster domain, DNS form.** Checked against `request.audience.k8s.dns.format.cluster.domain`: the labels after the required `svc` label must be a label-boundary prefix of it, taken from the left. See DNS form above for the exact rule.
* **Cluster domain, custom form.** Checked as a literal match of the authority against the `request.audience.k8s.cluster-domains` allow-list. See Custom form above.

Both checks are always enforced, with no way to disable either one. They are two different mechanisms, not two configurations of a single mechanism, so configuring one has no effect on the other.

The remaining segments are compared the same way regardless of which form an entry parsed as:

* **Namespace.** Compared when `request.audience.k8s.namespace.from.destination.spiffe-id.header.name` and/or `request.audience.k8s.server.name.header.name` is configured. Configuring both is supported and gives two independent sources for the same value; when both are configured, both are read and their namespaces must agree, or the request is rejected.
* **Service name.** Compared only when `request.audience.k8s.server.name.header.name` is configured.
* **Resource path.** Compared when `request.audience.k8s.path.header.name` is configured, and when the `aud` entry itself carries a path other than exactly `/`. See Path matching below.

A configuration that sets a SPIFFE-id header and a path header but no server-name header (validating cluster domain, namespace, and path, but not service name) is supported. Under it, a delegation token minted to authorize calling one service in a namespace at a given path is also accepted when presented to a *different* service in that same namespace at that same path. The token is still bound to one cluster, one namespace, and one path, which is a large reduction from no destination binding at all, but it is not a binding to one specific service; closing that gap requires configuring the server-name header as well, which requires a trusted source for it (see Prerequisites below).

Namespace and service-name segments are compared case-insensitively. The resource path is compared case-sensitively, and never percent-decoded on either side: a raw and a percent-encoded candidate are both derived from the `aud` entry's path and either is accepted, but an escaped `%2F` in either value is never treated as equal to a literal `/`. A path taken from a header is required to already be free of `.` and `..` segments and of empty segments; such a value is rejected rather than normalized, since normalizing it here could disagree with however the component that actually routes the request resolves it.

##### Configuration parameters #####

All parameters are set as `<param>` entries on the `JWTProvider` provider, alongside `request.audience.validator`. They are grouped below by what they apply to.

###### Trusted-header parameters ######

These name the headers this validator reads; see Prerequisites below for why each one must come from a trusted source. They apply regardless of which `aud` form an entry takes.

Name | Description | Default
---------|-------------|--------
request.audience.k8s.namespace.from.destination.spiffe-id.header.name | HTTP header carrying the destination workload's SPIFFE id, e.g. `spiffe://trust-domain/ns/namespace/sa/service-account`. When set, the namespace inside the SPIFFE id is compared against the `aud` entry's namespace segment. | n/a (unset; namespace is then not matched from this source)
request.audience.k8s.server.name.header.name | HTTP header carrying the destination workload's FQDN, in the form `service-name.namespace<cluster-suffix>`, optionally followed by `:port`. When set, both the service-name and namespace segments are compared. | n/a (unset; this header is then not read at all)
request.audience.k8s.server.name.cluster-suffix | Suffix that terminates the FQDN read from the header above, e.g. `.svc.cluster.local`. Only read when that header parameter is itself configured. Independent of `request.audience.k8s.dns.format.cluster.domain` below, which plays the same role for a DNS-form `aud` entry rather than for this header. | `.svc.cluster.local`
request.audience.k8s.path.header.name | HTTP header carrying the path to match against the `aud` entry's resource-path segment. | n/a (unset; resource path is then not matched)
request.audience.k8s.path.header.from.url | Whether the header above carries a full URL whose path component should be extracted, rather than already being the bare path to match. Only read when that header parameter is itself configured. | `false`
request.audience.k8s.source.spiffe-id.header.name | HTTP header carrying the calling (source) workload's SPIFFE id. Supplies the namespace for a namespace-less DNS-form `aud` entry, and is the identity compared against the actor subject when actor-subject binding is enabled. | n/a (unset; a namespace-less DNS-form entry can then never match, and actor-subject binding cannot be enabled)

###### DNS-form parameters ######

Name | Description | Default
---------|-------------|--------
request.audience.k8s.dns.format.enabled | Whether an `aud` entry written as a k8s service DNS name is accepted at all. See DNS form above for why a multi-cluster or multi-trust-domain deployment may want this `false`. When `false`, `request.audience.k8s.dns.format.cluster.domain` is unused. | `true`
request.audience.k8s.dns.format.cluster.domain | The cluster domain a DNS-form `aud` entry's host is checked against, e.g. the `cluster.local` in `https://reporting-svc.analytics.svc.cluster.local`. See DNS form above for the exact shortening and matching rule. Only read when DNS-form entries are enabled. Named apart from `cluster-domains` below specifically so the two are not mistaken for each other. | `cluster.local`

###### Custom-form parameters ######

Name | Description | Default
---------|-------------|--------
request.audience.k8s.cluster-domains | Comma-separated allow-list of cluster domains (host, with an optional `:port`, defaulting to `443`) a custom-form `aud` entry's authority may match. Always enforced for a custom-form entry; there is no way to disable this check. Does not apply to a DNS-form entry; see `dns.format.cluster.domain` above. | `service.local` (a placeholder that fails closed until set to the deployment's own cluster domain(s))
request.audience.k8s.audience.path.prefix | Optional path prefix searched for in a custom-form `aud` entry's path before namespace and service-name are parsed out of it; see Optional path prefix above. Never applies to a DNS-form entry. | n/a (unset; a custom-form entry's path must then begin with namespace and service-name straight after the authority)

###### Cross-cutting behavior parameters ######

Name | Description | Default
---------|-------------|--------
request.audience.k8s.require-all-audiences-match | Whether at least one `aud` entry matching the destination is sufficient (`false`, the ordinary "am I an intended audience" semantic of RFC 7519 section 4.1.3), or whether the claim must be non-empty and every entry must match (`true`). See the note below on combining this with the minting-side flags. | `false`
request.audience.k8s.validate.audiences.without.act.claim | Whether a token with no `act` claim is validated against the request destination (`true`) or against the fixed `knox.token.audiences` list (`false`). See Non-delegated tokens below. Has no effect on a token that does carry an `act` claim. | `true`
request.audience.k8s.enforce.act.sub.service-account.matches.source.spiffeid | Whether the most recent actor in the token's `act` chain must match the source workload's SPIFFE id. See Actor-subject binding below. Requires `source.spiffe-id.header.name` to be configured; setting this `true` without it fails topology startup. | `false`
request.audience.k8s.enforce.act.sub.is.service.account | Whether an actor subject that cannot be read as a k8s service-account subject fails validation (`true`) or passes unchecked (`false`). Only consulted when the parameter above is `true`. | `false`

`request.audience.k8s.require-all-audiences-match=true` is independent of, and does not by itself prevent, a delegation token being minted with more than one audience in the first place; it only changes how this validator reacts to one once presented. `JWTFederationFilter` separately exposes `delegation.enforce.requested.audience.required` and `delegation.enforce.requested.audience.max.one`, which constrain what a delegation token may be minted with. A deployment that wants every delegation token to carry exactly one audience should set all three: the two minting-side flags stop a multi-audience token from being issued, and `require-all-audiences-match` independently rejects one at the destination even if it is minted anyway, for example by an older or misconfigured issuer.

##### Path matching #####

An `aud` entry with no resource path, or with a resource path of exactly `/`, matches ANY request path, for either form, including when `request.audience.k8s.path.header.name` is configured and the header is present on the request. This is deliberate, not an oversight: an entry with an empty path is a statement about which destination service a token may be presented to, not about which operations on that service it may invoke. An audience meant to be scoped to one endpoint must carry that endpoint's path.

##### Non-delegated tokens #####

`request.audience.k8s.validate.audiences.without.act.claim` controls how a token with no `act` claim, that is, a token that is not a delegation token, is handled. It defaults to `true`: such a token's `aud` claim is validated against the request's actual destination in exactly the same way as a delegation token's, and the request is rejected if the token carries no `aud` claim, or an `aud` that is not a valid destination for this request. Setting it to `false` sends a non-delegated token's `aud` claim to the fixed `knox.token.audiences` check (`AbstractJWTFilter.matchesConfiguredAudiences`) instead, the same as if this validator were not selected for it. This parameter has no effect on a token that does carry an `act` claim: that token is always destination-validated by this validator.

##### Actor-subject binding #####

Setting `request.audience.k8s.enforce.act.sub.service-account.matches.source.spiffeid` (default `false`) to `true` enables validating that the source SPIFFE id's service-account identity matches the most recent actor's service-account identity in the token's `act` chain, provided that actor's `sub` claim is of the form `system:serviceaccount:<namespace>:<service-account-name>`. This enforces that a delegated token can only be used by the service account that exchanged for it. Trust domain and issuer are not compared, since the two subject forms carry no comparable value for either, so this check is meaningful only within a single trust domain. If the actor's `sub` claim is not of the expected form, the check is skipped (the request passes), unless `request.audience.k8s.enforce.act.sub.is.service.account` is also `true`, in which case it fails. See below for that parameter.

Enabling this parameter requires `request.audience.k8s.source.spiffe-id.header.name` to also be configured; leaving it unset while this is `true` fails the topology at startup, rather than leaving a check the deployment asked for silently inactive.

`request.audience.k8s.enforce.act.sub.is.service.account` (default `false`) is only consulted when the parameter above is `true`. It controls what happens when the actor subject cannot be read as a Kubernetes service-account subject at all: `false` (the default) passes such an actor without comparing it, since a legitimate actor subject may take another form; `true` fails validation instead.

A token whose `act` claim is present but is not a JSON object, so no actor subject can be read from it at all, fails validation when `enforce.act.sub.service-account.matches.source.spiffeid` is `true`, the same as any other actor subject this check rejects. A token with no `act` claim carries no actor at all, so this check does not apply to it regardless of either parameter's value.

##### Prerequisites #####

Each of the four header parameters below (destination SPIFFE-id, server-name, path, and source SPIFFE-id) names a header this validator trusts at face value, with no independent verification. Every one of them **must** be populated only by a component upstream of Knox that is trusted to set or overwrite it correctly with a value it independently determined, never a header an untrusted caller could set or influence. If that guarantee does not hold for a configured header, an untrusted caller controls the corresponding side of the comparison, and the destination binding this validator provides is defeated. Establishing that guarantee is the responsibility of the deployment's network/ingress layer, not something this validator can check.

When a header parameter is configured, that header must be present and parseable in the shape this validator requires for it on every request, or validation fails, whether or not the `aud` entries actually presented on a given request would have needed that value. This holds for all four header parameters.

If the path this validator checks can be changed by the surrounding infrastructure after this validator runs (for example, a routing rule that rewrites the request path), configure the path header to carry the path as it was *before* any such rewrite: the audience being validated describes the resource the caller requested, not the path an internal rewrite happens to route it to.

##### Example topology #####

This example is the combination that validates the destination namespace and path for every token (including non-delegated ones), binds a delegation token's actor to the workload presenting it, and deliberately does not validate service-name or accept the custom `aud` form. Every parameter this validator reads is shown, each marked `(default)` or `(non-default)`, so the full configuration surface, and what it does and does not bind, is visible in one place. Header names are examples; the real ones depend on what the service mesh sets.

    <provider>
        <role>federation</role>
        <name>JWTProvider</name>
        <enabled>true</enabled>

        <!-- Selects this validator. No default: without it, no destination checking happens. -->
        <param>
            <name>request.audience.validator</name>
            <value>request.audience.k8s.destination.validation</value>
        </param>

        <!-- (default) The k8s cluster domain, for aud entries written as a service DNS name.
             Set explicitly so the cluster domain is visible in the topology, not implied. -->
        <param>
            <name>request.audience.k8s.dns.format.cluster.domain</name>
            <value>cluster.local</value>
        </param>

        <!-- (non-default: no default exists) Destination workload SPIFFE id.
             Must be set by a trusted upstream component; see Prerequisites.
             This is what makes the namespace check active. -->
        <param>
            <name>request.audience.k8s.namespace.from.destination.spiffe-id.header.name</name>
            <value>x-destination-spiffe-id</value>
        </param>

        <!-- DELIBERATELY NOT SET. Setting this would turn on service-name validation and add a
             second source for the destination namespace. It is omitted because the service name
             is not available in this deployment. Shown here so its absence is a visible,
             intentional choice rather than an oversight:

             request.audience.k8s.server.name.header.name
        -->

        <!-- (default) Suffix terminating the FQDN in the server-name header. INERT in this
             configuration, since that header is not set. Listed for completeness. -->
        <param>
            <name>request.audience.k8s.server.name.cluster-suffix</name>
            <value>.svc.cluster.local</value>
        </param>

        <!-- (non-default: no default exists) Routed request path.
             Must be set by a trusted upstream component; see Prerequisites.
             This is what makes the path check active, for aud entries that carry a path. -->
        <param>
            <name>request.audience.k8s.path.header.name</name>
            <value>x-destination-path</value>
        </param>

        <!-- (default) The path header carries a bare path, not a full URL. -->
        <param>
            <name>request.audience.k8s.path.header.from.url</name>
            <value>false</value>
        </param>

        <!-- (non-default: no default exists) Source workload SPIFFE id.
             Must be set by a trusted upstream component; see Prerequisites.
             Supplies the namespace for an aud entry whose host, in DNS form, omits one,
             and is the identity the actor subject is compared against below. -->
        <param>
            <name>request.audience.k8s.source.spiffe-id.header.name</name>
            <value>x-source-spiffe-id</value>
        </param>

        <!-- (default) Validate the aud claim of non-delegated tokens, those with no act claim,
             against the request destination. Set explicitly because this is the parameter that
             extends destination checking beyond delegated tokens. -->
        <param>
            <name>request.audience.k8s.validate.audiences.without.act.claim</name>
            <value>true</value>
        </param>

        <!-- (non-default: the default is false) Bind the most recent actor in the act chain to
             the source workload's service account. Requires the source SPIFFE id header above;
             setting this true without it fails startup. -->
        <param>
            <name>request.audience.k8s.enforce.act.sub.service-account.matches.source.spiffeid</name>
            <value>true</value>
        </param>

        <!-- (default) Do NOT require every actor subject to be a k8s service account. An actor
             subject that is not in system:serviceaccount:<namespace>:<name> form is passed
             without being compared, rather than failed. Set explicitly because leaving the
             default implicit here would hide a real policy choice: only service-account actors
             are checked, others are not. -->
        <param>
            <name>request.audience.k8s.enforce.act.sub.is.service.account</name>
            <value>false</value>
        </param>

        <!-- (default) Allow-list of cluster domains for aud entries in the custom
             https://cluster-domain/namespace/service-name/path form. Left at the fail-closed
             placeholder: this deployment presents service DNS audiences, which do not consult
             this list, so any custom-form audience is rejected. That is the intended outcome
             here, not an omission. -->
        <param>
            <name>request.audience.k8s.cluster-domains</name>
            <value>service.local</value>
        </param>

        <!-- (non-default) Every aud claim must match the destination. This is not the ordinary
              RFC 7519 semantic where one matching aud entry is sufficient. Use this to enforce
              that every token be exchanged for only one use.-->
        <param>
            <name>request.audience.k8s.require-all-audiences-match</name>
            <value>true</value>
        </param>

        <!-- DELIBERATELY NOT SET. A routing prefix searched for in a custom-form aud path before
             namespace and service-name are parsed. Not used here, and it never applies to a
             service DNS audience in any case. Shown so its absence is intentional:

             request.audience.k8s.audience.path.prefix
        -->

    </provider>

What this configuration enforces, derived from it item by item:

aud entry form accepted | BOTH. Each entry is tried as a k8s service DNS name first and as the custom form second, and is accepted if either matches.
---------|-----------
cluster domain, DNS form | ENFORCED, via `dns.format.cluster.domain`. Labels after `svc` must be a label-boundary prefix of `cluster.local`. Shortened hosts are accepted; a trailing dot is not.
cluster domain, custom form | ENFORCED, via `cluster-domains`, left at the fail-closed `service.local`.
port | NOT enforced for a service DNS audience; parsed and ignored. Part of the authority match for a custom-form entry.
namespace | ENFORCED, from the destination SPIFFE id header. For a service DNS audience whose host omits the namespace, the namespace comes from the SOURCE SPIFFE id header and must equal this destination namespace.
second namespace source | NOT present, because the server-name header is not set. With both configured the two destination namespaces would have to agree.
service name | NOT enforced. The server-name header is not set, and it is the only source for it. A token minted to call service A in namespace N is therefore accepted when presented to service B in namespace N. Closing that gap needs the cluster-IP-to-service-name lookup, which this configuration does not have.
resource path | ENFORCED when the aud entry carries a path: it must match the routed path from the path header. NOT enforced when the aud entry carries no path, or only `/`, in which case the entry matches any request path and the path header is not consulted for it. An audience meant to be scoped to one endpoint must carry that endpoint's path.
actor subject | ENFORCED for service-account actors. The most recent actor in the act chain must name the same namespace and service-account name as the source workload's SPIFFE id. Trust domain and issuer are not compared. Valid only within a single trust domain.
non-service-account actor | NOT failed. Such an actor subject is passed without comparison, because `is.service.account` is `false`. Only service-account actors are checked.
token with no act claim | VALIDATED, the same as a delegated token, because `validate.audiences.without.act.claim` is `true`. Such a token is rejected if it carries no aud claim, or an aud that is not a valid destination for this request.
multiple aud entries | All audiences must match the destination, one matching entry is NOT sufficient (`require-all-audiences-match` is `true`).
custom-form routing prefix | Not configured, so a custom-form aud path must begin with namespace and service-name immediately after the authority.

##### Example request #####

Given the topology above, a delegation token minted for workload `frontend/analytics-ui` (actor subject `system:serviceaccount:frontend:analytics-ui`) to call `analytics/reporting-svc` at `/v1/reports`, with an `aud` claim containing `https://reporting-svc.analytics.svc.cluster.local/v1/reports`, presented alongside headers set by a trusted upstream component:

    curl -k -i \
        --header "Authorization: Bearer <delegation-token>" \
        --header "x-destination-spiffe-id: spiffe://example.org/ns/analytics/sa/reporting-svc" \
        --header "x-destination-path: /v1/reports" \
        --header "x-source-spiffe-id: spiffe://example.org/ns/frontend/sa/analytics-ui" \
        -v https://knox.example.org:8443/gateway/sandbox/webhdfs/v1/tmp?op=LISTSTATUS

The request is accepted:

* the DNS-form `aud` entry's host (`reporting-svc.analytics.svc.cluster.local`) parses, with namespace `analytics`, and its `svc.cluster.local` tail is the full configured cluster domain;
* the namespace (`analytics`) taken from `x-destination-spiffe-id` matches the entry's namespace;
* the path (`/v1/reports`) taken from `x-destination-path` matches the entry's resource path;
* the token's most recent actor subject (`frontend`/`analytics-ui`) matches the namespace and service-account name in `x-source-spiffe-id`.

Presenting the same token with `x-destination-spiffe-id` naming a different namespace, `x-destination-path` naming a different path, or `x-source-spiffe-id` naming a workload other than the one the token's actor names, is rejected.

If the `aud` entry instead carried no path, or exactly `/` (e.g. `https://reporting-svc.analytics.svc.cluster.local`), the same request would still be accepted, and so would an otherwise identical request against `/v1/other` or any other path on the same destination: `x-destination-path` would not be consulted for this entry at all. Every other check above still applies unchanged; only the resource-path check is skipped. To scope an audience to one endpoint, keep its path, as in the example above.

##### Failure modes #####

The validator rejects the request (a bad-request response, since audience validation runs as part of token validation) and logs a diagnostic message when:

* a token in scope (one carrying an `act` claim, or one with no `act` claim while `validate.audiences.without.act.claim` is `true`) has no `aud` claim at all;
* a configured header is missing, empty, or fails to parse in the shape this validator requires for it (an unparseable SPIFFE id, a server-name header that doesn't end in the configured cluster-suffix with exactly two labels before it, and so on);
* both namespace sources are configured and disagree;
* `audience.path.prefix` is configured and a custom-form entry's path never contains it as its own segment (or contiguous run of segments), so namespace and service-name cannot be located;
* `enforce.act.sub.service-account.matches.source.spiffeid` is `true` and the most recent actor in the token's `act` chain does not match the source SPIFFE id's namespace and service-account name, including when the `act` claim's value is not a JSON object at all, or when the actor subject cannot be read as a service-account subject and `enforce.act.sub.is.service.account` is `true`;
* no `aud` entry both parses as a supported URL shape (DNS form, custom form, or either depending on `dns.format.enabled`) and matches every segment this validator is configured to check (or, with `require-all-audiences-match=true`, any entry fails to).

A token with no `act` claim is validated against the fixed `knox.token.audiences` list instead of all of the above only when `validate.audiences.without.act.claim` is set to `false`.
