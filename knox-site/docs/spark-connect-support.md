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

#### Why a separate port ####

Spark Connect does not go through Knox's servlet pipeline, and could not: gRPC
needs HTTP/2 negotiated over ALPN, which Knox's Jetty connectors do not offer;
the Servlet 3.1 API Knox targets has no way to read or write HTTP trailers, where
`grpc-status` and Spark's structured error details live; and the outbound
dispatch layer is built on a strict request/response HTTP/1.1 client, whereas
`ExecutePlan` and `ReattachExecute` are long-lived server streams and
`AddArtifacts` is a client stream.

Routing rules it out independently. A `sc://` connection string may not contain a
path — the Spark client forbids it, to stay compatible with the gRPC standard —
and gRPC fixes request paths at `/pkg.Service/Method`. Knox's usual
`/gateway/{topology}/{service}` routing therefore has nothing to match on.

So Spark Connect is served by a **dedicated listener on its own port**, started
and stopped with the gateway, alongside Jetty rather than inside it:

                             Knox JVM
            ┌─────────────────────────────────────────────┐
    sc:// ─▶│ :15002  gRPC listener (Netty)               │
    grpc/h2 │   ├─ TLS (gateway identity)                 │
            │   ├─ audit                                  │
            │   ├─ authentication (bearer JWT)            │      grpc/h2
            │   ├─ routing (knox-topology → registry)     │──▶ Spark Connect
            │   ├─ authorization (topology ACLs)          │      server :15002
            │   └─ relay (asserts user_context.user_id)   │
            ├─────────────────────────────────────────────┤
            │ :8443  Jetty (the existing servlet gateway) │
            └─────────────────────────────────────────────┘

This mirrors how Knox already handles WebSockets, which likewise bypass the
topology filter chains and do their own authentication — the difference being
that a WebSocket upgrade can share Jetty's HTTP/1.1 connector, and gRPC cannot.

### What is proxied ###

The whole `spark.connect.SparkConnectService` surface. Every RPC is relayed with
its status and trailers passed through verbatim, and every request has its
`user_context.user_id` replaced with the authenticated principal:

| Shape            | RPCs                                                                                                                                         |
|------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| Unary            | `AnalyzePlan`, `Config`, `ArtifactStatus`, `Interrupt`, `ReleaseExecute`, `ReleaseSession`, `FetchErrorDetails`, `CloneSession`, `GetStatus` |
| Server-streaming | `ExecutePlan`, `ReattachExecute`                                                                                                             |
| Client-streaming | `AddArtifacts`                                                                                                                               |

Flow control is honored in both directions, so a slow client cannot make the
gateway buffer an unbounded number of Arrow batches, and cancellation propagates
both ways rather than leaving orphaned executions on the backend.

Nothing in the message bodies is rewritten apart from the identity fields. There
are no URLs or hostnames inside these protobufs, so Knox's rewrite machinery has
no role here.

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

| Property                                                   | Default     | Meaning                                                                           |
|------------------------------------------------------------|-------------|-----------------------------------------------------------------------------------|
| `gateway.sparkconnect.enabled`                             | `false`     | Master switch; the listener is not started when false.                            |
| `gateway.sparkconnect.port`                                | `15002`     | Port for the gRPC listener.                                                       |
| `gateway.sparkconnect.default.topology`                    | *(none)*    | Topology used when the client sends no `knox-topology`.                           |
| `gateway.sparkconnect.max.message.size`                    | `134217728` | Maximum inbound message size in bytes, both legs. Matches Spark's 128 MB default. |
| `gateway.sparkconnect.max.concurrent.calls.per.connection` | `1000`      | Maximum concurrent gRPC streams per client connection.                            |
| `gateway.sparkconnect.permit.keepalive.time`               | `10000`     | Minimum tolerated interval between client keepalive pings, in ms.                 |
| `gateway.sparkconnect.permit.keepalive.without.calls`      | `true`      | Whether clients may ping an idle channel. Spark Connect clients do.               |
| `gateway.sparkconnect.channel.idle.timeout`                | `1800000`   | Idle time before an unused backend channel is shut down, in ms.                   |
| `gateway.sparkconnect.drain.timeout`                       | `30000`     | How long in-flight RPCs get to finish at shutdown, in ms.                         |
| `gateway.sparkconnect.backend.token.alias`                 | *(none)*    | Alias holding the backend's pre-shared token (see below).                         |
| `gateway.sparkconnect.add.artifacts.mode`                  | `ALLOW`     | `ALLOW`, `DENY`, or `ALLOW_LISTED_USERS` for the `AddArtifacts` RPC.              |
| `gateway.sparkconnect.add.artifacts.allowed.users`         | *(none)*    | Comma-separated users permitted when the mode is `ALLOW_LISTED_USERS`.            |
| `gateway.sparkconnect.reserved.config.prefix`              | `knox.`     | Session-configuration key prefix clients may not `Set` or `Unset`.                |

