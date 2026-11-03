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

## gRPC Support ##

### Introduction ###

Knox can proxy gRPC services. A dedicated listener — or several, each on its own
port — runs alongside Jetty, terminating gRPC calls at the edge and relaying them
to a backend declared in a topology — with Knox's usual authentication, topology ACLs and
audit applied first.

It is generic. The listener compiles against no `.proto` file and no generated
class; it relays opaque bytes for whichever proto services a deployment names.
Everything protocol-specific is a configuration value.

That is worth stating plainly because it is a change of posture. Knox's servlet
pipeline was already a generic reverse proxy — an arbitrary REST API needs a
service definition and a rewrite rule, no code, and WebSocket proxying matches
any service definition by context path. gRPC was the one protocol class outside
that coverage, because it is the one the servlet stack cannot physically carry.
This closes the gap without starting a pattern of per-protocol special cases.

The motivating workload was **Spark Connect**, and it runs through this page as
the worked example. Spark Connect is the decoupled client/server protocol for
Apache Spark (3.4+, and the default architecture in Spark 4); its OSS server has
essentially no built-in authentication or authorization, because the project
assumes a fronting proxy provides them. But nothing in the gateway knows what
Spark Connect is. Every Spark-specific thing below is a value in
`gateway-site.xml` or a topology file.

#### What the gateway adds ####

- **Authentication at the edge** — Knox-issued JWTs (KnoxToken bearer tokens),
  validated before anything reaches the backend.
- **Identity assertion** — where a protocol carries a *client-asserted* identity
  field, Knox overwrites it with the authenticated principal.
- **Coarse authorization** — the usual topology ACLs decide who may use the
  service at all, and per-topology allow/deny lists decide which RPCs they may
  call.
- **Auditing** — one record per RPC: principal, topology, backend, method,
  outcome and duration.

#### Why a separate port ####

gRPC does not go through Knox's servlet pipeline, and could not: it needs HTTP/2
negotiated over ALPN, which Knox's Jetty connectors do not offer; the Servlet 3.1
API Knox targets has no way to read or write HTTP trailers, where `grpc-status`
and any structured error details live; and the outbound dispatch layer is built
on a strict request/response HTTP/1.1 client, whereas gRPC calls may stream in
either or both directions for hours.

Routing rules it out independently. gRPC fixes request paths at
`/pkg.Service/Method`, and connection strings for such protocols commonly forbid
a path component altogether — a Spark Connect `sc://` URL may not contain one, by
rule, to stay compatible with the gRPC standard. Knox's usual
`/gateway/{topology}/{service}` routing therefore has nothing to match on.

So gRPC is served by a **dedicated listener on its own port**, started and
stopped with the gateway, alongside Jetty rather than inside it:

                              Knox JVM
             ┌──────────────────────────────────────────────┐
    grpc/h2 ▶│ :15002  gRPC listener (Netty)                │
             │   ├─ TLS (gateway identity, or its own)       │
             │   ├─ audit (one record per RPC)              │
             │   ├─ authentication (bearer JWT)             │      grpc/h2
             │   ├─ routing (topology metadata → registry)  │──▶  backend
             │   ├─ authorization (topology ACLs)           │      service
             │   ├─ method allow/deny (by RPC name)         │
             │   └─ relay (asserts the identity field)      │
             ├──────────────────────────────────────────────┤
             │ :8443  Jetty (the existing servlet gateway)  │
             └──────────────────────────────────────────────┘

The listener is discovered through the `ProtocolListener` service-loader
interface, so gRPC and its shaded Netty stay off the servlet classpath entirely
unless the module is deployed. This mirrors how Knox already handles WebSockets,
which likewise bypass the topology filter chains and do their own
authentication — the difference being that a WebSocket upgrade can share Jetty's
HTTP/1.1 connector, and gRPC cannot.

### Relaying without a schema ###

Every call for a permitted proto service reaches the same byte-level relay. No
generated service is registered, no method list is enumerated, and no message
type is known.

**All four RPC shapes** — unary, server-streaming, client-streaming and
bidirectional — go through one handler, because they differ only in how many
messages flow each way. Backend status and trailers are relayed verbatim, which
matters more than it looks: gRPC carries `grpc-status` in trailers, and protocols
commonly pack structured error details there too, so anything that interprets or
drops trailers breaks error reporting wholesale.

Flow control is explicit in both directions — a message is requested from one
side only once the other has accepted the previous one — so a slow client cannot
make the gateway buffer an unbounded number of response batches, and cancellation
propagates both ways rather than leaving orphaned work on the backend.

An RPC added by a newer version of a protocol is proxied like any other, since
nothing enumerates methods. Proto services *not* named in the permitted set are
answered `UNIMPLEMENTED` — the same answer a real server gives for a method it
does not have, so this reveals nothing about what the gateway fronts.

#### Identity assertion by field number ####

