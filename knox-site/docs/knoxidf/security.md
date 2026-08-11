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

# Security

This chapter describes the security controls KnoxIDF enforces as an OAuth 2.0 / OIDC provider.
Understanding them is important when hardening a deployment and when reasoning about the trust
boundary between clients, Knox, and any external OIDC Providers.

!!! warning "Always run over TLS"
    OAuth 2.0 and OIDC assume a confidential channel. Every token-bearing endpoint must be
    served over HTTPS in production. The `ssl.enabled=false` option is for local development
    only.

## Token-endpoint client authentication

The token endpoint independently authenticates the client when redeeming an authorization code —
it does not rely on the upstream `JWTProvider` (which authenticates the *request* but does not
verify the OAuth `client_secret`). The check is chosen by what was stored at authorize time:

- **PKCE path** — if the authorization request included a `code_challenge`, the client must
  present a matching `code_verifier`. See [PKCE](#pkce).
- **Client-secret path** — if no `code_challenge` was stored, the client must present a valid
  `client_secret`.

The two paths are mutually exclusive, and there is no path where neither applies. The
`refresh_token` grant always requires a `client_secret` (there is no PKCE bypass for refresh).

Client-secret verification is **constant-time**. The secret on the wire encodes the token id and
a passcode; Knox recomputes an HMAC (keyed by the `knox.token.hash.key` alias, over the token id,
issue time, and user name as a per-token salt) and compares it to the stored value with a
constant-time comparison. The embedded token id must equal the request `client_id`, binding the
secret to the specific client.

## PKCE

Only the **S256** code-challenge method is accepted. `plain` (and an omitted method, which OAuth
would otherwise default to `plain`) is **rejected** — both at the authorization endpoint (which
refuses to store a non-S256 challenge) and at the token endpoint (which refuses to verify with
any other method). This is enforced as defense in depth at both ends of the flow. The S256
challenge is computed as `BASE64URL(SHA-256(ASCII(code_verifier)))` without padding, per
RFC 7636.

Public clients (no `client_secret`) must use PKCE; this is how a public client proves possession
of the authorization code at redemption time.

## Single-use authorization codes

Authorization codes are single-use. The code is **atomically consumed before any token is
issued**, so of any number of concurrent redemptions of the same code, exactly one succeeds and
the rest receive `invalid_grant`. This closes the check-then-issue replay window.

A code that fails *validation* (bad `redirect_uri`, wrong `client_id`, PKCE/secret failure) is
deliberately **not** consumed — this prevents a denial-of-service in which an attacker replays a
victim's code with bad parameters to burn it before the legitimate client redeems it.

## Redirect-URI validation

Open redirects are prevented both at registration and at authorization time.

**At registration** (`/client/register`):

- The **host** component may not contain a wildcard.
- **HTTPS is required.** Plain `http://` is accepted only for loopback hosts (`localhost`,
  `127.0.0.1`, `::1`), per RFC 8252 for native apps.
- A wildcard `*` is permitted only at the **end of the path**, never in the host, query, or
  fragment.

**At authorization** (`/authorize`):

- Non-wildcard URIs are matched by exact string equality.
- Wildcard URIs are matched by first comparing the **origin** (scheme + host + port) so that a
  registered `https://good.example*` cannot match `https://good.example.evil.com`. Only then is
  the path prefix compared, after both paths are `URI.normalize()`d — collapsing traversal
  segments (e.g. `/callback/../admin`) so a raw prefix match cannot be tricked into escaping the
  registered prefix.

## Consent

For the Authorization Code flow, KnoxIDF presents a [consent page](endpoints.md#consent-page)
where the user approves the scopes a client is requesting. Consent is **one-time per (user,
client, scopes)**: once granted, subsequent authorization requests for the same scopes proceed
without re-prompting.

Whether consent can be skipped is a **server-side deployment decision**, governed by the
topology parameter `knoxidf.auto.consent.enabled`. It is read from the topology configuration at
startup and is **never** read from the incoming HTTP request — a client cannot bypass the consent
screen by sending an `auto_consent=true` parameter.

Consent records are stored as metadata on the client's token record, under a fixed-width key
derived as `"consent_"` + the first 20 hex characters of `SHA-256(subject)` (28 characters
total). Hashing the subject keeps the key within the storage column width regardless of how long
the username or federated UUID subject is, and the read and write paths derive the key
identically so they always agree.

## Dynamic client registration

Dynamic client registration is a deliberately supported deployment mode, but it is **not open by
default**. The endpoint refuses anonymous callers unless the topology explicitly sets:

```xml
<param>
    <name>knoxidf.client.registration.anonymous.allowed</name>
    <value>true</value>
</param>
```

The default is **`false`** (secure by default). When open registration is enabled, the
token-endpoint client authentication described above is what still prevents a
registered-but-unauthenticated client from redeeming another client's authorization code.

## Federated id_token validation

When Knox brokers login to an external OIDC Provider, the OP's `id_token` is **fully validated
before any claim is trusted** — it is never decoded and trusted as-is. Validation fails closed at
each stage:

1. **Signature + `exp`/`nbf`** — verified against the OP's JWKS (fetched from the configured
   `jwks.endpoint`) using the configured signature algorithm (default `RS256`).
2. **Issuer** — must equal the statically configured `federated.op.<name>.issuer`, not a value
   read from the token itself.
3. **Audience** — must contain the configured `federated.op.<name>.clientId` (Knox's client id
   at the OP).
4. **Subject** — `sub` must be present and non-blank.
5. **Nonce** — the token's `nonce` must equal the nonce Knox generated for that login session,
   binding the token to the specific authorization request.

If the OP configuration is missing its `jwks.endpoint`, `issuer`, or `clientId`, validation is
refused outright — no OP token can be accepted.

See [Federation](federation.md) for the full broker flow.

## Trusted issuer registry

The set of external issuers Knox will accept `id_token`s from is administered through the
[Trusted OIDC Issuers admin API](endpoints.md#trusted-oidc-issuers-admin) (`KNOXIDF_ADMIN`
role), which should be exposed only on an administrator-restricted topology. Registered issuer
URLs must be HTTPS, and there is a configurable upper bound on the number of trusted issuers.

## Secret handling

- **Federated OP client secrets** can be resolved from Knox's `AliasService` rather than being
  written in plaintext in the topology. Set `federated.op.<name>.clientSecret.alias` to an alias
  name; it takes precedence over the plaintext `clientSecret` parameter. If an alias is
  configured but cannot be resolved, resolution **fails closed** — the request to the OP is not
  made, rather than silently falling back to a plaintext value.
- **Federated access tokens are never persisted.** Only the federated *identity* (ID-token–derived
  data) is stored; the OP's access token is discarded immediately after the token exchange.
  Persisting an OP bearer token in plaintext token metadata would be a secret-at-rest exposure.

## Subject derivation

For federated users, the Knox `sub` is a deterministic **UUIDv5** over a fixed namespace UUID
(`6ba7b811-9dad-11d1-80b4-00c04fd430c8`, the RFC 4122 "URL" namespace) with the name
`issuer + "|" + subject`. The same upstream user therefore always maps to the same Knox subject
across logins and gateway restarts.

!!! danger "Do not change the subject namespace"
    Because the `sub` is derived from a fixed namespace UUID, changing that namespace would
    rewrite the subject of **every** previously persisted federated user. The namespace is an
    immutable part of the deployment's identity contract.

## What is (and isn't) stored at rest

KnoxIDF persists **only ID-token–derived federated identity data** — the core identity mapping
(Knox subject, provider, external subject, external issuer) and a filtered set of profile
attributes (`preferred_username`, `email`, `email_verified`, `given_name`, `family_name`,
`name`, `locale`). It does **not** store access tokens, refresh tokens, or OP client secrets.
This keeps the persisted footprint to what is needed for traceability and attribute reuse.