#### Keeping these out of `gateway-site.xml` ####

Knox has no `conf.d` directory, but it does load one optional extra file. The
gateway reads exactly three configuration files from `{GATEWAY_HOME}/conf`, in
this order, with later files overriding earlier ones:

      gateway-default.xml
      gateway-site.xml
      gateway-reloadable.xml

`gateway-reloadable.xml` is not shipped and does not have to exist, so the
`gateway.sparkconnect.*` properties can live there instead of being merged into
`gateway-site.xml`. It is a single shared file rather than a per-feature
directory, so anything else using it has to co-exist in the same file — but it
does keep this feature's settings out of the main one. See
[Reloadable Gateway Configuration](config.md) for the general mechanism.

Knox re-reads that file every `gateway.config.refresh.interval` milliseconds
(default 10 seconds). Some of the properties above then take effect immediately;
the rest cannot, because they are built into the bound server.

**Applied on the next RPC, no restart:**

- `gateway.sparkconnect.default.topology`
- `gateway.sparkconnect.add.artifacts.mode`
- `gateway.sparkconnect.add.artifacts.allowed.users`
- `gateway.sparkconnect.reserved.config.prefix`

These are the message-level and routing controls — the ones an operator is most
likely to want to change in response to something happening. Tightening artifact
gating, or reserving a different configuration prefix, applies to the very next
call without interrupting any session.

**Restart required:** everything else — `gateway.sparkconnect.enabled` itself,
the port, TLS, message and stream limits, keepalive settings, channel idle and
drain timeouts, and the backend token alias. Whether the listener runs at all is
decided once at startup, and the rest are fixed when the socket is bound.

Changing a restart-only property in a running gateway does not silently do
nothing. The refreshed configuration is compared against what the gateway started
with, and a warning names what could not be applied — for the transport settings,
which properties changed; for `gateway.sparkconnect.enabled`, that the listener
cannot be started or stopped without a restart. Switching it on when it was off
at startup is reported too, which is the case most likely to be mistaken for a
malfunction: without the warning, the only symptom is a port that never opens.

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

### Multiple Spark Connect clusters ###

One topology per cluster, all served by the single listener port. A topology
declares exactly one `SPARKCONNECT` backend, so a second cluster means a second
topology:

      conf/analytics.xml   ->  grpc://spark-analytics:15002
      conf/etl.xml         ->  grpc://spark-etl:15002

Clients pick one with the `knox-topology` connection parameter:

      sc://knox-host:15002/;use_ssl=true;token=<jwt>;knox-topology=analytics
      sc://knox-host:15002/;use_ssl=true;token=<jwt>;knox-topology=etl

Both connect to the *same* Knox port and are routed to different Spark clusters.
Topology selection is per-RPC, from call metadata, so concurrent sessions from
different users — or from one user — are multiplexed over that one port onto
distinct backends. Knox keeps one pooled gRPC channel per backend URL and shares
it across all calls routed there.

This is safe for Spark Connect's session model because the discriminator is
sticky by construction: the client sends the same `knox-topology` on every
request of the connection, so every call in a session lands on the backend that
owns it — which `ReattachExecute` requires. Nothing round-robins.

Each topology carries its own authentication provider, ACLs and audit scope, so
"separate cluster" and "separate policy boundary" stay aligned.

#### Authorizing which users may select which cluster ####

Topology selection is a client-supplied value, so it is authorized rather than
trusted. Authorization runs *after* routing and is evaluated against the topology
that was selected — naming a topology in a connection string is not the same as
being allowed to use it. Give each topology its own `SPARKCONNECT.acl`:

      <!-- conf/analytics.xml -->
      <param>
          <name>SPARKCONNECT.acl</name>
          <value>*;analysts;*</value>
      </param>

      <!-- conf/etl.xml -->
      <param>
          <name>SPARKCONNECT.acl</name>
          <value>*;engineers;*</value>
      </param>

