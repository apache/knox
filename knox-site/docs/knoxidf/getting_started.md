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

# Getting Started

This chapter walks through building Knox with KnoxIDF, deploying the topologies that expose
the OIDC endpoints, registering a client, and running a first Client Credentials flow.

!!! note "Branch"
    At the time of writing, KnoxIDF lives on the `knox_idf` development branch (kept in sync
    with `master`). Build from that branch until it is merged.

## 1. Build Knox

```bash
git clone https://github.com/apache/knox.git
cd knox
git checkout knox_idf

mvn -DskipTests -Dcheckstyle.skip=true -Dfindbugs.skip=true -Dpmd.skip=true \
    -Drat.skip -Dspotbugs.skip=true -Dforbiddenapis.skip=true \
    -Ppackage clean install
```

The build produces a Knox distribution archive under
`gateway-release/target/{version}/knox-{version}.zip`.

## 2. Install and start Knox

Unzip the distribution into a deployment directory (`$KNOX_HOME`), create the master secret
and the required aliases, then start the gateway. The signing-key hash alias
(`knox.token.hash.key`) backs server-managed (passcode) tokens; the database aliases back the
embedded/external persistence used by KnoxIDF and token state.

```bash
export KNOX_HOME=/path/to/knoxGateway

# Master secret (non-interactive)
$KNOX_HOME/bin/knoxcli.sh create-master --master gateway

# Signing / passcode HMAC key
$KNOX_HOME/bin/knoxcli.sh create-alias knox.token.hash.key --value <a-strong-random-secret>

# Database credential aliases (used by the embedded H2 store and any external DB)
$KNOX_HOME/bin/knoxcli.sh create-alias gateway_database_user --value knox
$KNOX_HOME/bin/knoxcli.sh create-alias gateway_database_password --value knox

$KNOX_HOME/bin/gateway.sh start
```

!!! tip "Local (non-TLS) testing"
    For local experimentation you can disable TLS by setting `ssl.enabled=false` in
    `$KNOX_HOME/conf/gateway-site.xml`. **Do not do this in production** — OAuth 2.0 / OIDC
    requires TLS for all token-bearing traffic.

By default, KnoxIDF's federated-identity persistence uses an **embedded H2** database that
Knox provisions automatically (the same physical DB used by token state). No external database
is required to get started. To point KnoxIDF at an external database (PostgreSQL, etc.), see
the [Configuration Reference](configuration.md) and [Operations](operations.md) chapters.

## 3. Deploy the KnoxIDF topologies

A typical KnoxIDF deployment uses **two topologies**:

- **A "front" topology** that exposes the OIDC endpoints and authenticates the end user (for
  example with LDAP Basic auth, or with an SSO cookie provider for federation). This is where
  `/authorize`, `/client/register`, `/jwks`, and discovery live.
- **A "token" topology** fronted by Knox's `JWTProvider`, referenced by the front topology's
  `token.exchange.topology.name`. The `/token` exchange is redirected here so that redeeming an
  authorization code is authenticated by a Knox-issued JWT.

Copy the topology files into `$KNOX_HOME/conf/topologies/`; Knox hot-deploys them within a few
seconds.

### Front topology (LDAP Basic auth) — `knoxidf-ldap.xml`

The `ShiroProvider` authenticates users against LDAP, and the OIDC endpoints that must be
reachable *before* login (discovery, registration, JWKS, and the federation callback) are
wired as `anon`:

```xml
<topology>
    <gateway>
      <provider>
         <role>authentication</role>
         <name>ShiroProvider</name>
         <enabled>true</enabled>
         <param>
            <name>main.ldapRealm</name>
            <value>org.apache.knox.gateway.shirorealm.KnoxLdapRealm</value>
         </param>
         <param>
            <name>main.ldapRealm.userDnTemplate</name>
            <value>uid={0},ou=people,dc=hadoop,dc=apache,dc=org</value>
         </param>
         <param>
            <name>main.ldapRealm.contextFactory.url</name>
            <value>ldaps://localhost:33390</value>
         </param>
         <param>
            <name>main.ldapRealm.contextFactory.authenticationMechanism</name>
            <value>simple</value>
         </param>
         <param>
            <name>urls./knoxidf/api/v1/.well-known/openid-configuration</name>
            <value>anon</value>
         </param>
         <param>
            <name>urls./knoxidf/api/v1/client/register</name>
            <value>anon</value>
         </param>
         <param>
            <name>urls./knoxidf/api/v1/authorize/callback</name>
            <value>anon</value>
         </param>
         <param>
            <name>urls./knoxidf/api/v1/jwks</name>
            <value>anon</value>
          </param>
         <param>
            <name>urls./**</name>
            <value>authcBasic</value>
         </param>
      </provider>
      <provider>
            <role>identity-assertion</role>
            <name>Default</name>
            <enabled>true</enabled>
      </provider>
    </gateway>

    <service>
        <role>KNOXIDF</role>
        <param>
            <name>knoxidf.knox.token.ttl</name>
            <value>60000</value>
        </param>
        <param>
            <name>knoxidf.knox.token.limit.per.user</name>
            <value>-1</value>
        </param>
        <param>
            <!-- Registration refuses anonymous callers unless this is explicitly true. -->
            <name>knoxidf.client.registration.anonymous.allowed</name>
            <value>true</value>
        </param>
        <param>
            <!-- Skipping consent is a server-side deployment decision, never a client param. -->
            <name>knoxidf.auto.consent.enabled</name>
            <value>true</value>
        </param>
        <param>
            <name>token.exchange.topology.name</name>
            <value>knoxidf-token</value>
        </param>
    </service>
</topology>
```

