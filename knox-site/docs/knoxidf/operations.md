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

The persistence backend activates automatically whenever a topology with the `KNOXIDF` or
`KNOXIDF_ADMIN` role is deployed. Which backend is used follows the gateway's database
configuration:

| `gateway.database.type` | Backend | Notes |
|-------------------------|---------|-------|
| `none` (default) | Self-provisioning **embedded H2** | Uses the same physical embedded database as token state (under the gateway security directory). Zero setup. Optional at-rest encryption — see [Configuration → Embedded database encryption](configuration.md#embedded-database-encryption). |
| A real external type (`postgresql`, `mysql`, `oracle`, …) | **JDBC-backed** store | Uses the operator-configured external database. Recommended for HA. |

You can also pin the implementation explicitly with the service property
`gateway.service.KnoxIDFFederatedIdentityService.impl` (Empty / H2 / JDBC); an explicit value
always wins over auto-selection. Setting it to the empty (no-op) implementation disables
persistence.

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

Sticky sessions are recommended for the interactive Authorization Code / federation flow so that
the browser stays on the instance holding the in-flight authorize/consent state, though the issued
tokens themselves are verifiable on any instance.

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
Anonymous client registration in particular should either be left disabled (the default) or, when
enabled, fronted by the rate-limiting filter above.

## Operational checklist

- [ ] TLS enabled on every topology that exposes KnoxIDF endpoints.
- [ ] External database configured for any multi-instance / HA deployment.
- [ ] Signing keystore and `gateway.signing.key.alias*` identical across all instances.
- [ ] `knoxidf.client.registration.anonymous.allowed` reviewed (default `false`).
- [ ] `knoxidf.auto.consent.enabled` reviewed (default `false`).
- [ ] Federated OP client secrets stored as aliases, not plaintext.
- [ ] Trusted-issuer registry (`KNOXIDF_ADMIN`) exposed only on an administrator-restricted topology.
- [ ] Rate limiting enabled (WebAppSec provider on the topology, and/or at the edge) for the token / authorize / registration endpoints.
- [ ] Federated-OP back-channel timeouts (`gateway.knoxidf.federated.op.connect.timeout.ms` / `.read.timeout.ms`) reviewed for your OP.
- [ ] Audit log retention configured.