An analyst connecting with `knox-topology=etl` is refused with
`PERMISSION_DENIED` before any backend connection is made. The full ACL syntax
applies per topology — named users, named groups, IP ranges, `AND`/`OR`
processing mode, and the `KNOX_ADMIN_USERS` / `KNOX_ADMIN_GROUPS` placeholders —
so selection can be gated by user name, by group, by source address, or by a
combination.

Two things to be deliberate about:

- **The default is permissive.** A topology that declares no `SPARKCONNECT.acl`,
  or whose `AclsAuthz` provider is disabled, is reachable by *any* authenticated
  user. This matches the servlet provider's behaviour, but it means restricting
  selection is something you switch on, not something you get for free. If a
  cluster should be reachable by a subset of your users, it needs an ACL.
- **Group ACLs need group claims.** Groups come from the token's `knox.groups`
  claim, so a `knoxtoken` deployment that does not embed groups will match no
  group ACL. Where groups are unavailable, gate on user names instead.

A user probing topologies they cannot use can tell an existing Spark Connect
topology (`PERMISSION_DENIED`) from one that does not exist or serves no
`SPARKCONNECT` service (`UNAVAILABLE`). They must already hold a valid token to
learn even that, but do not treat topology names as secrets.

What is *not* supported is several backends **within** one topology. Spark Connect
sessions are server-side state keyed by `(user_id, session_id)`, so spreading one
topology across backends needs session-affine routing rather than any form of
load balancing; that is not implemented (see Limitations).

#### Adding a cluster without a restart ####

Topologies are hot-reloaded. Knox watches the topologies directory and picks up
changes within about five seconds, so dropping in a new topology file, or editing
an existing one, takes effect on a running gateway:

- **A new topology, or a changed backend URL** — takes effect on the next RPC.
  The backend is resolved from the service registry per call, and redeployment
  rewrites the registry entry.
- **A changed `SPARKCONNECT.acl`** — takes effect on the next RPC after the
  redeployment. The listener caches parsed ACLs per topology and drops that cache
  when topologies are redeployed.
- **A deleted topology** — subsequent calls selecting it fail `UNAVAILABLE`.
  Calls already in flight are not interrupted.

Only `gateway-site.xml` properties — the port, message limits, `AddArtifacts`
mode and so on — need a gateway restart, since the listener binds its socket and
captures those settings at startup.

### Connecting ###

Clients need no plugins or code changes. First acquire a token — over HTTPS,
authenticating however that topology is configured:

      curl --negotiate -u : https://knox:8443/gateway/tokens/knoxtoken/api/v1/token

Then put it in the connection string:

      sc://knox-host:15002/;use_ssl=true;token=<knox-jwt>;knox-topology=analytics

Two details make this work. The `token=` parameter is sent as a standard
`Authorization: Bearer` header and forces TLS on. Any parameter the client does
not recognize — `knox-topology` here — is sent as gRPC metadata on every request,
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

### Making the asserted identity usable inside Spark ###

The deployment this was built for is a single always-on Spark Connect server
behind the firewall, running as a privileged principal, with fine-grained
authorization enforced *inside* the server — typically a plan-level plugin
evaluating Ranger policies against the identity Knox asserts. Getting that
identity from the gateway into the engine takes one more step, and it is worth
being explicit about it because the gap is easy to miss.

**The carrier of record is `user_context.user_id`.** Knox rewrites it on every
message, it keys the server-side session cache — so two users cannot share a
session by construction — and it lands in Knox's audit records. Anything else
should be *derived* from it, never asserted independently by the client.

**But OSS Spark does not surface it to SQL.** `user_id` is used for the session
key and for logging; it is not propagated into `CurrentUserContext`, so
`current_user()` returns the Spark application's own user. A server-side
component has to bridge it.

The robust bridge is a gRPC `ServerInterceptor` deployed with the Spark
application and registered through `spark.connect.grpc.interceptor.classes`.
That is a static configuration, so clients cannot alter it. The interceptor reads
`user_context.user_id` on each request and publishes it — by setting
`CurrentUserContext.CURRENT_USER`, which makes `current_user()` itself correct,
and/or by writing a reserved session configuration key. Whatever consumes the
identity downstream (a Ranger plugin, say) and the bridge should agree on one
mechanism rather than each inventing its own.

One thing to verify when building such a bridge: `CurrentUserContext` is an
`InheritableThreadLocal`, and Spark Connect runs plans on dedicated execution
threads. Confirm that a value set in the interceptor is actually visible at
analysis and optimization time; if it is not, set it from a session hook on the
execution path instead.

