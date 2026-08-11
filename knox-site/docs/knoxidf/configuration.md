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

# Configuration Reference

KnoxIDF is configured through two layers:

- **Topology `KNOXIDF` service parameters** — per-deployment behavior (token TTLs, consent,
  federation, user attributes). These live inside the `<service><role>KNOXIDF</role>…</service>`
  element of a topology file.
- **`gateway-site.xml` properties** — gateway-wide concerns shared with the rest of Knox (signing
  keys, persistence/database, trusted-issuer cache tuning).

This chapter lists every parameter, its default, and its effect.

## How KNOXIDF service parameters are read

### The `knoxidf.` → `knox.token.` prefix passthrough

KnoxIDF reuses Knox's existing server-managed token machinery. Any `KNOXIDF` service parameter
written with the **`knoxidf.` prefix** has that prefix stripped and the remainder passed through
to the underlying token configuration. So the topology parameter:

```xml
<param>
    <name>knoxidf.knox.token.ttl</name>
    <value>86400000</value>
</param>
```

is delivered to the token layer as `knox.token.ttl`. This lets you set any
[KnoxToken](../config_knox_token.md) parameter on the KnoxIDF service by prefixing it with
`knoxidf.`.

!!! note "Access-token TTL is managed by Knox"
    KnoxIDF issues **server-managed** tokens: the access-token lifetime is governed by the token
    layer (`knoxidf.knox.token.ttl`) and, on the token-exchange topology, by the `JWTProvider`'s
    `knox.token.exp.server-managed=true`. Clients cannot request an arbitrary lifetime.

