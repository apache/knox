<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Apache Knox — Threat Model (v0 DRAFT)

- **Project:** Apache Knox (apache/knox) — the Apache Knox Gateway.
- **Scope:** the single repository `apache/knox`.
- **Date:** 2026-07-02
- **Status:** v0 **draft**, produced by the ASF Security team for the Knox PMC to
  review, correct, and own. Not yet ratified. The Knox PMC (Larry McCay, chair)
  confirmed on 2026-07-02 that they want a v0 draft to react to and take
  ownership of *(maintainer, 2026-07-02)*. Larry McCay completed a line-by-line
  review on 2026-07-09; his answers to the Wave 1–3 and meta §14 questions are
  folded in below and the corresponding claims promoted to *(maintainer)*
  *(maintainer, 2026-07-09 — L. McCay)*.
- **Version binding:** written against Knox 2.x / current `master`, cross-read
  against the 1.6.0 User's Guide. A report against Knox version *N* should be
  triaged against the model as it stood at *N*, not at HEAD. Attach this model
  to a release tag once ratified.
- **Reporting cross-reference:** findings that violate a §8 claimed property
  should be reported per the ASF process (security@apache.org — Knox has no
  project-specific security page; it routes to the Apache Security Team).
  Findings that fall under §3 (out of scope) or §9 (disclaimed properties)
  will be closed citing this document.

### Provenance legend

| Tag | Meaning |
| --- | --- |
| *(documented)* | Stated in Knox's own docs (project site, User's Guide). Cited inline. |
| *(maintainer)* | Stated by a Knox maintainer in response to this process. |
| *(inferred)*   | Reasoned from architecture, domain knowledge, or absence of a doc statement — not yet confirmed. Has a matching §14 open question. |

### Draft confidence

**~14 documented / ~20 maintainer / ~22 inferred** (after the 2026-07-09 PMC
review). The Wave 1–3 core answers — deployment shape, adversary boundary,
the keystone identity-assertion property, backend-hop TLS expectations, the
`HeaderPreAuth` disposition, and the reverse-proxy responsibility split — are
now *(maintainer)*. Still *(inferred)* and awaiting confirmation: Q7, Q9–Q13,
Q17, Q21–Q22, Q28, Q29 (remaining environment/host-side-effect inventory,
self-signed / `-persist-master` stances, secondary adversary capabilities,
the known-non-findings suppression set, and cross-topology isolation).

### What Knox is (one paragraph)