!!! warning "Anonymous client registration is opt-in"
    `knoxidf.client.registration.anonymous.allowed` defaults to **`false`** (secure by
    default). The sample above sets it to `true` only to keep the endpoint open for
    experimentation. See [Security](security.md#dynamic-client-registration).

### Token topology (`JWTProvider`) — `knoxidf-token.xml`

```xml
<topology>
    <gateway>
      <provider>
         <role>federation</role>
         <name>JWTProvider</name>
         <enabled>true</enabled>
         <param>
            <name>knox.token.exp.server-managed</name>
            <value>true</value>
         </param>
      </provider>
      <provider>
            <role>identity-assertion</role>
            <name>Default</name>
            <enabled>true</enabled>
      </provider>
    </gateway>

    <service>
        <role>KNOXIDF</role>
        <param>
           <name>knoxidf.knox.token.ttl</name>
           <value>86400000</value>
        </param>
        <param>
            <name>knoxidf.knox.token.limit.per.user</name>
            <value>-1</value>
        </param>
        <param>
            <name>knoxidf.auto.consent.enabled</name>
            <value>true</value>
        </param>
    </service>
    <service>
        <role>KNOXTOKEN</role>
        <param>
            <name>knox.token.ttl</name>
            <value>60000</value>
        </param>
        <param>
            <name>knox.token.limit.per.user</name>
            <value>-1</value>
        </param>
    </service>
</topology>
```

## 4. Discover the endpoints

Every subsequent step should read endpoint URLs from the discovery document rather than
hard-coding paths. Fetch it from the front topology:

```bash
curl -sk https://knox:8443/gateway/knoxidf-ldap/knoxidf/api/v1/.well-known/openid-configuration | jq .
```

The response includes `issuer`, `authorization_endpoint`, `token_endpoint`,
`userinfo_endpoint`, `jwks_uri`, `registration_endpoint`, and the supported grant types,
scopes, response types, and PKCE methods. See the [Endpoint Reference](endpoints.md) for the
full document.

## 5. Register a client

```bash
curl -sk -X POST \
  https://knox:8443/gateway/knoxidf-ldap/knoxidf/api/v1/client/register \
  -H 'Content-Type: application/json' \
  -d '{
        "client_name": "my-first-client",
        "redirect_uris": ["https://app.example.com/callback"],
        "grant_types": ["authorization_code", "refresh_token"]
      }' | jq .
```

The response contains a generated `client_id` and, for confidential clients, a
`client_secret`. Store the secret securely — it is required to redeem authorization codes on
the token endpoint (see [Security](security.md#token-endpoint-client-authentication)).

## 6. Run a Client Credentials flow

The Client Credentials grant issues a token to a confidential client with no interactive user
login. Post the client credentials to the token endpoint advertised by discovery:

```bash
curl -sk -X POST \
  https://knox:8443/gateway/knoxidf-ldap/knoxidf/api/v1/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials' \
  -d 'client_id=<client_id>' \
  -d 'client_secret=<client_secret>' \
  -d 'scope=openid' | jq .
```

You will receive a Knox-signed access token (and, when `openid` is requested, an ID token).
Verify it against the JWKS endpoint (`jwks_uri`).

## Next steps

- To drive an interactive login, use the **Authorization Code + PKCE** flow — see the
  [Endpoint Reference](endpoints.md#authorization-endpoint) and [Security](security.md#pkce).
- To broker login to an external OIDC Provider (Keycloak, Okta, Azure AD, Auth0), see
  **[Federation](federation.md)**.
- To tune tokens, persistence, and claims, see the **[Configuration Reference](configuration.md)**.
