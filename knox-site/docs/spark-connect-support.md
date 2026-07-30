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

## Spark Connect Support ##

### Introduction ###

Spark Connect is the decoupled client/server protocol for Apache Spark (3.4+, and
the default architecture in Spark 4). Clients — PySpark, the Scala client, Go,
Rust, or JDBC via the Spark Connect driver — talk to a Spark Connect server over
gRPC, by default on port 15002.

The OSS Spark Connect server has essentially no built-in authentication or
authorization; the project assumes a fronting proxy provides them. Knox can now
fill that role, adding:

- **Authentication at the edge** — Knox-issued JWTs (KnoxToken bearer tokens),
  validated before anything reaches Spark.
- **Identity assertion** — Spark Connect otherwise trusts a *client-asserted*
  `user_context.user_id`. Knox overwrites it with the authenticated principal.
- **Coarse authorization** — the usual topology ACLs decide who may use Spark
  Connect at all.
- **Auditing** — one record per RPC: principal, topology, method, session,
  outcome and duration.

Because gRPC requires HTTP/2 with ALPN, and because `grpc-status` and Spark's
structured error details travel in HTTP trailers, this cannot run on Knox's
existing Jetty connectors. Spark Connect is served by a **dedicated listener on
its own port**, started and stopped with the gateway.

### Configuration ###

Spark Connect support is disabled by default. Enable it in
`<KNOX-HOME>/conf/gateway-site.xml`:

      <property>
          <name>gateway.sparkconnect.enabled</name>
          <value>true</value>
          <description>Enable the Spark Connect (gRPC) listener.</description>
      </property>
      <property>
          <name>gateway.sparkconnect.default.topology</name>
          <value>analytics</value>
          <description>Topology used when a client does not select one.</description>
      </property>

The listener presents the gateway's own TLS identity — the same keystore and
alias Jetty uses — whenever `ssl.enabled` is true, so there is no second
certificate to manage.

#### All properties ####

| Property | Default | Meaning |
|---|---|---|
| `gateway.sparkconnect.enabled` | `false` | Master switch; the listener is not started when false. |
| `gateway.sparkconnect.port` | `15002` | Port for the gRPC listener. |
| `gateway.sparkconnect.default.topology` | *(none)* | Topology used when the client sends no `knox-topology`. |
| `gateway.sparkconnect.max.message.size` | `134217728` | Maximum inbound message size in bytes, both legs. Matches Spark's 128 MB default. |
| `gateway.sparkconnect.max.concurrent.calls.per.connection` | `1000` | Maximum concurrent gRPC streams per client connection. |
| `gateway.sparkconnect.permit.keepalive.time` | `10000` | Minimum tolerated interval between client keepalive pings, in ms. |
| `gateway.sparkconnect.permit.keepalive.without.calls` | `true` | Whether clients may ping an idle channel. Spark Connect clients do. |
| `gateway.sparkconnect.channel.idle.timeout` | `1800000` | Idle time before an unused backend channel is shut down, in ms. |
| `gateway.sparkconnect.drain.timeout` | `30000` | How long in-flight RPCs get to finish at shutdown, in ms. |
| `gateway.sparkconnect.backend.token.alias` | *(none)* | Alias holding the backend's pre-shared token (see below). |
| `gateway.sparkconnect.add.artifacts.mode` | `ALLOW` | `ALLOW`, `DENY`, or `ALLOW_LISTED_USERS` for the `AddArtifacts` RPC. |
| `gateway.sparkconnect.add.artifacts.allowed.users` | *(none)* | Comma-separated users permitted when the mode is `ALLOW_LISTED_USERS`. |
| `gateway.sparkconnect.reserved.config.prefix` | `knox.` | Session-configuration key prefix clients may not `Set` or `Unset`. |

### Topology configuration ###

Declare the backend like any other service. The registry treats the URL as an
opaque string, so `grpc://` and `grpcs://` need no special handling:

      <service>
          <role>SPARKCONNECT</role>
          <url>grpc://spark-connect-host:15002</url>
      </service>

Use `grpcs://` for a TLS backend; Knox verifies it against the HTTP client
truststore, falling back to the gateway keystore.

Authorization uses the ordinary `AclsAuthz` provider syntax, keyed on the
`SPARKCONNECT` role:

      <provider>
          <role>authorization</role>
          <name>AclsAuthz</name>
          <enabled>true</enabled>
          <param>
              <name>SPARKCONNECT.acl</name>
              <value>*;analysts;*</value>
          </param>
      </provider>

Group membership comes from the `knox.groups` claim in the token, so configure
`knoxtoken` to include groups if you intend to write group ACLs.

### Connecting ###

Clients need no plugins or code changes. First acquire a token — over HTTPS,
authenticating however that topology is configured:

      curl --negotiate -u : https://knox:8443/gateway/tokens/knoxtoken/api/v1/token

Then put it in the connection string:

      sc://knox-host:15002/;use_ssl=true;token=<knox-jwt>;knox-topology=analytics

Two details make this work. The `token=` parameter is sent as a standard
`Authorization: Bearer` header and forces TLS on. Any parameter the client does
not recognise — `knox-topology` here — is sent as gRPC metadata on every request,
which is how a topology gets selected despite gRPC forbidding a path component in
the connection URL. If you set `gateway.sparkconnect.default.topology`, the
`knox-topology` parameter can be omitted.