Apache Knox is an **application gateway / reverse proxy that provides perimeter
security for the REST APIs and web UIs of Apache Hadoop-ecosystem (and other)
services** *(documented — project homepage)*. A single Knox instance sits at the
network edge in front of a cluster, terminates client TLS, authenticates the
caller through a configurable **provider chain** (LDAP/AD via Shiro, Kerberos
via HadoopAuth, SAML/OIDC/CAS via pac4j, header pre-auth, SSO cookie, JWT),
authorizes the request against service-level ACLs, rewrites URLs to hide
internal topology, and **dispatches** the request to the appropriate backend
service while asserting the caller's identity to that backend *(documented —
User's Guide)*. It also issues SSO tokens (**KnoxSSO**) and bearer tokens
(**KnoxToken**) and manages secrets in a master-secret-protected credential
store / alias service *(documented — User's Guide)*. Knox's whole purpose is to
**be a trust boundary**: untrusted HTTP clients on one side, trusted cluster
services on the other.

---

## §2 Scope and intended use

**Primary intended use** *(documented — homepage / User's Guide)*:

- Centralized **perimeter authentication + authorization** for Hadoop REST APIs
  and UIs (WebHDFS, YARN, HBase, Hive, Livy, Spark, NiFi, Ambari, Zeppelin,
  NameNode UI, etc.).
- **Topology concealment** — clients see one Knox endpoint; internal host/port
  layout is hidden behind rewrite rules.
- **SSO** for web UIs (KnoxSSO) and **token issuance** for programmatic clients
  (KnoxToken).

**Deployment context** *(maintainer, 2026-07-09 — L. McCay confirmed Q1)*: a
long-running network daemon (Jetty-based server), deployed at the cluster edge,
operated by a cluster administrator. It is **not** an in-process library and
**not** a CLI-first tool (though a Knox CLI exists for admin/keystore
operations).

**Caller roles.** A gateway has no single "caller"; the role splits three ways:

- **Client** — an untrusted HTTP/WebSocket peer at the perimeter. *The primary
  adversary (§7).*
- **Operator / administrator** — trusted for the instance; authors topology
  descriptors, provider configs, and manages the master secret and keystores.
  **Out of scope as an adversary** (§3).
- **Backend service** — the Hadoop service Knox fronts; trusted, on the far side
  of the boundary. Also the **federated IdP** (LDAP/AD, SAML/OIDC provider) that
  Knox delegates authentication to — trusted infrastructure, out of scope as an
  adversary (§3), but its trust assumptions are load-bearing (§5, §14).

**Component-family table** *(maintainer, 2026-07-09 — L. McCay confirmed Q2)*:

| Family | Representative entry point | Touches outside process? | In model? |
| --- | --- | --- | --- |
| Request pipeline (routing, filter chain) | inbound HTTP/WS on the gateway port | network | **in** |
| Authentication provider chain | Shiro/LDAP, HadoopAuth/Kerberos, pac4j SAML/OIDC, HeaderPreAuth, SSOCookie, JWT filters | network (to IdP), filesystem | **in** |
| Authorization (AclsAuthz) | topology ACL evaluation | — | **in** |
| Rewrite / URL translation | per-service rewrite rules | — | **in** |
| Dispatch / proxy | backend HTTP(S) client, identity assertion | network (to backend) | **in** |
| KnoxSSO / KnoxToken services | `/knoxsso`, `/knoxtoken` endpoints | filesystem (keys), network | **in** |
| Credential / alias / keystore service | master secret, `__gateway-credentials.jceks`, per-topology stores | filesystem | **in (operator boundary — see §3)** |
| Admin API + Knox CLI | `/admin`, `bin/knoxcli.sh` | filesystem | **in (operator-authenticated)** |
| Topology / provider descriptors | XML in `conf/topologies/` | filesystem | **operator input, trusted — §3/§6** |
| Sample topologies, demo LDAP, examples | `sandbox.xml`, demo `ApacheDS`, gateway samples | filesystem, network | **OUT — §3** |

---

## §3 Out of scope (explicit non-goals)

- **The operator/administrator as an adversary.** Anyone who can write topology
  descriptors, provider configs, the master secret, or the keystore files
  already controls the gateway's trust decisions. A "vulnerability" that requires
  malicious operator config is not in the model *(maintainer, 2026-07-09 —
  L. McCay confirmed Q3: malicious operator config is never a Knox vuln)*.
- **The backend services Knox fronts, and the federated IdP.** Knox trusts
  authentication assertions from the configured IdP and trusts the backend to
  enforce its own authorization once Knox asserts identity. Compromise or
  misbehavior of those systems is out of layer *(maintainer, 2026-07-09 —
  L. McCay confirmed Q4)*.
- **The demo/sample surface.** The bundled **demo LDAP (ApacheDS)**, sample
  `sandbox` topology, gateway sample apps, and self-signed test certificate are
  explicitly for evaluation, not production. Findings that only manifest with the
  demo LDAP running, the sample topology deployed, or the self-signed cert in use
  are `OUT-OF-MODEL: unsupported-component` *(maintainer, 2026-07-09 — L. McCay
  confirmed Q5; the User's Guide markets these as getting-started aids,
  documented)*.
- **Securing an already-insecure cluster.** Knox is a **perimeter** control. It
  does not defend a backend that is directly network-reachable around the
  gateway; bypassing Knox by reaching the backend host directly is an operator
  network-segmentation responsibility, not a Knox flaw *(maintainer, 2026-07-09 —
  L. McCay confirmed Q6; note that clients able to authenticate directly to the
  backend — e.g. via Kerberos — are a legitimate deployment choice, and without
  explicit proxy-user config making them trusted proxies they cannot assert
  another user's identity via `doAs`)*.
- **Availability of the backend cluster.** Knox does not guarantee the liveness,
  correctness, or capacity of the services it proxies.
- **Not a WAF / not a general-purpose API security product.** Knox enforces
  authn/authz and hides topology; it is not claimed to be a payload-inspecting
  web application firewall for the backend's application-layer vulnerabilities
  *(inferred → Q7)*.

---

## §4 Trust boundaries and data flow

**The trust boundary is the gateway itself.** North of Knox: untrusted HTTP/WS
clients. South of Knox: trusted backend services. Knox's job is to be the gate
where an untrusted request is authenticated, authorized, and — only then —
re-emitted as a trusted request *(documented — homepage: "perimeter security";
User's Guide)*.

Request data flow (per request) *(inferred → Q8)*:

1. **TLS termination** at the gateway (Jetty). Client identity, if any, is a TLS
   client cert (HeaderPreAuth/pki) or is established later in the chain.
2. **Provider chain** runs in topology-defined order: authentication →
   identity-assertion → authorization → (rewrite) → dispatch. A request that
   fails authn/authz never reaches dispatch.
3. **Identity assertion** maps the authenticated principal (and groups) into the
   form the backend expects (e.g. `doAs`/proxy-user header, SPNEGO). **This is a
   trust-elevation point**: after this step the request carries Knox-minted
   identity that the backend trusts.
4. **Rewrite** translates external URLs/bodies to internal ones and back.
5. **Dispatch** opens a (possibly new-TLS) connection to the backend and proxies
   the request/response, streaming bodies.

**Reachability preconditions (the triager's first test)** *(inferred → Q8)*:

- A finding in the **request pipeline / rewrite / dispatch** is in-model only if
  reachable from **untrusted client bytes** (URL, headers, body, WS frames)
  *before* the authn provider has rejected the request, **or** from an
  authenticated-but-unauthorized-elsewhere client.
- A finding in the **auth provider chain** is in-model only if it lets an
  untrusted client **obtain, forge, or bypass** an identity assertion the
  backend will then trust (authentication bypass, assertion forgery, token
  forgery), or escalate authorization.
- A finding in **KnoxToken/KnoxSSO** is in-model only if a client can **mint,
  forge, replay, or fail-to-revoke** a token/cookie without the corresponding
  credential.
- A finding in the **credential/keystore/alias** family is in-model only if
  reachable **without** operator filesystem access (e.g. a network path that
  discloses a secret); anything requiring local FS access is the operator
  boundary (§3).
- A finding reachable only via **topology descriptor contents** is
  `OUT-OF-MODEL: trusted-input` (§6).

---

## §5 Assumptions about the environment

- **Host / runtime** *(inferred → Q9)*: a JVM on a server OS; embedded Jetty as
  the HTTP/WS container. Knox trusts the JVM's TLS stack and the OS filesystem
  permissions protecting `data/security/` and `conf/`.
- **Filesystem is the operator trust anchor** *(documented — User's Guide: master
  secret persisted to disk, credential stores under
  `data/security/keystores/`)*. Knox assumes only the operator (and the Knox
  process user) can read the master secret file, the credential stores, and the
  identity keystore. `-persist-master` explicitly trades a prompt for on-disk
  storage and "requires careful file permission management" *(documented)*.
- **The federated IdP is authoritative and honest** *(maintainer, 2026-07-09 —
  L. McCay confirmed Q4)*. When a SAML/OIDC/LDAP provider asserts "this is user
  X", Knox believes it. Knox assumes the IdP's assertions are integrity-protected
  in transit (signed SAML, validated OIDC ID-token, LDAPS) — the strength of that
  check is provider-config dependent (§5a). **TLS to the backend is assumed
  required** to protect the SPNEGO tokens Knox forwards on the dispatch hop
  *(maintainer, 2026-07-09 — L. McCay)*.
- **The backend trusts Knox's asserted identity** *(inferred → Q10)*. The whole
  proxy-user / identity-assertion mechanism assumes the backend will accept a
  Knox-asserted principal. This means **anything that lets a client control the
  asserted identity is an authentication bypass against every backend.**
- **Network segmentation** *(maintainer, 2026-07-09 — L. McCay confirmed Q6)*:
  Knox assumes clients cannot reach backends except through it. Knox does not
  enforce this; the operator's network does.
- **Concurrency** *(inferred → Q11)*: a Knox instance serves many concurrent
  clients; provider filters and the alias/keystore service are assumed
  thread-safe. Clustered Knox instances share the same gateway master secret
  *(documented — "the same across all gateway instances")*.
- **Side effects Knox has on its host** *(inferred → Q12)*: opens the gateway
  listen socket(s); opens outbound connections to backends and IdPs; reads/writes
  `conf/` and `data/security/`; writes audit + service logs; may spawn nothing
  beyond the JVM. This "no-surprise" inventory is almost entirely inferred and is
  a high-priority confirmation target. **Specific open item flagged by the PMC
  (2026-07-09 — L. McCay): verify whether the webshell terminal service spawns
  any process** — if it does, the "spawns nothing beyond the JVM" assumption
  needs qualification and the webshell surface warrants its own trust-boundary
  note.

---

## §5a Build-time and configuration variants (the security envelope is config-driven)

Unlike a library with compile flags, **Knox's security envelope is set almost
entirely by operator topology/gateway configuration**, not build flags. The
model describes a *correctly configured production* gateway; several knobs, at
their sample/default values, void §8 properties. For each, the PMC must rule
whether the less-secure value is a **supported posture** (report ⇒ `VALID`) or a
**dev-convenience the operator must flip** (report ⇒ `OUT-OF-MODEL:
non-default-build`). All rulings below are proposed, pending §14.

| Knob / mode | Less-secure setting | Proposed maintainer stance |
| --- | --- | --- |
| Demo LDAP + `sandbox` topology | shipped, trivially bypassable creds | dev-only; production must replace *(maintainer — confirmed Q5)* |
| Self-signed identity certificate | default if operator provides none | dev-only; production installs a CA cert *(→ Q13)* |
| `HeaderPreAuth` without an IP/mTLS trust check | trusts `SM_USER`/custom identity header from any client | **OUT-OF-MODEL — operator responsibility** *(maintainer, 2026-07-09 — L. McCay, Q14)*: the operator must avoid this or implicitly accepts the risk. Report ⇒ `OUT-OF-MODEL: non-default-build`. **Hardening (VALID-HARDENING):** Knox should default the trust gate so operators must *explicitly* accept the ungated risk. |
| Anonymous / `Anonymous` auth provider or no authz provider in a topology | request passes unauthenticated/unauthorized | operator-chosen posture for public / self-authenticating services; report ⇒ OUT-OF-MODEL unless a *different* topology's protection leaks *(maintainer, 2026-07-09 — L. McCay confirmed Q15: this is used for public/self-authenticating services and is OUT-OF-MODEL)* |
| TLS protocol/cipher and hostname-verification settings on the **backend dispatch hop** | plaintext or unverified backend connection | operator-configured; a plaintext/unverified default hop is a §11 misuse, not a Knox guarantee *(maintainer, 2026-07-09 — L. McCay confirmed Q16; TLS to backend is required to protect SPNEGO tokens)* |
| `-persist-master` | master secret stored on disk | supported with correct file perms *(documented)* *(→ Q17)* |
| Token TTL / renewal / server-managed token state | long-lived or non-revocable KnoxTokens | supported range TBD *(→ Q18)* |

---

## §6 Assumptions about inputs

Knox's inputs split cleanly into **untrusted network input** (the client) and
**trusted operator input** (the descriptors and secrets).

Per-surface trust table *(inferred → Q19 unless noted)*:

| Surface | Parameter | Attacker-controllable? | Operator/Knox must enforce |
| --- | --- | --- | --- |
| Any gateway route | request line / path | **yes** | rewrite must not allow path-traversal into other topologies/services |
| Any gateway route | request headers | **yes** | identity headers (`SM_USER`, `X-Forwarded-*`, forwarded-identity) must NOT be trusted unless a preauth trust gate is configured |
| Any gateway route | request body / WS frames | **yes** | streamed to backend; size/timeout limits are operator/Jetty config |
| Auth providers | credentials (LDAP bind, SPNEGO, OIDC code, JWT) | **yes** | validate signature/binding against the trusted IdP; no bypass |
| `/knoxtoken`, `/knoxsso` | token/cookie presented by client | **yes** | verify signature + expiry + (if applicable) server-side revocation |
| Dispatch | backend hostname/port | **no — from topology** | rewrite/service defs come from trusted operator config |
| Topology descriptors (`conf/topologies/*.xml`) | provider chain, ACLs, service URLs | **no — trusted operator** | authored by admin; not attacker input |
| Provider config / descriptors (`__gateway.xml`, shared provider configs) | all fields | **no — trusted operator** | — |
| Master secret / credential store / keystore | secrets, aliases | **no — operator, FS-protected** | OS file permissions |
| Admin API / Knox CLI | topology CRUD, alias ops | **operator-authenticated** | admin authz; not an anonymous surface |

Key line: **client-supplied identity headers and tokens are the crown-jewel
untrusted inputs.** The single most consequential Knox failure class is a client
getting Knox to assert an identity (or authorization) it did not legitimately
earn.

Size/shape/rate: Knox streams request/response bodies and WebSocket frames; body
size, connection, and timeout limits are Jetty/topology configuration rather than
a Knox-guaranteed bound *(inferred → Q20)*.

---

## §7 Adversary model

**Primary adversary — the untrusted perimeter client** *(inferred → Q21)*. An
anonymous or partially-authenticated HTTP/WebSocket peer that can reach the
gateway listen port. Capabilities:

- Send arbitrary request lines, headers (including spoofed identity/forwarded
  headers), bodies, and WS frames.
- Present forged or replayed credentials, cookies, SAML responses, OIDC tokens,
  and KnoxTokens/KnoxSSO cookies.
- Attempt to make Knox open a connection to an attacker-chosen or
  internal-only destination (SSRF-shaped), *to the extent rewrite/dispatch turn
  client input into a backend target*.
- Open many/slow connections (resource pressure).

**Secondary adversary — an authenticated-but-limited client** *(inferred →
Q22)*: a client with valid credentials for *some* topology/service who tries to
reach a service/topology they are not authorized for (authorization bypass,
cross-topology confusion, rewrite escape).

**Attacker goals in scope:** authentication bypass; identity/assertion forgery
(impersonate another user to the backend); authorization bypass; token forgery/
replay; disclosure of a secret from the credential store over the network;
topology/internal-address disclosure defeating the concealment goal; SSRF via the
proxy; crashing/hanging the gateway from unauthenticated input.

**Explicitly out of the adversary model** *(maintainer, 2026-07-09 — L. McCay confirmed Q3/Q4)*:

- The **operator** (writes topology/secrets — already fully trusted).
- The **backend service** and the **federated IdP** (trusted infrastructure).
- A **local process / co-tenant** on the Knox host with filesystem access to
  `data/security/` (that's the operator boundary).
- An attacker who has **already bypassed the gateway** at the network layer.

*(No Byzantine-participant / consensus actor applies — Knox is a proxy, not a
replicated consensus system. Clustered Knox instances share config and secret
and are mutually trusting, not adversarial to each other → Q11.)*

---

## §8 Security properties Knox provides

Each: property (+conditions) · violation symptom · severity · provenance.

1. **Perimeter authentication enforcement.** Given a correctly configured
   topology with an authentication provider, a request that does not satisfy that
   provider does not reach dispatch. · *Violation:* unauthenticated client
   reaches a protected backend / gets a response it should not. · *Security-
   critical (auth bypass).* · *(documented — perimeter-security purpose; enforcement invariant maintainer, 2026-07-09 — L. McCay confirmed Q23: a request failing authn/authz never reaches dispatch, no exceptions)*
2. **Authorization enforcement (AclsAuthz).** Given an authorization provider,
   only principals/groups/IPs permitted by the topology ACL reach the service.
   · *Violation:* authorized-for-A client reaches B. · *Security-critical.* ·
   *(documented — AclsAuthz; invariant maintainer, 2026-07-09 — L. McCay confirmed Q23, no exceptions)*
3. **Faithful identity assertion.** The identity asserted to the backend is the
   one Knox authenticated — a client cannot substitute another principal. ·
   *Violation:* client impersonates another user to the backend. ·
   *Security-critical (this is the keystone property).* · *(maintainer,
   2026-07-09 — L. McCay confirmed Q24: this is the keystone; any break is
   security-critical)*
4. **Token/SSO integrity.** KnoxToken/KnoxSSO tokens are integrity-protected
   (signed) and validated (signature + expiry) before acceptance; a client cannot
   forge or tamper one. · *Violation:* accepted forged/tampered/expired token. ·
   *Security-critical.* · *(documented — tokens issued/normalized; crypto
   invariant maintainer, 2026-07-09 — L. McCay confirmed Q18/Q25: signed,
   short-TTL, revocation-optional bearer tokens; replay within TTL is BY-DESIGN)*
5. **Secret confidentiality at rest.** Master-secret-encrypted credential stores
   protect provider/gateway secrets; secrets are not exposed over the network
   surface. · *Violation:* a network request discloses an alias/secret or the
   master secret. · *Security-critical.* · *(documented — master secret +
   credential store)*
6. **Topology/internal-address concealment.** Rewrite hides backend host/port
   from clients. · *Violation:* client obtains internal addresses or reaches an
   un-fronted internal endpoint via rewrite escape. · *Security-relevant (defeats
   a stated purpose; often medium).* · *(documented — "conceals cluster
   topology")*
7. **Transport confidentiality (client hop).** TLS terminates client traffic
   with the configured identity keystore. · *Violation:* downgrade/plaintext
   acceptance where TLS is configured. · *Security-critical.* · *(documented —
   identity keystore / TLS)*

**Resource properties:** Knox makes **no strong quantitative resource guarantee**
against unauthenticated load beyond what Jetty/topology limits provide; slow-loris
/ large-body / many-connection pressure is bounded by operator config, not a Knox
invariant *(maintainer, 2026-07-09 — L. McCay confirmed Q19/Q20)*. Confirmed
resource line: *"an unauthenticated request that hangs or crashes the gateway
process is a bug; merely consuming proportionate resources under load is not."*
*(maintainer, 2026-07-09 — L. McCay)*

---

## §9 Security properties Knox does NOT provide

- **No protection against the operator.** Malicious or mistaken topology/provider
  config, secrets, or keystore contents are not defended against *(maintainer,
  2026-07-09 — L. McCay confirmed Q3)*.
- **No backend authorization.** Once Knox asserts identity, the backend decides
  what that identity may do. Knox authz is coarse perimeter ACLs, not a
  replacement for backend/Ranger fine-grained authz *(maintainer, 2026-07-09 —
  L. McCay confirmed Q26)*.
- **No defense of an around-the-gateway path.** If a client can reach the backend
  without traversing Knox, Knox provides nothing *(maintainer, 2026-07-09 —
  L. McCay confirmed Q6)*.
- **No application-layer inspection of backend payloads** (not a WAF) *(inferred
  → Q7)*.
- **No guarantee for the demo/sample surface** (§3).

**False friends — call these out explicitly:**

- **A forwarded/preauth identity header is not authentication.** `HeaderPreAuth`
  (`SM_USER` etc.) *looks* like it authenticates the user, but it only trusts a
  header — it is secure **only** when paired with an mTLS/IP trust gate that
  proves the header came from a trusted SSO front-end. Without that gate any
  client sets the header and impersonates anyone. This is the most dangerous Knox
  false friend. **Disposition (maintainer, 2026-07-09 — L. McCay, Q14): an
  ungated `HeaderPreAuth` is `OUT-OF-MODEL: non-default-build`** — the operator
  must avoid it or implicitly accepts the risk; it is *not* reported as a Knox
  `VALID` bug. The paired hardening ask is that Knox default the gate so the
  operator has to explicitly accept the ungated posture (VALID-HARDENING).
- **KnoxSSO cookie / KnoxToken are bearer credentials, not proof of possession.**
  Anyone who captures one can replay it until expiry; TLS + short TTL + (where
  available) revocation are load-bearing, not decorative *(inferred → Q18/Q25)*.
- **"Topology concealment" is obscurity, not an access control.** Hiding backend
  addresses reduces attack surface but is not itself an authentication/authz
  boundary *(inferred)*.
- **TLS termination at Knox does not encrypt the backend hop.** Client→Knox TLS
  says nothing about Knox→backend; that hop's encryption/verification is separate
  config *(inferred → Q16)*.

**Well-known attack classes Knox as a reverse proxy is exposed to and the
operator must weigh** *(maintainer, 2026-07-09 — L. McCay answered Q27)*: HTTP
request smuggling / desync between Knox and backend; SSRF via rewrite/dispatch if
client input reaches the backend target; open-redirect via the SSO
`originalUrl`/return-URL parameter; XXE if any provider parses attacker-supplied
XML (e.g. SAML responses); host-header / X-Forwarded-* trust confusion; WebSocket
origin/authorization gaps; cookie scope/`Secure`/`HttpOnly` handling for KnoxSSO.

**Responsibility split (maintainer, 2026-07-09 — L. McCay):** all of these
protections are ultimately the **operator's responsibility**, but Knox provides
the mechanisms to configure them:

- The **`WebAppSecProvider`** can be configured for **XSRF, CORS, CSP, and
  related** cross-site protections.
- **Regexp-based whitelists** protect against **SSRF and open-redirect** (host /
  port / domain allow-lists).

These mechanisms **must be tuned to the deployment's specific expectations**
(allowed hosts, ports, domains). A report that a protection is *available but
unconfigured* is therefore `OUT-OF-MODEL` (operator responsibility); a report
that a configured `WebAppSecProvider` / whitelist can be *bypassed* is `VALID`.

---

## §10 Downstream responsibilities (the operator's contract)

For a gateway, "downstream user" = **the cluster operator/deployer.** To keep
§5–§8 valid, the operator must:

1. **Protect the master secret and keystores** with OS file permissions; treat
   `-persist-master` as security-sensitive *(documented)*.
2. **Replace all demo/sample material** before production: remove demo LDAP,
   sample `sandbox` topology, and the self-signed cert; install a real IdP and a
   CA-issued identity keystore *(documented as getting-started aids → Q5/Q13)*.
3. **Never deploy `HeaderPreAuth` without a trust gate** (mTLS/IP allow-list
   proving the header source) — an ungated deployment is the operator's risk to
   accept, not a Knox bug *(maintainer, 2026-07-09 — L. McCay, Q14)*.
4. **Enforce network segmentation** so backends are unreachable except through
   Knox *(maintainer, 2026-07-09 — L. McCay confirmed Q6)*.
5. **Configure the backend dispatch hop's TLS** (protocol, ciphers, hostname
   verification) to match the threat environment. **TLS is required together with
   Kerberos for proxy-user-based trusted proxies** (it protects the forwarded
   SPNEGO tokens) — the PMC asks that this be explicitly documented *(maintainer,
   2026-07-09 — L. McCay confirmed Q16)*.
6. **Set token TTLs / enable revocation** appropriate to data sensitivity
   *(maintainer, 2026-07-09 — L. McCay confirmed Q18)*.
7. **Scope authorization ACLs per service/topology**; do not rely on backend
   authz alone if perimeter authz is intended *(maintainer, 2026-07-09 —
   L. McCay confirmed Q26)*.
8. **Keep provider configs pointed at integrity-checked IdP channels** (signed
   SAML, validated OIDC, LDAPS) *(maintainer, 2026-07-09 — L. McCay confirmed
   Q4)*.
9. **Configure and tune `WebAppSecProvider` and the SSRF/open-redirect regexp
   whitelists** (XSRF/CORS/CSP; allowed hosts/ports/domains) for the deployment
   — Knox provides these mechanisms but the operator must turn them on and tune
   them *(maintainer, 2026-07-09 — L. McCay, Q27)*.

---

## §11 Known misuse patterns

- **Preauth header trusted without a gate** — deploying `HeaderPreAuth` and
  trusting `SM_USER`/custom identity headers directly from clients. *Looks* like
  SSO integration; is actually anonymous impersonation. Fix: require mTLS/IP trust
  gate. Per Q14 this is the operator's risk to accept (`OUT-OF-MODEL`), with a
  standing hardening ask that Knox default the gate to force explicit acceptance
  *(maintainer, 2026-07-09 — L. McCay)*.
- **Leaving demo LDAP / sample topology in production** — the sample creds are
  public. Fix: replace before exposure *(→ Q5)*.
- **Treating Knox authz as the only authz** — assuming perimeter ACLs remove the
  need for backend authorization, then exposing services with weak backend checks
  *(→ Q26)*.
- **Exposing the backend around Knox** — Knox on the edge but backends also
  directly reachable, so clients skip the gateway *(→ Q6)*.
- **Long-lived / non-revocable tokens** — issuing KnoxTokens with large TTLs and
  no revocation, so a captured token is a durable credential *(→ Q18)*.
- **Plaintext / unverified backend hop** — assuming client-side TLS covers the
  whole path *(→ Q16)*.

## §11a Known non-findings (recurring false positives)

Populated with the shapes a scanner/AI is likely to raise against Knox; each is
safe **given the model** and the PMC should confirm/extend *(all inferred →
Q28)*:

- **"Knox trusts the `SM_USER` header" flagged as auth bypass** — *not a finding*
  when the topology pairs `HeaderPreAuth` with an mTLS/IP trust gate (§6 header
  row, §9 false friend). An **ungated** `HeaderPreAuth` is `OUT-OF-MODEL:
  non-default-build` (operator responsibility per Q14), **not** a Knox `VALID`
  bug — though a proposal to harden the default gate is welcome
  *(maintainer, 2026-07-09 — L. McCay, Q14)*.
- **"Backend URL is taken from configuration" flagged as SSRF** — the dispatch
  target comes from the **trusted topology descriptor**, not client input (§6) ⇒
  `OUT-OF-MODEL: trusted-input`. `VALID` only if *client* bytes reach the
  destination selection.
- **"Self-signed certificate accepted"** — the bundled self-signed identity cert
  is a getting-started default (§3/§5a) ⇒ `OUT-OF-MODEL: unsupported-component /
  non-default-build`.
- **"Secret stored on disk (`-persist-master`, `.jceks`)"** — by design under the
  operator FS-trust boundary (§5) ⇒ `BY-DESIGN` / operator responsibility, not a
  network-reachable disclosure.
- **"Demo LDAP allows weak/anonymous bind"** — demo component, out of scope
  (§3) ⇒ `OUT-OF-MODEL: unsupported-component`.
- **"Verbose error / topology info in a response"** — only `VALID` if it defeats
  the §8(6) concealment property with real internal addresses; generic stack
  traces are `VALID-HARDENING` at most.

---

## §12 Conditions that would change this model

- A new authentication/authorization provider, or a change to how identity is
  asserted to backends.
- A new client-facing protocol surface (new WebSocket features, gRPC, HTTP/2/3
  specifics) or a new dispatch mechanism.
- Making a previously demo/sample component production-supported (or vice-versa).
- A change of default for any §5a knob (e.g. shipping a non-self-signed default,
  or gating `HeaderPreAuth` by default).
- Introducing multi-tenant isolation guarantees between topologies (currently
  treated as operator-partitioned, not a Knox-enforced boundary → Q29).
- **A report that cannot be routed to one §13 disposition** ⇒ the model has a gap;
  revise §8/§9 rather than making an ad-hoc call.

## §13 Triage dispositions

| Disposition | Meaning | Licensed by |
| --- | --- | --- |
| `VALID` | Untrusted client bypasses authn/authz, forges an identity assertion or token, discloses a secret over the network, or crashes/hangs the gateway from unauthenticated input. | §8, §6, §7 |
| `VALID-HARDENING` | No §8 property broken, but Knox elects to make a §11 misuse harder (e.g. safer default, extra warning, tighter header handling). Usually no CVE. | §11 |
| `OUT-OF-MODEL: trusted-input` | Requires attacker control of topology/provider config, a dispatch target from config, or another §6-trusted parameter. | §6 |
| `OUT-OF-MODEL: adversary-not-in-scope` | Requires operator, backend, IdP, local-FS, or around-the-gateway capability. | §7 |
| `OUT-OF-MODEL: unsupported-component` | Lands in demo LDAP, sample topologies, gateway samples, or self-signed test cert. | §3 |
| `OUT-OF-MODEL: non-default-build` | Only manifests under a discouraged §5a configuration the operator is documented to change. | §5a |
| `BY-DESIGN: property-disclaimed` | Concerns a §9 disclaimed property (backend authz, WAF inspection, bearer-token replay within TTL, operator secret-at-rest). | §9 |
| `KNOWN-NON-FINDING` | Matches a §11a recurring false positive. | §11a |
| `MODEL-GAP` | Routes to none of the above. | triggers §12 |

## §14 Open questions for the maintainers

Each states a **proposed answer** to confirm/correct, and the section it lands
in. Grouped in waves. **Larry McCay (PMC chair) answered Wave 1–3 core + meta on
2026-07-09; those answers are recorded here and folded into the sections above.**

**Wave 1 — scope, deployment, adversary (unblocks everything) — ANSWERED
2026-07-09:**

- **Q1 (§2): ✅ Confirmed.** Deployment shape is "a long-running Jetty-based edge
  daemon, administrator-operated; not an in-process library."
- **Q2 (§2): ✅ Confirmed.** The component-family split is right and the
  demo/sample surface is the only out-of-model code.
- **Q3 (§3/§7): ✅ Confirmed.** The operator is entirely out of the adversary
  model — malicious operator config is never a Knox vuln.
- **Q4 (§3/§5): ✅ Confirmed**, with the addition that **TLS to the backend is
  assumed required to protect SPNEGO tokens.**
- **Q5 (§3/§5a): ✅ Confirmed.** Demo LDAP, `sandbox`/sample topologies, and
  gateway samples are dev/eval only; findings requiring them are out of model.
- **Q6 (§3/§9): ✅ Confirmed.** Around-the-gateway backend reachability is an
  operator network responsibility. Note: clients that authenticate directly to
  the backend (e.g. Kerberos) are a legitimate deployment choice; without
  explicit proxy-user config making them trusted proxies they cannot assert
  another user's identity via `doAs`.

**Wave 2 — the keystone properties & false friends — ANSWERED 2026-07-09:**

- **Q14 (§5a/§9/§11): ✅ Answered — OUT-OF-MODEL.** An ungated `HeaderPreAuth` is
  a misconfiguration the operator must avoid or implicitly accepts; a report is
  `OUT-OF-MODEL: non-default-build`, **not** a Knox `VALID` bug. Paired hardening
  ask: Knox should default the trust gate so operators must explicitly accept the
  ungated risk (VALID-HARDENING). *(This corrects the v0 proposal of "VALID
  whenever ungated".)*
- **Q24 (§8): ✅ Confirmed.** Faithful identity assertion is the keystone
  property; any break is security-critical.
- **Q23 (§8): ✅ Confirmed — no exceptions.** A request failing authn/authz never
  reaches dispatch.
- **Q16 (§5a/§9): ✅ Confirmed.** Backend-hop TLS is operator-configured; a
  plaintext/unverified default hop is a §11 misuse, not a Knox guarantee. **TLS
  is required together with Kerberos for proxy-user-based trusted proxies, and
  this should be documented.**
- **Q18/Q25 (§5a/§8): ✅ Confirmed.** KnoxToken/KnoxSSO are signed, short-TTL,
  revocation-optional bearer tokens; replay within TTL is BY-DESIGN.

**Wave 3 — inputs, resources, isolation, false-positive suppression:**

- **Q19/Q20 (§6/§8): ✅ Confirmed 2026-07-09.** Per-surface trust table stands;
  resource line = "unauthenticated hang/crash = bug; proportionate load = not."
- **Q26 (§9): ✅ Confirmed 2026-07-09.** Knox perimeter authz does not replace
  backend/Ranger authz.
- **Q27 (§9): ✅ Answered 2026-07-09.** All these protections are the operator's
  responsibility, but Knox provides the mechanisms: `WebAppSecProvider`
  (configurable for XSRF/CORS/CSP) and regexp-based whitelists (SSRF /
  open-redirect). They must be tuned to the deployment's hosts/ports/domains.
- **Q29 (§12): ⬜ Open.** Is isolation **between topologies** a Knox-enforced
  boundary or merely operator partitioning? Proposed: operator partitioning, not
  a guaranteed isolation boundary — a cross-topology leak would be `VALID` only
  if it breaks §8(1/2).
- **Q28 (§11a): ⬜ Open.** Confirm/extend the known-non-findings list so it can
  seed a scanner suppression set.
- **Q7 (§3), Q9–Q13, Q17, Q21–Q22 (§5/§7/§5a): ⬜ Open.** Confirm the remaining
  environment, host side-effect inventory (**including whether the webshell
  terminal spawns a process — Q12**), self-signed/`-persist-master` stances, and
  secondary adversary-capability details as stated inline.

**Wave 4 — meta / ownership — ANSWERED 2026-07-09:**

- **Q-meta-A: ✅ Yes.** This model should become the canonical Knox security
  document, linked from a new `SECURITY.md` and the project site.
- **Q-meta-B: ✅ Yes.** The ratified model lives in `docs/` in-repo, versioned
  with releases; a §12 change class triggers a revision.

## §15 Optional: machine-readable companion

Not produced in v0. Once §14 wave 1–2 answers land, emit a `threat-model.yaml`
sidecar (entry surfaces → trust level from §6; component families in/out from
§2/§3; §5a config knobs; §8 properties with severity+symptom; §9 disclaimed +
false friends; §11a non-findings; §13 dispositions) for automated/Glasswing
triage.

---

### Appendix — SECURITY.md / website back-map

**Not applicable.** Knox publishes no project-specific `SECURITY.md` (repo 404)
and no dedicated security page; `security.apache.org/projects/` lists Knox only
with the generic Apache Security Team contact. There is therefore no prior
maintainer-authored security-policy artifact to back-map or superset. If the PMC
adopts this model (Q-meta-A), this section becomes the seed for a new
`SECURITY.md`.