The one thing a byte-level proxy would normally give up is the ability to touch
message contents, and that is exactly what makes this worth doing. Protocols in
this family commonly trust a *client-asserted* identity field: the client states
who it is and the server believes it. Such a field typically keys the server-side
session cache, so leaving it alone lets one caller collide with — or attach
to — another's session simply by claiming their name. That is session isolation,
not merely audit fidelity.

It is recovered without a schema by a list of rewrite rules, each naming a place
in the message by field number and what to write there:

      <property>
          <name>gateway.grpc.identity.rules</name>
          <value>2.1=principal,2.2=principal</value>
      </property>

A rule is `path=subject`. The path is one or more protobuf field numbers
separated by dots: each leading number is a nested message to descend into, and
the last is the string field to replace. So `1=principal` rewrites a top-level
field, and `2.1=principal` rewrites a field one level down. The subject names
what to write; `principal` — the subject of the validated bearer token — is the
only one this build supports, and an unknown one is refused at startup rather
than written as an empty string.

The example above is Spark Connect: `user_context = 2`, holding `user_id = 1` and
`user_name = 2`, both set to the authenticated principal. Field numbers are the
part of a protobuf schema that cannot change without breaking every deployed
client, so this tracks no particular protocol version. Zero rules is the ordinary
case for a protocol that carries no identity: the relay is then a pure pipe.

What happens per request:

- Every field named by a rule is replaced with the authenticated principal.
  *Every occurrence* of it, not just the first — protobuf merges repeated
  records, so one left alone could override the one that was asserted.
- Everything else is copied byte for byte, including extensions and fields from a
  newer protocol version this build has never heard of. Those are not merely
  preserved; they are never decoded.
- A path that is absent is created, the whole chain of it, so the backend never
  sees a request whose identity Knox did not put there.
- A message that cannot be parsed is rejected with `INVALID_ARGUMENT` rather than
  forwarded. So is one whose shape contradicts the rules — a field a rule expects
  to descend into that arrives as a scalar, say. Forwarding either would send the
  caller's own claim through unaltered, which is what this exists to prevent.

Rules are validated at startup: field numbers in protobuf's legal range, avoiding
the reserved 19000–19999 block, no two rules writing the same place, and none
writing a value at a field another descends through.

#### The scan limit ####

Rewriting a nested field means slicing it out and rebuilding it, so a client that
put a hundred megabytes inside the identity container could make the gateway copy
it several times over. Every field a rule touches must therefore lie wholly
within the first `gateway.grpc.identity.scan.limit` bytes of the request, 128 KiB
by default:

      <property>
          <name>gateway.grpc.identity.scan.limit</name>
          <value>131072</value>
      </property>

This bounds where the identity may sit, not how large a request may be. Generated
serializers emit fields in ascending number order, so an identity container with
a low field number lands near the front however large the payload after it — a
128 MB `AddArtifacts` chunk passes, and costs the same to rewrite as a small
message.

A request that breaks the limit is **refused**, with `INVALID_ARGUMENT`, rather
than partially asserted. That is the security-relevant part: giving up on a rule
whose target sits beyond the limit and synthesising a fresh identity instead would
leave the caller's own claim in the message behind ours, where protobuf's
last-wins merge would let it take effect. Synthesis when a path is genuinely
absent is unaffected — the whole message was walked and nothing was found, so
there is nothing an appended identity could be overridden by.

Raise the limit for a protocol that puts its identity late or carries an unusually
large identity container. Lower it to tighten the bound.

Correctness is held to typed semantics by test. The Spark Connect protos are
vendored at **test scope only** and used as an oracle: they say what the wire
bytes are supposed to mean, and the hand-written wire code is checked against
generated classes across every request shape, unknown fields, absent containers
and large payloads. A protocol change that moved the fields this depends on fails
CI rather than production. Nothing generated ships in the module jar, and the
gateway needs no protobuf library at runtime.

### Configuration ###

The listener is disabled by default. Enable it in
`<KNOX-HOME>/conf/gateway-site.xml`:

      <property>
          <name>gateway.grpc.enabled</name>
          <value>true</value>
      </property>
      <property>
          <name>gateway.grpc.service.role</name>
          <value>SPARKCONNECT</value>
          <description>The Knox service role that ties this listener to a topology.</description>
      </property>
      <property>
          <name>gateway.grpc.proto.services</name>
          <value>spark.connect.SparkConnectService</value>
          <description>The proto services to proxy. Anything else gets UNIMPLEMENTED.</description>
      </property>
      <property>
          <name>gateway.grpc.identity.rules</name>
          <value>2.1=principal,2.2=principal</value>
          <description>Where the identity lives: user_context = 2, user_id = 1, user_name = 2.</description>
      </property>

`gateway.grpc.proto.services` is required — a listener with nothing to proxy
refuses to start, rather than binding a port that answers `UNIMPLEMENTED` to
everything and looks like it is working.

By default a listener presents the gateway's own TLS identity — the same keystore
and alias Jetty uses — so there is no second certificate to manage. A listener
can present its own instead; see below. Running without TLS is logged as a
warning: bearer tokens would cross the network in clear text, so it is a
development posture only.