## Core `KNOXIDF` service parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `knoxidf.knox.token.ttl` | (token-layer default) | Access-token lifetime in **milliseconds** (passthrough to `knox.token.ttl`). |
| `knoxidf.knox.token.limit.per.user` | (token-layer default) | Max concurrent server-managed tokens per user; `-1` = unlimited (passthrough to `knox.token.limit.per.user`). |
| `refresh.token.ttl` | `86400000` (24 h) | Refresh-token lifetime in milliseconds. Refresh tokens are issued only when the `offline_access` scope is granted, and are rotated on each use. |
| `knoxidf.auto.consent.enabled` | `false` | When `true`, the [consent screen](security.md#consent) is skipped. This is a **server-side** decision and is never read from a client request parameter. |
| `knoxidf.client.registration.anonymous.allowed` | `false` | When `true`, [dynamic client registration](security.md#dynamic-client-registration) accepts anonymous callers. Secure by default. |
| `token.exchange.topology.name` | (none) | Name of the token-exchange topology (fronted by `JWTProvider`) to which the `token_endpoint` and `userinfo_endpoint` are redirected in discovery. See the [two-topology model](getting_started.md#3-deploy-the-knoxidf-topologies). |
| `federated.op.names` | (none) | Comma-separated list of federated OP logical names to enable. See [Federated OP parameters](#federated-op-parameters). |

!!! warning "Secure-by-default flags"
    Both `knoxidf.auto.consent.enabled` and `knoxidf.client.registration.anonymous.allowed`
    default to **`false`**. The sample topologies set them to `true` only to keep experimentation
    frictionless — review them before any non-development deployment.

## Federated OP parameters

Each name listed in `federated.op.names` is configured with a block of
`federated.op.<name>.<suffix>` parameters. See [Federation](federation.md) for the full flow and a
complete example.

| Suffix | Required | Default | Description |
|--------|----------|---------|-------------|
| `enabled` | — | `false` | Activates this OP. Only enabled OPs are offered on the login page. |
| `issuer` | **Yes** | — | Expected `iss` of the OP's id_token. Validated exactly — must match the OP's issuer. |
| `jwks.endpoint` | **Yes** | — | OP JWKS URL used to verify the id_token signature. |
| `clientId` | Yes | — | Knox's client id at the OP; must appear in the id_token `aud`. |
| `clientSecret` | Conditional | — | Knox's client secret at the OP (plaintext). Prefer `clientSecret.alias`. |
| `clientSecret.alias` | Conditional | — | Alias name resolved via `AliasService`. **Takes precedence** over `clientSecret` and **fails closed** if unresolvable. See [Security → Secret handling](security.md#secret-handling). |
| `authorize.endpoint` | Yes | — | OP authorization endpoint Knox redirects the user to. |
| `token.endpoint` | Yes | — | OP token endpoint for the back-channel code exchange. |
| `userinfo.endpoint` | No | — | OP UserInfo endpoint. |
| `discovery.endpoint` | No | — | OP discovery document URL (alternative to listing endpoints individually). |
| `authorize.callback` | Yes | — | The Knox callback URL registered at the OP (`…/knoxidf/api/v1/authorize/callback`). |
| `signature.algorithm` | No | `RS256` | Expected id_token signing algorithm. |

## User parameters and claims

KnoxIDF can enrich issued tokens with additional claims — statically configured claims and, for
local users, attributes resolved from a **user-parameter provider**.

### Hard-coded claim mappings

| Parameter | Default | Description |
|-----------|---------|-------------|
| `knox.token.hardcoded.claim.mappings` | (none) | `;`-separated list of `key=value` pairs added as claims to every issued token. |

### LDAP user-parameter provider

If `user.params.provider.ldap.url` is set, KnoxIDF looks up the authenticated user in LDAP and adds
the resolved attributes to the token (and to the UserInfo response). If it is **absent**, an
`EmptyUserParamsProvider` is used and no LDAP lookup occurs.

| Parameter | Default | Description |
|-----------|---------|-------------|
| `user.params.provider.ldap.url` | (none) | LDAP(S) URL. Its presence selects the LDAP provider; absence selects the no-op provider. |
| `user.params.provider.ldap.baseDn` | `dc=hadoop,dc=apache,dc=org` | Search base DN. |
| `user.params.provider.ldap.userDnTemplate` | `uid=%s,ou=people,dc=hadoop,dc=apache,dc=org` | DN template; `%s` is replaced with the (escaped) username. |
| `user.params.provider.ldap.systemUser` | `uid=admin,ou=people,dc=hadoop,dc=apache,dc=org` | Bind DN for attribute lookups. |
| `user.params.provider.ldap.systemPasswordAlias` | — | **Required** alias for the system-user password. There is no plaintext fallback — if the alias is absent or unresolvable, initialization fails with an `IllegalStateException` (fail-closed). |

## Gateway-site properties

These are set in `$KNOX_HOME/conf/gateway-site.xml` and shared with the rest of Knox.

### Signing keys

| Property | Default | Description |
|----------|---------|-------------|
| `gateway.signing.key.alias` | `gateway-identity` | Alias of the primary key used to sign KnoxIDF-issued JWTs. |
| `gateway.signing.key.aliases.additional` | `none` | Comma-separated additional signing-key aliases to **also publish** on the JWKS endpoint. This is the mechanism behind zero-downtime [signing-key rotation](operations.md#signing-key-rotation). `none` means no additional keys. |
| `gateway.signing.keystore.name` | (gateway identity keystore) | Keystore holding the signing key(s). |
| `gateway.signing.keystore.type` | (gateway default) | Keystore type (e.g. `JKS`, `PKCS12`). |
| `gateway.signing.keystore.password.alias` | (gateway default) | Alias of the keystore password. |

### Persistence and database

KnoxIDF's [federated-identity store](operations.md#federated-identity-persistence) and the trusted-issuer
registry share Knox's database configuration. With no external database configured, KnoxIDF
self-provisions an **embedded Derby** database (the same physical store used by token state), so no
setup is required to get started.

| Property | Default | Description |
|----------|---------|-------------|
| `gateway.database.type` | `none` | Database backend: `none` / `derbydb` select the embedded self-provisioning Derby store; a real external type (`postgresql`, `mysql`, `oracle`, …) selects the JDBC-backed store. |
| `gateway.database.connection.url` | (none) | Full JDBC URL (overrides host/port/name if set). |
| `gateway.database.host` | (none) | Database host (when not using a full connection URL). |
| `gateway.database.port` | (none) | Database port. |
| `gateway.database.name` | `GATEWAY_DATABASE` | Database/schema name. |
| `gateway.database.ssl.enabled` | `false` | Enable TLS to the database. |
| `gateway.database.ssl.truststore.path` / `.alias` | (none) | Truststore path / password alias for the database TLS connection. |

Database credentials are supplied as aliases (`gateway_database_user`,
`gateway_database_password`) — see [Getting Started](getting_started.md#2-install-and-start-knox).

### Trusted OIDC issuer registry

| Property | Default | Description |
|----------|---------|-------------|
| `gateway.trustedoidcissuer.discovery.cache.ttl.secs` | `600` | How long a fetched issuer JWKS/discovery document is cached before re-fetch. |
| `gateway.trustedoidcissuer.discovery.connect.timeout.ms` | `3000` | Connect timeout when fetching an issuer's discovery/JWKS document. |
| `gateway.trustedoidcissuer.discovery.read.timeout.ms` | `10000` | Read timeout for the same fetch. |
| `gateway.trusted.oidc.issuer.max.issuers` | `10000` | Upper bound on the number of registered trusted issuers. Registration returns `409 issuer_limit_reached` once reached. |

### Provider-related properties (sample topologies)

These are not KnoxIDF parameters but appear in the sample federation topologies:

| Property | Description |
|----------|-------------|
| `jwt.expected.issuer` | Expected issuer enforced by a `JWTProvider` fronting the token-exchange topology. |
| `sso.unauthenticated.path.list` | On an `SSOCookieProvider` front topology, the `;`-separated list of KnoxIDF paths reachable before login (callback, JWKS, discovery, registration). See [Federation](federation.md#front-topology-for-federation). |

## See also

- [Getting Started](getting_started.md) — worked topology examples.
- [Security](security.md) — what the secure-by-default flags protect against.
- [Federation](federation.md) — configuring external OPs.
- [Operations](operations.md) — persistence backends and signing-key rotation.