### Kerberos environments ###

Neither gRPC nor the vanilla Spark Connect clients support SPNEGO, and gRPC has no
challenge-response step for it to hook into. Kerberos therefore authenticates
*token acquisition* rather than each RPC: a `kinit`'d user or a keytab'd service
fetches a token from a `knoxtoken` topology using HadoopAuth/SPNEGO, and the JWT
carries the data path.

This is the same trade Kerberized Hadoop already makes — nobody SPNEGOs every
HDFS block read. It is also better operationally for long-running jobs: an
administrator can revoke one token without touching the principal.

Tokens are validated when an RPC starts, not continuously. A long-running
`ExecutePlan` is not killed when its token expires; the next RPC fails with
`UNAUTHENTICATED`.

### Security considerations ###

**Knox's authorization here is coarse by design.** It answers only "may this user
use Spark Connect in this topology". Database, table, column and row-level policy
must be enforced inside the Spark Connect server — for example by a Ranger-backed
plan-level plugin keyed off the identity Knox asserts.

**Asserting `user_id` is not storage-level enforcement.** On the server,
`user_id` keys the session cache — so two users can never share a session — and
appears in logs and events. It is not propagated into Spark's
`CurrentUserContext`, so `current_user()` in SQL reports the Spark application's
own user unless a server-side component bridges it. A shared Spark Connect server
is one application running as one principal, and its storage credentials are that
principal's.

**User-supplied code bypasses plan-level policy.** Uploaded jars and inline
Python/Scala UDFs run inside that JVM with that principal's credentials, so they
can read data directly. `gateway.sparkconnect.add.artifacts.mode` shrinks the
attack surface but does not close it, because inline UDFs reach the same
capability. This is a property of plan-level enforcement generally, not something
the gateway introduces. Deployments needing a hard boundary want per-user or
per-tenant backend instances.

**Restrict the backend.** Knox in front of an openly reachable Spark Connect port
secures nothing. Firewall the backend so only Knox can reach it, and set Spark 4's
pre-shared token (`spark.connect.authenticate.token`), storing it as a Knox alias
and naming it in `gateway.sparkconnect.backend.token.alias`. Knox then presents it
on the backend leg — and, because it strips the client's own credential there, a
client cannot bypass the gateway even with network reachability.

### Is this a generic gRPC gateway? ###

No — and deliberately so. Knox proxies exactly one gRPC service,
`spark.connect.SparkConnectService`. There is no configuration property that
points this listener at an arbitrary gRPC backend, and a call to any other proto
service is answered `UNIMPLEMENTED`.

It is worth being open about what sits behind that, because anyone reading the
source will notice it: most of this feature is not Spark-specific. The listener,
TLS from the gateway identity, bearer authentication, the coarse ACL check,
topology routing, backend channel caching, auditing, graceful drain and the relay
itself are all protocol-agnostic — the relay in particular treats messages as
opaque and collapses all four RPC shapes into one code path. Only identity
assertion and the per-RPC gating switches need to understand Spark Connect's
messages.

The implementation keeps that split explicit: the gateway listener is an abstract
class whose protocol-aware parts are abstract methods, and the Spark Connect
listener is its only concrete subclass. That is a bet that someone will
eventually want to front a second gRPC service, and it costs little to leave the
seam in place rather than discover it later. It is **not** a commitment, and it
is not a supported extension point — the abstraction exists for the benefit of a
future change to Knox itself, not as an API to build against.

Promoting it to a real capability would take more than flipping a switch, which
is the main reason it has not been:

- **A service-to-role mapping.** gRPC paths are `/pkg.Service/Method`, so a
  topology would need to declare which proto services map to which backend roles,
  default-denying anything unmapped.
- **An honest security posture.** Byte-level proxying gives authentication,
  coarse authorization, TLS and method-level audit — but no message-body
  controls at all. It cannot assert identity, cannot protect a reserved config
  key, and cannot record a session id. That is a materially weaker offering than
  what Spark Connect gets here, and it competes much less favourably with simply
  putting Envoy or nginx in front of the service.
- **A community discussion**, its own configuration surface, and its own
  documentation.

One narrow piece of byte-level relay *is* active, and it is scoped accordingly:
an RPC that belongs to `spark.connect.SparkConnectService` but has no generated
handler in this build — a method added in a newer Spark line — is forwarded as
opaque bytes rather than rejected. Such a call is still authenticated,
authorized, routed and audited; it simply does not get identity assertion,
because that requires parsing the message. This exists so version skew degrades
gracefully, not as a general passthrough.

### Limitations ###

- One `SPARKCONNECT` URL per topology. Spark Connect sessions are server-side
  state keyed by `(user_id, session_id)` and `ReattachExecute` must reach the
  backend owning the operation, so round-robin over several backends would be
  wrong; session-affine routing across multiple backends is not yet implemented.
- The asserted principal is the token's subject. Identity-assertion provider
  mapping rules are not applied on this path.
- A gateway restart severs active streams. Clients recover through their own
  `ReattachExecute` retry logic, and shutdown drains for
  `gateway.sparkconnect.drain.timeout` first.