#### Several listeners, several certificates ####

A gateway can run more than one gRPC listener. They are **not** a policy
boundary — each still routes to as many topologies as its clients select, by the
same metadata key — so this is not an alternative to topology selection.

What separates them is the socket, and therefore the certificate on it. Serving
several hostnames from one endpoint needs one certificate naming all of them, and
a platform PKI that cannot issue multi-name (SAN or wildcard) certificates cannot
produce one. A listener per hostname, each presenting a plain single-name
certificate, serves those clients without it:

      <property>
          <name>gateway.grpc.listener.names</name>
          <value>analytics, partner</value>
      </property>

      <property>
          <name>gateway.grpc.analytics.port</name>
          <value>15002</value>
      </property>
      <property>
          <name>gateway.grpc.analytics.ssl.keystore.path</name>
          <value>/opt/pki/analytics.p12</value>
      </property>

      <property>
          <name>gateway.grpc.partner.port</name>
          <value>15003</value>
      </property>
      <property>
          <name>gateway.grpc.partner.ssl.keystore.path</name>
          <value>/opt/pki/partner.p12</value>
      </property>

Clients then dial `sc://analytics.example.com:15002` and
`sc://partner.example.com:15003`, both resolving to the same gateway, each
validating a certificate issued for the name it asked for — and both selecting
topologies exactly as they would on a single listener.

**Every property inherits.** A listener reads `gateway.grpc.<name>.<property>`
where it sets one and the plain `gateway.grpc.<property>` otherwise, so shared
settings — the service role, proto services, identity rules, message limits — are
written once and only the differences are repeated. Naming no listeners runs
exactly one, configured entirely from the plain properties, which is the ordinary
deployment and what every earlier example on this page describes.

The TLS properties are the exception: `ssl.keystore.path`, `ssl.keystore.alias`,
`ssl.keystore.password.alias` and `ssl.keystore.type` are never inherited, since
sharing one keystore across listeners would defeat the point of having several. A
listener that sets no keystore path presents the gateway identity.

