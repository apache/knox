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

# Operations

This chapter covers running KnoxIDF in production: where identity state is persisted, how to
rotate signing keys without disrupting clients, how requests are audited, and how KnoxIDF behaves
behind a highly available Knox deployment.

## Federated identity persistence

KnoxIDF persists federated-identity data so that the same upstream user maps to a stable Knox
subject across logins and restarts, and so a filtered set of profile attributes can be reused. It
stores **only ID-token–derived data** — no access tokens, refresh tokens, or OP client secrets
(see [Security → What is (and isn't) stored at rest](security.md#what-is-and-isnt-stored-at-rest)).

### Backend selection

The persistence backend is always active — it does **not** depend on a `KNOXIDF` /
`KNOXIDF_ADMIN` topology being deployed, and it is chosen the same way as
[token state](../config_knox_token.md): a self-provisioning **embedded H2** database by default,
switching to a **JDBC-backed** external store only when you explicitly configure it. This mirrors
`TokenStateService` and applies to all three KnoxIDF stores (federated identity, the trusted-issuer
registry, and delegation policies).

| Backend | How it is selected | Notes |
|---------|--------------------|-------|
| Self-provisioning **embedded H2** (default) | Used when the service `impl` is left unset. | Uses the same physical embedded database as token state (under the gateway security directory). Zero setup. Optional at-rest encryption — see [Configuration → Embedded database encryption](configuration.md#embedded-database-encryption). |
| **JDBC-backed** store | Selected explicitly by setting the service `impl` to the JDBC implementation (below). `gateway.database.type` then names the external database (`postgresql`, `mysql`, `oracle`, …) and supplies the connection details. | Uses the operator-configured external database. Recommended for HA. |

Pin the implementation explicitly with the per-service `impl` property:

| Service | `impl` property | Default (H2) implementation | JDBC implementation |
|---------|-----------------|-----------------------------|---------------------|
| Federated identity | `gateway.service.knoxidffederatedidentity.impl` | `H2DBFederatedIdentityService` | `JdbcFederatedIdentityService` |
| Trusted OIDC issuers | `gateway.service.trustedoidcissuer.impl` | `H2DBTrustedOidcIssuerService` | `JdbcTrustedOidcIssuerService` |
| Delegation policy | `gateway.service.delegationpolicy.impl` | `H2DBDelegationPolicyService` | `JdbcDelegationPolicyService` |

The value is the fully qualified class name, e.g.
`org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.JdbcTrustedOidcIssuerService` (the
federated identity and delegation policy implementations live in the `...services.knoxidf.federation`
and `...services.knoxidf.delegation` packages).

Leaving a property unset selects the embedded H2 implementation. There is no longer a no-op
implementation that disables persistence; if the selected backend cannot be initialized, gateway
startup **fails** rather than silently serving an empty store.

!!! note "Configure all three consistently"
    These properties are read per service, so it is technically possible to point some at JDBC and
    leave others on H2. Don't — the embedded-H2 backend rewrites the shared `gateway.database.*`
    configuration as it starts, so a mixed setup can pull the JDBC-configured services onto H2 (or
    conflict) depending on startup order. Treat the backend as a gateway-wide choice: configure all
    three KnoxIDF services (and token state) the same way.

!!! note "`derbydb` is no longer supported"
    The embedded backend is now **H2 only**; the Apache Derby driver has been removed from the Knox
    distribution. If you have existing embedded-Derby data, migrate it offline before upgrading —
    see [Configuration → Migrating from Derby to H2](configuration.md#migrating-from-derby-to-h2).

!!! note "Use an external database for multi-instance deployments"
    The embedded H2 store is local to a single gateway process. For a clustered / HA
    deployment where more than one Knox instance must share federated-identity state, configure an
    external database (see [Configuration → Persistence](configuration.md#persistence-and-database)).

### What is stored

- **Identity mapping:** Knox subject (UUIDv5), provider name (upper-cased), external subject,
  external issuer.
- **Attributes:** the allow-listed profile claims (`preferred_username`, `email`, `email_verified`,
  `given_name`, `family_name`, `name`, `locale`).

The identity row and its attributes are written in a **single transaction**, and a unique
constraint on `(provider, external_issuer, external_subject)` makes concurrent first-logins of the
same user converge on one row.

## Delegation policies

When [token exchange with delegation](token_exchange.md) is enabled, KnoxIDF stores the
operator-authored [delegation policies](token_exchange.md#delegation-policies) that decide which
actor may act on behalf of which subjects. These policies live in the same persistence backend as
the other KnoxIDF stores — embedded H2 by default, or a JDBC store when
`gateway.service.delegationpolicy.impl` is pinned to `JdbcDelegationPolicyService` (see
[Backend selection](#backend-selection)). Treat the backend choice as gateway-wide: for any
multi-instance deployment, delegation policies must be in the shared external database so every
instance authorizes exchanges identically.

Policies are created and maintained through the
[Delegation Policies admin API](endpoints.md#delegation-policies-admin) on the `KNOXIDF_ADMIN`
topology; the minted delegated-token lifetime and the listing caps are tuned with the
[`gateway.delegation.service.*` and `knox.delegation.*` properties](configuration.md#delegation-policy-service).

## Signing-key rotation

KnoxIDF signs issued JWTs with the gateway signing key (`gateway.signing.key.alias`, default
`gateway-identity`) and publishes the corresponding public key(s) on the
[JWKS endpoint](endpoints.md#jwks-endpoint). Each published key is identified by a `kid` equal to
the **SHA-256 thumbprint** of its public key, so verifiers select the right key by `kid` rather
than assuming a single static key.

To rotate the signing key **without breaking tokens already in the wild**:

1. **Provision the new key** in the signing keystore under a new alias.
2. **Publish both keys.** Add the *old* alias to `gateway.signing.key.aliases.additional` in
   `gateway-site.xml` so the JWKS endpoint serves both the old and new public keys:

   ```xml
   <property>
       <name>gateway.signing.key.aliases.additional</name>
       <value>gateway-identity-previous</value>
   </property>
   ```

3. **Cut over signing** by pointing `gateway.signing.key.alias` at the new alias. New tokens are
   now signed with the new key; verifiers still find the old key (by its `kid`) on JWKS for
   tokens signed before the cutover.
4. **Retire the old key** once all tokens signed with it have expired: remove it from
   `gateway.signing.key.aliases.additional`.

Because clients resolve keys from JWKS by `kid`, no client reconfiguration is needed at any step.

!!! tip "Order matters"
    Publish the new key on JWKS *before* you start signing with it, and keep the old key published
    *until* the last token it signed has expired. Overlapping the two windows is what makes the
    rotation seamless.

## Auditing

KnoxIDF actions are recorded through Knox's standard audit framework, so KnoxIDF audit records
appear in the same audit log as the rest of the gateway (`$KNOX_HOME/logs/gateway-audit.log` by
default) and follow the gateway's configured audit layout. Security-relevant operations — token
issuance, client registration, consent decisions, federated login, and trusted-issuer
administration — are audited with the acting principal and outcome.

Audit output is configured through the gateway's Log4j2 configuration
(`$KNOX_HOME/conf/gateway-log4j2.xml`), the same as every other Knox audit stream; see
[Audit](../config_audit.md) for audit-appender and retention configuration.

### Token-exchange and delegation auditing

Every [token exchange](token_exchange.md) is audited, whether it is allowed or denied — this is the
**only** place the reason for a policy denial is recorded, since the endpoint deliberately returns a
generic error to the caller. Each record's message carries:

| Field | Meaning |
|-------|---------|
| `event_type` | `token_exchange_allowed` or `token_exchange_denied`. |
| `deny_reason` | Present only on a denial: the specific cause (e.g. `actor_not_registered`, `headless_not_allowed`, `subject_not_allowed`). |
| `actor_authority` | The acting party's authority — `USER` or `K8S_SA`. |
| `actor_id` | The acting party's identity (the `sub`, or `<issuer>:<namespace>:<name>` for a ServiceAccount). |
| `subject_token_iss` / `subject_token_sub` | Issuer and subject of the presented `subject_token`. |
| `requested_subject` | The impersonated identity for a headless exchange (blank otherwise). |
| `requested_resources` | The distinct requested `resource`/`audience` values. |
| `act_chain_depth` | Depth of the resulting `act` (actor) chain. |

Administrative changes to delegation policies are audited separately under the
delegation-lifecycle action, recording the `event_type` (`policy_registered`, `policy_updated`,
`policy_deleted`, `policy_read`, `policy_listed`), the affected policy, the acting operator
(`performed_by`), and the outcome.

## High availability

KnoxIDF adds no HA mechanism of its own — it inherits Knox's standard HA model. Run multiple Knox
instances behind a load balancer as you would for any other Knox service, with two
KnoxIDF-specific requirements:

- **Shared persistence.** All instances must point at the **same external database**
  (`gateway.database.*`) so a federated identity created on one instance is visible on the others.
  The embedded H2 default is per-process and is not suitable for multi-instance HA.
- **Consistent signing keys.** All instances must share the same signing keystore and the same
  `gateway.signing.key.alias` / `gateway.signing.key.aliases.additional` configuration, so a token
  issued by one instance verifies against the JWKS served by any instance.

### In-flight authorization flow state

An interactive Authorization Code flow spans several browser requests, and the state between them
must be visible to whichever instance serves the next request:

- **Consent:** `/authorize` keeps the pending request until the user accepts it on the consent page
  (`POST .../authorize/consentAccepted`).
- **Federated login:** `/authorize` keeps the pending request and the federated OPs offered for the
  login session; the KnoxSSO login page records the OP the user picked and the nonce sent to it;
  `/authorize/callback` reads all of it when the OP redirects back.

This state lives for minutes and is held by the `KnoxIDFAuthzFlowStateService`
(`gateway.service.knoxidfauthzflowstate.impl`), which has two implementations in the
`org.apache.knox.gateway.services.knoxidf.flowstate` package:

| Implementation | Notes |
|----------------|-------|
| `InMemoryAuthzFlowStateService` | State is local to the instance. Load balancer session affinity is **required** for the interactive flows: a request that reaches another instance fails with `invalid_request` (`Invalid state` / `Unknown or expired state`). |
| `JdbcAuthzFlowStateService` | State is kept in the `KNOXIDF_AUTHZ_FLOW_STATE` table of the external database, so any instance can continue any flow and no session affinity is needed. Each consent and callback state is consumed atomically, so it can be used only once across all instances. |

When the property is unset, the JDBC implementation is used if `gateway.database.type` names an
external database (`postgresql`, `mysql`, `mariadb`, `oracle`), and the in-memory one otherwise. The
embedded H2 database is never used for this state: it is local to one instance, so it would add
nothing over memory.

For HA, set the property to the JDBC implementation, together with the other KnoxIDF services and
token state (see [Backend selection](#backend-selection)):

```xml
<property>
    <name>gateway.service.knoxidfauthzflowstate.impl</name>
    <value>org.apache.knox.gateway.services.knoxidf.flowstate.JdbcAuthzFlowStateService</value>
</property>
```

Setting it explicitly matters because an embedded H2 service rewrites `gateway.database.type` to
`h2` as it starts, which would silently select the in-memory implementation. If the explicitly
configured JDBC implementation cannot connect, gateway startup fails. When the implementation was
selected automatically, the gateway logs an `ERROR` and falls back to in-memory state instead
(logger `org.apache.knox.gateway.knoxidf.authz.flow.state.service`).

Entries expire after `gateway.knoxidf.authz.flow.state.ttl.secs` (default `300` seconds), which bounds
how long a user may take on the consent and login pages; expired entries are ignored on read and
deleted every `gateway.knoxidf.authz.flow.state.cleanup.interval.secs` (default `60`). With the JDBC
implementation, every instance runs that cleanup; it is a single `DELETE` of expired rows, which
uses the `KNOXIDF_AUTHZ_FLOW_STATE_EXP` index on `expires_at`. The table and the index are created
at startup if missing. If a write fails, the flow ends with `server_error` rather than continuing
without its state.

Federated login state is written only for requests to the authorize endpoint
(`.../knoxidf/api/v1/authorize`). An unauthenticated request to any other path of the topology is
redirected to the login page without the federated OP options and stores nothing.

No OP client secrets are stored: the federated login state records only the OP names, client IDs
and endpoints, and the callback resolves the OP's full configuration from the topology.

The issued tokens themselves are verifiable on any instance, whichever implementation is used.

### Database load and connection pool sizing

With the JDBC implementation, the interactive flows add these statements to the external database:

| Flow | Statements |
|------|------------|
| Consent | `/authorize`: 1 `INSERT`. `consentAccepted` / `consentDenied`: 1 atomic consume. |
| Federated login | `/authorize`: 2 `INSERT`s. KnoxSSO login page, after the user picks an OP: 1 `SELECT` and 1 `INSERT`. `/authorize/callback`: 2 atomic consumes and 1 `DELETE`. |

An atomic consume is a single `DELETE ... RETURNING` on PostgreSQL and MariaDB, and a `SELECT`
followed by a `DELETE` on the same connection on MySQL and Oracle. Each statement takes one
connection from the pool and returns it right away; no connection is held while the user is on a
page.

All gateway services that use `gateway.database.*` share **one connection pool per instance**:
token state, federated identities, delegation policies, the trusted issuer registry and this flow
state. Token exchanges that are evaluated against delegation policies therefore compete for the same
connections as `/authorize` traffic. Size the pool for the combined peak:

| Property | Default | Notes |
|----------|---------|-------|
| `gateway.database.pool.max.size` | `10` | Connections per instance. A request that finds every connection busy waits for one. |
| `gateway.database.pool.connection.timeout.ms` | `30000` | How long a request waits for a connection before failing. |

The database must accept `gateway.database.pool.max.size` connections from **every** instance, plus
whatever other clients it has; for example, keep PostgreSQL's `max_connections` above
`instances x pool.max.size`. A short database round trip matters more than a large pool: at about
1 ms per statement, one connection serves roughly a thousand statements a second.

`/authorize` and the KnoxSSO login page are reachable without authentication, and every
`/authorize` request that shows the consent or login page writes a row that lives for
`gateway.knoxidf.authz.flow.state.ttl.secs`. Rate-limit these endpoints (see
[Rate limiting](#rate-limiting)) so a flood of them cannot fill the table or starve the pool for the
token endpoints.

### Trusted OIDC issuer registry consistency

Each instance keeps an in-memory copy of the trusted OIDC issuer registry, so token requests do not
query the database. A registration or removal is visible on the instance that served the admin
request immediately, and on the other instances within
`gateway.trusted.oidc.issuer.registry.cache.ttl.secs` (default `60` seconds). Lower it if a removed
issuer must stop being accepted cluster-wide sooner; each re-read is a single `SELECT` of the
issuer table. Delegation policies and federated identities are read from the database on every
request and need no such setting.

If the database becomes unreachable, an instance keeps serving its in-memory registry until it is
older than the TTL (logging a `WARN` on each failed re-read) and then **fails closed**: no issuer is
trusted, so tokens from external issuers are rejected. While failed closed, an instance retries the
database at the same pace as a healthy re-read, not on every request, so once the database is
reachable again it can take up to half the TTL to recover. Entering this state is logged once at
`ERROR` and leaving it at `INFO` (logger `org.apache.knox.gateway.knoxidf.trustedoidcissuer.service`).

Two related behaviors remain per-instance:

- The OIDC discovery cache (`gateway.trusted.oidc.issuer.discovery.cache.ttl.secs`) and the CA
  bundles loaded for issuers registered with a `jwksCa`. An instance discards an issuer's entries
  whenever it notices that the issuer was added, removed or re-registered, but the admin
  `refresh-jwks` operation only discards them on the instance that served the request. The other
  instances re-fetch the discovery document when their entry expires, and re-read a Secret-backed
  CA bundle after `gateway.trusted.oidc.issuer.ca.secret.max.age.secs` or a TLS certificate failure.
- The `max.issuers` limit is checked against the local registry copy, so instances registering
  issuers concurrently can briefly exceed it.

## Rate limiting

KnoxIDF does not implement rate limiting of its own, but it does not need to — Knox's
**`WebAppSec` provider** ships a rate-limiting filter you can attach to a KnoxIDF topology to
throttle request flooding, whether malicious or from a misconfigured client. This protects the
high-value token, authorization, and registration endpoints without any external infrastructure.

Add the provider to the KnoxIDF topology and enable rate limiting:

```xml
<provider>
    <role>webappsec</role>
    <name>WebAppSec</name>
    <enabled>true</enabled>
    <param>
        <name>rate.limiting.enabled</name>
        <value>true</value>
    </param>
    <param>
        <name>rate.limiting.maxRequestsPerSec</name>
        <value>25</value>
    </param>
    <param>
        <!-- -1 = reject over-limit requests; a non-negative value delays instead
             and requires gateway.servlet.async.supported=true in gateway-site.xml -->
        <name>rate.limiting.delayMs</name>
        <value>-1</value>
    </param>
</provider>
```

The filter tracks request rate per connection (or per session when
`rate.limiting.trackSessions=true`), delays or rejects requests over
`rate.limiting.maxRequestsPerSec`, and can exempt trusted callers via
`rate.limiting.ipWhitelist`. See the
[WebAppSec provider → Rate limiting](../config_webappsec_provider.md) documentation for the full
parameter set (`delayMs`, `maxWaitMs`, `throttledRequests`, `insertHeaders`, `ipWhitelist`, …).

!!! note "Async support for non-rejecting modes"
    A non-negative `rate.limiting.delayMs` (delay rather than reject) requires
    `gateway.servlet.async.supported=true` in `gateway-site.xml` (it is `false` by default).

You may still add rate limiting at the edge (load balancer / reverse proxy) as defense in depth.
The edge is the better place for the unauthenticated `/authorize` and KnoxSSO login endpoints: it
can limit per client IP across all instances, and a rejected request never reaches the gateway
(with the JDBC flow state, each accepted `/authorize` request writes a database row, see
[Database load and connection pool sizing](#database-load-and-connection-pool-sizing)).
Anonymous client registration in particular should either be left disabled (the default) or, when
enabled, fronted by the rate-limiting filter above.

## Operational checklist

- [ ] TLS enabled on every topology that exposes KnoxIDF endpoints.
- [ ] External database configured for any multi-instance / HA deployment.
- [ ] Multi-instance: `gateway.service.knoxidfauthzflowstate.impl` set to `JdbcAuthzFlowStateService`, or load balancer session affinity configured for the interactive authorize flows.
- [ ] Multi-instance: `gateway.database.pool.max.size` sized for token exchange plus `/authorize` traffic, and the database accepts that many connections from every instance.
- [ ] Signing keystore and `gateway.signing.key.alias*` identical across all instances.
- [ ] `knoxidf.client.registration.anonymous.allowed` reviewed (default `false`).
- [ ] `knoxidf.auto.consent.enabled` reviewed (default `false`).
- [ ] Federated OP client secrets stored as aliases, not plaintext.
- [ ] Trusted-issuer registry (`KNOXIDF_ADMIN`) exposed only on an administrator-restricted topology.
- [ ] Delegation-policy admin API (`KNOXIDF_ADMIN`) exposed only on an administrator-restricted topology; delegation switches (`delegation.server.enabled`, …) reviewed (default `false`).
- [ ] Rate limiting enabled (WebAppSec provider on the topology, and/or at the edge) for the token / authorize / registration endpoints.
- [ ] Federated-OP back-channel timeouts (`gateway.knoxidf.federated.op.connect.timeout.ms` / `.read.timeout.ms`) reviewed for your OP.
- [ ] Audit log retention configured.