A weaker alternative needing no server-side code is to have the client's session
prime a reserved configuration key. Knox does **not** do this for you — it does
not inject `Config` calls — and the approach is less trustworthy than the
interceptor for the reason below.

**Reserved keys are protected, but only on the structured path.** Knox denies
client `Set` and `Unset` on any session configuration key beginning with
`gateway.sparkconnect.reserved.config.prefix` (default `knox.`). Those are named
fields in the `Config` RPC, so the check is exact and cheap. What Knox does *not*
screen is `SET knox.whatever=...` issued as SQL inside `ExecutePlan`, which would
require inspecting plan text and would be best-effort at best. This is the main
argument for the interceptor bridge: a value recomputed from `user_context` on
every request cannot be overwritten by a session `SET` at all, whereas a
configuration key can.

**And code execution bypasses all of it.** See the security notes above: a
plan-level plugin lives in the same JVM as user code, which runs with the
application's credentials. Plan-level enforcement is a real control among
cooperating users, and an honest audit trail; it is not a boundary against a
determined one.

### Is this a generic gRPC gateway? ###

No — and deliberately so. Knox proxies exactly one gRPC service,
`spark.connect.SparkConnectService`. There is no configuration property that
points this listener at an arbitrary gRPC backend, and a call to any other proto
service is answered `UNIMPLEMENTED`.

It is worth saying why that is not the slippery slope it might look like. Knox's
servlet pipeline is already a generic reverse proxy: an arbitrary REST API is
proxied with a service definition and one rewrite rule, no code, and WebSocket
proxying matches any service definition by context path. gRPC was the one
protocol class outside that coverage, because it is the one the servlet stack
cannot physically carry. This closes that gap; it does not begin a pattern of
per-protocol special cases.

It is also worth being open about what sits behind the abstraction, because
anyone reading the source will notice it: most of this feature is not
Spark-specific. The listener,
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
- **Bearer tokens only.** Neither gRPC nor the vanilla clients can carry Kerberos
  on the RPC path, and the connection string exposes no client-certificate
  surface, so mutual TLS from the client is not available without a non-vanilla
  `channelBuilder`.
- **No gRPC-Web.** Spark Connect clients speak native gRPC; no translation layer
  is provided, so browsers cannot talk to this listener directly.
- **No per-RPC metrics yet.** Audit records cover each call; the standard gateway
  metrics do not yet include gRPC counters, latencies or active-stream gauges.
- **`Config` key restrictions beyond the reserved prefix, and per-RPC allow/deny
  lists**, are not implemented. `AddArtifacts` gating and reserved-prefix
  protection are the only message-level controls.

### Possible future work ###

Recorded so the reasoning is not lost; none of this is implemented or promised.

- **Session affinity across multiple backends** — consistent hashing on
  `session_id` with an in-memory affinity map. Failover semantics would stay
  honest: if a backend dies its sessions die, and Knox routes the client's *new*
  session to a live backend rather than pretending the old one survived. The same
  mechanism with a different stickiness key (principal or group) is also the
  route to per-user or per-tenant backend instances, which is what a deployment
  needing genuine storage-level isolation actually wants.
- **More topology discriminators.** Two beyond `knox-topology` metadata were
  designed for but not built. A **token claim** binding a topology at issuance
  would make routing an authorization property — a user could not reach a
  topology their token was not minted for. **Virtual-host mapping** on the HTTP/2
  `:authority` would be invisible in the connection string and immune to clients
  stripping unknown parameters, but needs DNS discipline and one certificate
  covering every mapped hostname; where the platform PKI cannot issue
  multi-name (SAN or wildcard) certificates, a listener per topology is the
  practical alternative, since every listener then presents the same hostname and
  a plain single-name certificate covers them all.
- **Identity-assertion provider mapping** on this path, so the asserted principal
  can be transformed the way the servlet pipeline transforms it.

### References ###

- Spark Connect connection string specification —
  `apache/spark: sql/connect/docs/client-connection-string.md`
- Spark Connect protocol definitions —
  `apache/spark: sql/connect/common/src/main/protobuf/spark/connect/`
  (vendored into `gateway-service-sparkconnect`; see the README there for the
  exact revision and the refresh procedure)
- PySpark `ChannelBuilder`, for how connection-string parameters become metadata —
  `apache/spark: python/pyspark/sql/connect/client/core.py`
- [SPARK-51156](https://issues.apache.org/jira/browse/SPARK-51156) — the
  pre-shared backend token (`spark.connect.authenticate.token`)
- [KNOX-3402](https://issues.apache.org/jira/browse/KNOX-3402) — this feature