| Property                                             | Default             | Meaning                                                                     |
|------------------------------------------------------|---------------------|------------------------------------------------------------------------------|
| `gateway.grpc.listener.names`                        | *(none)*            | Comma-separated listener names. Empty means one listener from the plain properties. |
| `gateway.grpc.<name>.<property>`                     | the plain property  | Any property above, set for one listener.                                    |
| `gateway.grpc.<name>.ssl.enabled`                    | `ssl.enabled`       | Whether this listener presents TLS.                                          |
| `gateway.grpc.<name>.ssl.keystore.path`              | *(gateway identity)*| A keystore holding this listener's server certificate.                       |
| `gateway.grpc.<name>.ssl.keystore.type`              | `PKCS12`            | Its format.                                                                  |
| `gateway.grpc.<name>.ssl.keystore.alias`             | *(the sole entry)*  | Which entry to present. Required if the keystore holds more than one key.    |
| `gateway.grpc.<name>.ssl.keystore.password.alias`    | *(gateway's)*       | Knox alias holding the keystore password.                                    |

Names may contain `a-z`, `0-9`, `-` and `_`. A name that collides with an
existing property — `identity`, `default`, `methods` and so on — is refused at
startup, as are two listeners configured on one port: the alternative is an
address-in-use error naming neither of them.

Which listeners exist is fixed at startup, like whether the feature runs at all.
A name added or removed in `gateway-reloadable.xml` is reported in the log rather
than acted on; the per-listener properties that *can* move at runtime move for
each listener independently.

#### All properties ####

| Property                                           | Default         | Meaning                                                                                             |
|----------------------------------------------------|-----------------|-----------------------------------------------------------------------------------------------------|
| `gateway.grpc.enabled`                             | `false`         | Master switch; the listener is not started when false.                                              |
| `gateway.grpc.port`                                | `15002`         | Port for the gRPC listener. The default is Spark Connect's.                                          |
| `gateway.grpc.service.role`                        | `GRPC`          | Knox service role tying this listener to a topology, and the prefix for its ACL and method params.   |
| `gateway.grpc.proto.services`                      | *(none)*        | Comma-separated proto service names to proxy. Required; anything else gets `UNIMPLEMENTED`.          |
| `gateway.grpc.identity.rules`                      | *(none)*        | Comma-separated `path=subject` rules placing the identity by field number. Empty means no rewrite.   |
| `gateway.grpc.identity.scan.limit`                 | `131072`        | Every field a rule rewrites must end within this many bytes of the request start.                    |
| `gateway.grpc.default.topology`                    | *(none)*        | Topology used when the client selects none.                                                          |
| `gateway.grpc.topology.metadata.key`               | `knox-topology` | Name of the connection-string parameter clients use to select a topology.                            |
| `gateway.grpc.methods.deny`                        | *(none)*        | RPCs refused by default, by method name. Topologies may override.                                    |
| `gateway.grpc.methods.allow`                       | *(none)*        | When set, the only RPCs permitted by default. Topologies may override.                               |
| `gateway.grpc.max.message.size`                    | `134217728`     | Maximum inbound message size in bytes, both legs. Matches Spark's 128 MB default.                     |
| `gateway.grpc.max.concurrent.calls.per.connection` | `1000`          | Maximum concurrent gRPC streams per client connection.                                               |
| `gateway.grpc.permit.keepalive.time`               | `10000`         | Minimum tolerated interval between client keepalive pings, in ms.                                     |
| `gateway.grpc.permit.keepalive.without.calls`      | `true`          | Whether clients may ping an idle channel. Spark Connect clients do.                                   |
| `gateway.grpc.channel.idle.timeout`                | `1800000`       | Idle time before an unused backend channel is shut down, in ms.                                       |
| `gateway.grpc.drain.timeout`                       | `30000`         | How long in-flight RPCs get to finish at shutdown, in ms.                                             |
| `gateway.grpc.backend.token.alias`                 | *(none)*        | Alias holding the backend's pre-shared token (see Security considerations).                           |

The message, stream and keepalive limits are the listener's DoS surface. A new
socket accepting 128 MB messages on long-lived streams wants those bounds set
from the start, not added after the first incident.

#### Keeping these out of `gateway-site.xml` ####

Knox has no `conf.d` directory, but it does load one optional extra file. The
gateway reads exactly three configuration files from `{GATEWAY_HOME}/conf`, in
this order, with later files overriding earlier ones:

      gateway-default.xml
      gateway-site.xml
      gateway-reloadable.xml

`gateway-reloadable.xml` is not shipped and does not have to exist, so the
`gateway.grpc.*` properties can live there instead of being merged into
`gateway-site.xml`. It is a single shared file rather than a per-feature
directory, so anything else using it has to co-exist in the same file — but it
does keep this feature's settings out of the main one. See
[Reloadable Gateway Configuration](config.md) for the general mechanism.

Knox re-reads that file every `gateway.config.refresh.interval` milliseconds
(default 10 seconds). Two of the properties above then take effect immediately;
the rest cannot, because they are built into the bound server.

**Applied on the next RPC, no restart:**

- `gateway.grpc.identity.rules`
- `gateway.grpc.identity.scan.limit`
- `gateway.grpc.default.topology`

These are the message-level and routing controls — the ones an operator is most
likely to want to change in response to something happening. They apply to the
very next call without interrupting any session.

**Restart required:** everything else — `gateway.grpc.enabled` itself, the port,
the proto service list, the service role, the topology metadata key, TLS, the
gateway-wide method lists, message and stream limits, keepalive settings, and the
channel idle and drain timeouts. Whether the listener runs at all is decided once
at startup, and the rest are fixed when the socket is bound.

Changing a restart-only property in a running gateway does not silently do
nothing. The refreshed configuration is compared against what the gateway started
with, and a warning names what could not be applied — for the transport settings,
which properties changed; for `gateway.grpc.enabled`, that the listener cannot be
started or stopped without a restart. Switching it on when it was off at startup
is reported too, which is the case most likely to be mistaken for a malfunction:
without the warning, the only symptom is a port that never opens.

### Topology configuration ###

Declare the backend like any other service, under the role named by
`gateway.grpc.service.role`. The registry treats the URL as an opaque string, so
`grpc://` and `grpcs://` need no special handling:

      <service>
          <role>SPARKCONNECT</role>
          <url>grpc://backend-host:15002</url>
      </service>

Use `grpcs://` for a TLS backend; Knox verifies it against the HTTP client
truststore, falling back to the gateway keystore. Any other scheme, or a URL
without a host and port, is refused with `FAILED_PRECONDITION`.

Authorization uses the ordinary `AclsAuthz` provider syntax, keyed on the same
role:

      <provider>
          <role>authorization</role>
          <name>AclsAuthz</name>
          <enabled>true</enabled>
          <param>
              <name>SPARKCONNECT.acl</name>
              <value>*;analysts;*</value>
          </param>
      </provider>

The syntax and semantics are the servlet provider's, down to sharing its parser:
`users;groups;ipaddresses`, an `AND`/`OR` processing mode via
`SPARKCONNECT.acl.mode`, `*` wildcards, and the `KNOX_ADMIN_USERS` /
`KNOX_ADMIN_GROUPS` placeholders. Nobody should have to learn a second ACL
dialect because the transport changed.

Group membership comes from the `knox.groups` claim in the token, so configure
`knoxtoken` to include groups if you intend to write group ACLs.

#### Refusing individual RPCs ####

gRPC puts the method in the request path, so allowing or denying whole RPCs by
name needs no marshaller, no descriptor and no schema. It is the coarsest control
the gateway offers and the only message-level one that survives completely intact
on a byte-level proxy.

Configure it alongside the ACLs, on the same provider:

      <param>
          <name>SPARKCONNECT.methods.deny</name>
          <value>AddArtifacts</value>
      </param>

Names may be bare (`AddArtifacts`, matching that method on any service) or fully
qualified (`spark.connect.SparkConnectService/AddArtifacts`), and matching is
case-insensitive. `SPARKCONNECT.methods.allow` is the inverse and, once given, is
exhaustive: anything unnamed is refused, so an RPC added by a newer protocol
version does not appear by default. Deny wins over allow. A topology that sets
neither falls back to the gateway-wide `gateway.grpc.methods.*` lists.

Being keyed on the topology is the point: the same gateway can front a cluster
where uploading code is fine and one where it is not, and the difference is a
parameter in the topology that already forms the policy boundary. The check runs
last in the chain, so a denial is attributable to a known user in a known
topology.

Be clear about the limit, though. Denying an upload RPC does not close code
execution where a protocol also allows inline functions inside ordinary
requests — inline Python and Scala UDFs travel *inside* Spark Connect's
`ExecutePlan`. It shrinks the attack surface rather than drawing a boundary. See
Security considerations.

### Multiple backends ###

One topology per backend, all served by the single listener port. A topology
declares exactly one service for the role, so a second backend means a second
topology:

      conf/analytics.xml   ->  grpc://spark-analytics:15002
      conf/etl.xml         ->  grpc://spark-etl:15002

Clients pick one with the `knox-topology` connection parameter:

      sc://knox-host:15002/;use_ssl=true;token=<jwt>;knox-topology=analytics
      sc://knox-host:15002/;use_ssl=true;token=<jwt>;knox-topology=etl

Both connect to the *same* Knox port and are routed to different backends.
Topology selection is per-RPC, from call metadata, so concurrent sessions from
different users — or from one user — are multiplexed over that one port onto
distinct backends. Knox keeps one pooled gRPC channel per backend URL and shares
it across all calls routed there; channels go idle on their own and reconnect
transparently, so a cached entry for an unused backend costs nothing.

This is safe for session-oriented protocols because the discriminator is sticky
by construction: the client sends the same value on every request of the
connection, so every call in a session lands on the backend that owns it — which
Spark Connect's `ReattachExecute` requires. Nothing round-robins.

Each topology carries its own authentication provider, ACLs, method rules and
audit scope, so "separate backend" and "separate policy boundary" stay aligned.

#### Authorizing which users may select which backend ####

Topology selection is a client-supplied value, so it is authorized rather than
trusted. Authorization runs *after* routing and is evaluated against the topology
that was selected — naming a topology in a connection string is not the same as
being allowed to use it. Give each topology its own ACL:

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
applies per topology, so selection can be gated by user name, by group, by source
address, or by a combination.

Two things to be deliberate about:

- **The default is permissive.** A topology that declares no ACL for the role, or
  whose `AclsAuthz` provider is disabled, is reachable by *any* authenticated
  user. This matches the servlet provider's behaviour, but it means restricting
  selection is something you switch on, not something you get for free.
- **Group ACLs need group claims.** Groups come from the token's `knox.groups`
  claim, so a `knoxtoken` deployment that does not embed groups will match no
  group ACL. Where groups are unavailable, gate on user names instead.

A user probing topologies they cannot use can tell an existing one
(`PERMISSION_DENIED`) from one that does not exist or declares no service for the
role (`UNAVAILABLE`). They must already hold a valid token to learn even that, but
do not treat topology names as secrets.

What is *not* supported is several backends **within** one topology. Where
sessions are server-side state keyed on the caller's identity and a session id,
spreading one topology across backends needs session-affine routing rather than
any form of load balancing; that is not implemented (see Limitations).

#### Adding a backend without a restart ####

Topologies are hot-reloaded. Knox watches the topologies directory and picks up
changes within about five seconds, so dropping in a new topology file, or editing
an existing one, takes effect on a running gateway:

- **A new topology, or a changed backend URL** — takes effect on the next RPC.
  The backend is resolved from the service registry per call, and redeployment
  rewrites the registry entry.
- **Changed ACLs or method rules** — take effect on the next RPC after the
  redeployment. The listener caches parsed ACLs and method policies per topology
  and drops both caches when topologies are redeployed.
- **A deleted topology** — subsequent calls selecting it fail `UNAVAILABLE`.
  Calls already in flight are not interrupted.

Only `gateway-site.xml` properties — the port, the proto service list, message
limits and so on — need a gateway restart, since the listener binds its socket and
captures those settings at startup.

### Connecting ###

Clients need no plugins or code changes. First acquire a token — over HTTPS,
authenticating however that topology is configured:

      curl --negotiate -u : https://knox:8443/gateway/tokens/knoxtoken/api/v1/token

Then put it in the connection string. For Spark Connect:

      sc://knox-host:15002/;use_ssl=true;token=<knox-jwt>;knox-topology=analytics

Two details make this work, and both generalize to any gRPC client that can set
static metadata. The `token=` parameter is sent as a standard
`Authorization: Bearer` header and forces TLS on. Any parameter the client does
not recognize — `knox-topology` here — is sent as gRPC metadata on every request,
which is how a topology gets selected despite gRPC forbidding a path component in
the connection URL. If you set `gateway.grpc.default.topology`, the parameter can
be omitted.

#### Renaming the topology parameter ####

`knox-topology` is only a default. Because the name appears verbatim in every
connection string a user writes, a deployment may prefer one that describes the
choice being made rather than the gateway making it:

      <property>
          <name>gateway.grpc.topology.metadata.key</name>
          <value>cluster</value>
      </property>

Clients then write `sc://knox-host:15002/;use_ssl=true;token=<jwt>;cluster=analytics`.
The gateway strips this parameter before forwarding, so the backend never sees it
under any name.

gRPC restricts header names to lowercase letters, digits and `-_.`, and reserves
the `-bin` suffix for binary values. A name that breaks those rules — or that
collides with `authorization` — is rejected when the gateway starts, with an
explanation, rather than failing obscurely on the first call.

### Worked example: Spark Connect ###

Everything Spark-specific in a working deployment. In `gateway-site.xml`:

      <property>
          <name>gateway.grpc.enabled</name>
          <value>true</value>
      </property>
      <property>
          <name>gateway.grpc.service.role</name>
          <value>SPARKCONNECT</value>
      </property>
      <property>
          <name>gateway.grpc.proto.services</name>
          <value>spark.connect.SparkConnectService</value>
      </property>
      <!-- user_context = 2, holding user_id = 1 and user_name = 2 -->
      <property>
          <name>gateway.grpc.identity.rules</name>
          <value>2.1=principal,2.2=principal</value>
      </property>

The whole `spark.connect.SparkConnectService` surface is then proxied, every RPC
relayed with its status and trailers passed through verbatim and its
`user_context` replaced:

| Shape            | RPCs                                                                                                                                        |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| Unary            | `AnalyzePlan`, `Config`, `ArtifactStatus`, `Interrupt`, `ReleaseExecute`, `ReleaseSession`, `FetchErrorDetails`, `CloneSession`, `GetStatus` |
| Server-streaming | `ExecutePlan`, `ReattachExecute`                                                                                                             |
| Client-streaming | `AddArtifacts`                                                                                                                               |

That table describes Spark Connect, not the gateway: the relay never enumerates
methods, and a Spark release that adds RPCs needs no change here. Nothing in the
message bodies is rewritten apart from the identity fields — there are no URLs or
hostnames inside these protobufs, so Knox's rewrite machinery has no role.

### What failures look like ###

Every rejection carries a gRPC status code and a description, both of which reach
the client — PySpark surfaces them in the exception it raises. The codes are
chosen so the cause is distinguishable:

| Situation | Status | Description |
|---|---|---|
| No token, or a token that is not valid | `UNAUTHENTICATED` | `Invalid or missing bearer token` |
| No topology selected and no default configured | `UNIMPLEMENTED` | `no topology selected; set a knox-topology connection parameter or configure a default topology` |
| Selected topology declares no service for the role, or does not exist | `UNAVAILABLE` | `topology <name> declares no SPARKCONNECT service` |
| Topology ACLs refuse the user | `PERMISSION_DENIED` | `Not permitted to use SPARKCONNECT` |
| The RPC is denied, or absent from an allow list | `PERMISSION_DENIED` | `This RPC is not permitted in this topology` |
| A proto service the listener does not front | `UNIMPLEMENTED` | from gRPC itself |
| A request that is not well-formed protobuf, where identity assertion is on | `INVALID_ARGUMENT` | `Request message is not a well-formed protobuf message` |
| An identity field that ends beyond the scan limit | `INVALID_ARGUMENT` | `Identity field <n> extends past the first <limit> bytes of the request...` |
| A field whose wire type contradicts the identity rules | `INVALID_ARGUMENT` | `...the configured identity rules do not describe this message` |
| Backend unreachable or its TLS cannot be established | `UNAVAILABLE` | from the backend leg |

Authentication failures are deliberately uniform: the description does not say
whether a token was expired, revoked, or badly signed, since that would tell
someone probing which of their guesses was closest.

If the listener is enabled but **no** topology declares a service for the
configured role, the gateway still starts and binds the port — enabling the
listener and declaring a backend are separate steps in separate files. Clients
then see the `UNIMPLEMENTED` or `UNAVAILABLE` cases above depending on what they
sent.

This is not treated as an error, because it is a reasonable steady state: a
deployment may enable the listener as a matter of course and add a topology only
when someone provisions a backend — possibly never. The gateway notes it at
`DEBUG` rather than warning:

      DEBUG gateway.grpc - The SPARKCONNECT listener is running but no deployed
      topology declares a SPARKCONNECT service, so calls will be rejected until
      one does.

Enable debug logging for `org.apache.knox.gateway.grpc` if you are investigating
why calls are being refused. Adding a topology fixes it without a restart.

Each call also produces one audit record, whether it succeeded or was rejected —
including calls refused before any backend was contacted. The record carries the
principal, method, status code, topology, backend URL, remote address, authority
and duration.

### Kerberos environments ###

Neither gRPC nor typical clients support SPNEGO, and gRPC has no
challenge-response step for it to hook into. Kerberos therefore authenticates
*token acquisition* rather than each RPC: a `kinit`'d user or a keytab'd service
fetches a token from a `knoxtoken` topology using HadoopAuth/SPNEGO, and the JWT
carries the data path.

This is the same trade Kerberized Hadoop already makes — nobody SPNEGOs every
HDFS block read. It is also better operationally for long-running jobs: an
administrator can revoke one token without touching the principal. Validation
covers issuer, expiry, not-before, signature and, when server-managed token state
is on, revocation.

Tokens are validated when an RPC starts, not continuously. A multi-hour
`ExecutePlan` is not killed when its token expires; the next RPC fails with
`UNAUTHENTICATED`. Cutting off long queries at expiry would punish precisely the
workloads these protocols exist to serve, and the backend's own session timeout
still bounds how long a session survives.

### What works well, and what does not ###

**What you get for any gRPC service, with no code:** TLS from the gateway
identity, bearer authentication, coarse authorization by topology ACL, topology
routing across multiple backends, per-topology method allow/deny lists, backend
channel pooling, per-RPC audit records, graceful drain, correct flow control and
cancellation in both directions, and verbatim relay of statuses and trailers.
Streaming RPCs of every shape work, including long-lived ones.

**What needs the protocol to cooperate:** identity assertion works where the
identity sits at constant field numbers — at any depth, but the same numbers on
every request — and where the backend actually reads it. A protocol that carries
identity somewhere structurally variable is out of reach of a field-number path:
a `oneof` wrapper whose case varies, an `Any`-boxed payload, a map entry selected
by key. Such a deployment still gets authentication, authorization, method gating
and audit, but not assertion. Nor is a rule the place to express a conditional —
the rules describe where a value goes, not when.

**What is deliberately not offered:** anything finer than a whole RPC. Screening
particular configuration keys, inspecting query plans, rewriting arbitrary
payload fields — none of it is available, because it would mean assuming a
message shape the gateway otherwise never assumes, and re-acquiring the version
coupling this design exists to avoid. Where that matters, the right home is a
component inside the backend, which has the real schema on its classpath for
free.

An earlier iteration of this feature did screen Spark Connect's `Config` RPC for
reserved session keys, and gated `AddArtifacts` from inside the message. Both are
gone. Artifact gating became a method-name rule, which is strictly more general;
the reserved-key guard was dropped in favour of a server-side component that
recomputes the identity per request. A key a client can write is a key a client
can forge, whereas a value derived from the asserted identity on every request
cannot be overwritten by a session setting at all.

### Security considerations ###

**Knox's authorization here is coarse by design.** It answers "may this user use
this service in this topology" and "may they call this RPC". Database, table,
column and row-level policy must be enforced inside the backend — for example by
a Ranger-backed plan-level plugin keyed off the identity Knox asserts.

**Asserting the identity field is not storage-level enforcement.** On a Spark
Connect server, `user_id` keys the session cache — so two users can never share a
session — and appears in logs and events. It is not propagated into Spark's
`CurrentUserContext`, so `current_user()` in SQL reports the Spark application's
own user unless a server-side component bridges it. A shared Spark Connect server
is one application running as one principal, and its storage credentials are that
principal's.

**User-supplied code bypasses plan-level policy.** Uploaded jars and inline
Python/Scala UDFs run inside that JVM with that principal's credentials, so they
can read data directly. Denying the upload RPC by name shrinks the attack surface
but does not close it, because inline UDFs reach the same capability. This is a
property of plan-level enforcement generally, not something the gateway
introduces. Deployments needing a hard boundary want per-user or per-tenant
backend instances.

**Restrict the backend.** Knox in front of an openly reachable backend port
secures nothing. Firewall the backend so only Knox can reach it, and where the
backend supports a pre-shared token — Spark 4's
`spark.connect.authenticate.token`, say — store it as a Knox alias and name it in
`gateway.grpc.backend.token.alias`. Knox then presents it on the backend leg, and
strips the client's own credential there, so a client cannot bypass the gateway
even with network reachability. Knox-internal routing metadata is stripped too:
it was addressed to the gateway, and the backend has no use for it.

### Making the asserted identity usable inside the backend ###

The deployment this was built for is a single always-on backend behind the
firewall, running as a privileged principal, with fine-grained authorization
enforced *inside* it — typically a plan-level plugin evaluating Ranger policies
against the identity Knox asserts. Getting that identity from the gateway into
the engine takes one more step, and it is worth being explicit about it because
the gap is easy to miss.

**The carrier of record is the identity field.** Knox rewrites it on every
message, it keys the server-side session cache — so two users cannot share a
session by construction — and it lands in Knox's audit records. Anything else
should be *derived* from it, never asserted independently by the client.

**But OSS Spark does not surface it to SQL.** `user_id` is used for the session
key and for logging; it is not propagated into `CurrentUserContext`, so
`current_user()` returns the Spark application's own user. A server-side
component has to bridge it.

The robust bridge is a gRPC `ServerInterceptor` deployed with the Spark
application and registered through `spark.connect.grpc.interceptor.classes`. That
is a static configuration, so clients cannot alter it. The interceptor reads
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

A weaker alternative is to have the session carry the identity in a reserved
configuration key. Knox does **not** do this for you, and does not police such a
key either: the gateway reads only the identity fields, by field number, and has
no knowledge of any RPC's internal structure. Deployments that use a reserved key
anyway should have the server-side component own it rather than trusting anything
the client sends.

**And code execution bypasses all of it.** See the security notes above: a
plan-level plugin lives in the same JVM as user code, which runs with the
application's credentials. Plan-level enforcement is a real control among
cooperating users, and an honest audit trail; it is not a boundary against a
determined one.

### Limitations ###

- **One backend URL per topology.** Where sessions are server-side state and a
  reattach RPC must reach the backend owning the operation, round-robin over
  several backends would be wrong; session-affine routing across multiple
  backends is not yet implemented.
- **A listener's set is fixed at startup.** Adding or removing one, or changing
  its port or TLS identity, needs a gateway restart; the change is reported
  rather than silently ignored.
- **One service role and identity layout per listener.** Two protocols whose
  identities sit at different field numbers need a listener each, and therefore a
  port each.
- **Identity paths are constant, and identity fields are strings.** A rule names
  fixed field numbers at a fixed depth; it cannot select a map entry by key,
  follow a `oneof` whose case varies per request, or write a non-string field.
- **Rewritten fields must lie within the scan limit** — 128 KiB into the request
  by default. A protocol that emits its identity after a large payload needs the
  limit raised; requests that break it are refused, not partially asserted.
- **The asserted principal is the token's subject.** Identity-assertion provider
  mapping rules are not applied on this path.
- **Bearer tokens only.** Neither gRPC nor typical clients can carry Kerberos on
  the RPC path, and connection strings expose no client-certificate surface, so
  mutual TLS from the client is not available without a non-vanilla
  `channelBuilder`.
- **No gRPC-Web.** No translation layer is provided, so browsers cannot talk to
  this listener directly.
- **No per-RPC metrics yet.** Audit records cover each call; the standard gateway
  metrics do not yet include gRPC counters, latencies or active-stream gauges.
- **A gateway restart severs active streams.** Shutdown drains for
  `gateway.grpc.drain.timeout` first, and clients of streaming protocols
  generally recover through their own reattach or retry logic.

### Possible future work ###

Recorded so the reasoning is not lost; none of this is implemented or promised.

- **Session affinity across multiple backends** — consistent hashing on a session
  identifier with an in-memory affinity map. Failover semantics would stay
  honest: if a backend dies its sessions die, and Knox routes the client's *new*
  session to a live backend rather than pretending the old one survived. The same
  mechanism with a different stickiness key (principal or group) is also the
  route to per-user or per-tenant backend instances, which is what a deployment
  needing genuine storage-level isolation actually wants.
- **More topology discriminators.** Two beyond metadata were designed for but not
  built. A **token claim** binding a topology at issuance would make routing an
  authorization property — a user could not reach a topology their token was not
  minted for. **Virtual-host mapping** on the HTTP/2 `:authority` would be
  invisible in the connection string and immune to clients stripping unknown
  parameters, but needs DNS discipline and one certificate covering every mapped
  hostname; where the platform PKI cannot issue multi-name (SAN or wildcard)
  certificates, several listeners — each on its own port with its own single-name
  certificate — already cover that ground, at the cost of a port per hostname
  rather than one shared port.
- **Multiple listeners in one gateway**, each with its own port, role, proto
  service list and identity path, for a deployment fronting more than one gRPC
  protocol.
- **Identity-assertion provider mapping** on this path, so the asserted principal
  can be transformed the way the servlet pipeline transforms it.
- **Passing groups to the backend as metadata.** Group membership does not fit in
  a single identity field, and a server-side plugin that wants it has to look it
  up itself.

### References ###

- Spark Connect connection string specification —
  `apache/spark: sql/connect/docs/client-connection-string.md`
- Spark Connect protocol definitions —
  `apache/spark: sql/connect/common/src/main/protobuf/spark/connect/`
  (vendored at test scope into `gateway-service-grpc`; see the README there for
  the exact revision and the refresh procedure)
- PySpark `ChannelBuilder`, for how connection-string parameters become metadata —
  `apache/spark: python/pyspark/sql/connect/client/core.py`
- [SPARK-51156](https://issues.apache.org/jira/browse/SPARK-51156) — the
  pre-shared backend token (`spark.connect.authenticate.token`)
- [KNOX-3402](https://issues.apache.org/jira/browse/KNOX-3402) — this feature
