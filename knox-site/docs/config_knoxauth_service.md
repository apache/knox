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
<!---
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
## Knox Auth Service

### Introduction
With workloads moving to containers, it was necessary that Knox supports new ways of authentication needs of containers. As part of this effort, the Knox team developed a new internal service, called `KNOX-AUTH-SERVICE`. This service gathers a collection of public REST API endpoints that allows other developers to integrate Knox in their microservice/DEVOPS architectures using containers (such as docker or k9s).

### Configuration

This service can be added to any Knox topology as an internal service as follows:

    <service>
         <role>KNOX-AUTH-SERVICE</role>
         <param>
           <name>preauth.auth.header.actor.id.name</name>
           <value>X-Knox-Actor-ID</value>
         </param>
         <param>
           <name>preauth.auth.header.actor.groups.prefix</name>
           <value>X-Knox-Actor-Groups</value>
         </param>
         <param>
           <name>preauth.group.filter.pattern</name>
           <value>.*</value>
         </param>
         <param>
           <name>auth.bearer.token.env</name>
           <value>BEARER_AUTH_TOKEN</value>
         </param>
    </service>


### Available REST API endpoints

#### auth/api/v1/pre

This REST API endpoint has a very simple job: if a valid principal is found in the incoming request, a header is added to the response (by default `X-Knox-Actor-ID`) with the principal name. In addition, if the authenticated subject has group(s), it (they) will be added as comma-separated entries in the header(s) of the default form of `X-Knox-Actor-Groups-#num`. Each group header has a character limit of 1000 to keep them reasonably sized. The header names can be customized via the `preauth.auth.header.actor.id.name` and `preauth.auth.header.actor.groups.prefix` service parameters.

End users may filter user groups by setting the `preauth.group.filter.pattern` service parameter to a valid regular expression. By default, all the user gropus are added into the `X-Knox-Actor-Groups-#num` header.

##### Forwarding the caller's JWT

Some downstream services need the caller's token itself rather than just the resolved user name - to re-validate it, to read its scopes, or to call a further service on the user's behalf. Setting the `preauth.auth.header.auth.token.name` service parameter makes this endpoint return the caller's token in the header of that name, for example `X-Knox-Auth-Token`. The caller (typically an Envoy `ext_authz` or nginx `auth_request` filter) then copies that header onto the request it forwards downstream.

This parameter is opt-in and has no default: with it unset, no token is ever emitted and no response of this endpoint changes. (Two things do change regardless of the parameter, neither of them visible in the response: a JWT-authenticated request's `Subject` now carries the token as a private credential, and a header name configured to something illegal or protocol-reserved is now rejected with a `WARN` instead of being written blindly - see the header-name note below.) Because forwarding a credential is a deliberate choice, it is not part of the sample `<service>` block above - add it only where you mean to:

    <param>
      <name>preauth.auth.header.auth.token.name</name>
      <value>X-Knox-Auth-Token</value>
    </param>
    <param>
      <!-- optional; 6144 is the default -->
      <name>preauth.auth.header.auth.token.size.limit</name>
      <value>6144</value>
    </param>

Points to note:

* The value is the **bare serialized JWT**, with no `Bearer ` prefix. Add whatever scheme prefix the downstream service expects on the proxy side. The value is checked to be a well-formed JWS compact serialization before it is written; anything else is omitted with a `WARN`, so no attacker-influenced bytes can reach a response header.
* Only a JWT the caller presented as *this request's own credential* is forwarded - a `Bearer` (or JWT-as-`Basic`) token in the `Authorization` header, the configured token query parameter, or a KnoxSSO cookie. A JWT that arrives as a grant-flow parameter instead is deliberately not forwarded: a `refresh_token`, a `client_assertion` or an RFC 8693 `subject_token` is presented in order to mint a new token, not to authenticate the request, so echoing it downstream would leak a longer-lived credential than the caller used. Passcode tokens and token-id/client-credential identities carry no JWT at all. In every one of these cases the header is simply omitted; the request still authenticates and still returns the actor headers.
* Enabling this widens the token's blast radius: anything on the path downstream of the proxy can now see and replay a credential that was previously consumed at the gateway. Only turn it on toward backends you trust, over TLS.
* A KnoxSSO cookie typically lives far longer than a bearer token minted for a single call, so forwarding one widens the replay window as well as the blast radius. This endpoint is meant to be called by a proxy, not by a browser; keep it that way, and prefer `JWTProvider` over a browser-facing federation provider on a topology where this parameter is enabled.
* Under impersonation or identity assertion the two headers deliberately disagree: `X-Knox-Actor-ID` carries the *asserted* identity the request runs as, while the forwarded token's `sub` claim still names the *original* caller, since the token is the credential that was actually presented. A downstream service that re-validates the token must decide which of the two it treats as authoritative, and should not assume they match.
* A response carrying a token is sent with `Cache-Control: no-store`, per RFC 6749 section 5.1, so that nothing between this service and the proxy caches it. Responses that carry no token are unaffected.
* A JWT of several KB can exceed the header limits along the path - Knox's own Jetty response buffer (`gateway.httpserver.responseHeaderBuffer`, 8 KB by default) and the proxy in front of it (Envoy and nginx commonly cap headers at around 4-8 KB) - which would truncate or reject the whole response, costing the caller the actor headers as well. To keep that from happening silently, a token longer than `preauth.auth.header.auth.token.size.limit` is **omitted** with a `WARN` in `gateway.log` rather than emitted; the response is still a 200 and still carries the actor headers. The limit is 6144 by default and applies to this one header, not to the whole response - the group headers are unbounded unless you also set `preauth.auth.header.groups.size.limit` - so raise it alongside `gateway.httpserver.responseHeaderBuffer` and the proxy's `max_request_headers_kb` (Envoy) or `large_client_header_buffers` (nginx) when larger tokens must get through. Any value of `0` or less turns the check off, and a value that is not a number is ignored with a `WARN` in favour of the default.
* The header name must not collide with the actor id or actor groups header names. If it does, the token header is dropped with a `WARN` so that the identity headers this service exists to assert are never overwritten. The same applies to a name that is not a legal HTTP field name (RFC 9110 section 5.1), or one whose meaning belongs to the protocol or the container: `Set-Cookie`, the response-framing headers (`Content-Length`, `Transfer-Encoding`, `Content-Type`, `Content-Encoding`), `Cache-Control` (which this endpoint sets itself when it emits a token), `Location`, `WWW-Authenticate`, `Date` and the hop-by-hop set (`Connection`, `Upgrade`, `TE`, `Trailer`, `Keep-Alive`, `Proxy-Authenticate`, `Proxy-Authorization`). The same validation now applies to `preauth.auth.header.actor.id.name`, `preauth.auth.header.actor.groups` and `preauth.auth.header.actor.groups.prefix`: an unusable name there falls back to the default (or, for the optional explicit groups header, is ignored) with a `WARN`, rather than writing a user name into the response framing. Each such misconfiguration is logged once per gateway process, not once per request.
* Topologies that federate with the `HadoopAuth` provider do not populate this header, even with `support.jwt=true`. The reason is a detail of how that provider is wired rather than anything about the token: `HadoopAuthDeploymentContributor` installs both `HadoopAuthFilter` and `HadoopAuthPostFilter` in the same role, and while the former delegates a JWT-bearing request to the capturing `JWTFederationFilter`, the latter then re-derives its own `Subject` and wraps the rest of the chain in a second `Subject.doAs` - the innermost one wins, so the captured token is shadowed before it reaches this endpoint. Use the `JWTProvider` or `SSOCookieProvider` federation providers where the header is needed.

Sample `curl` commands are available in this [GitHub Pull Request](https://github.com/apache/knox/pull/625).

#### auth/api/v1/bearer

This REST API enpoint populates the HTTP "Authorization" header with the `Bearer Token` in the HTTP response object obtained from an environment variable.  The current implementation assumes that the token is not rotated as it never gets exposed to the end-user. By default, the `BEARER_AUTH_TOKEN` environment variable is expected to hold the Bearer token. This can be customized by configuring the `auth.bearer.token.env` service parameter to the desired value.

Sample `curl` commands are available in this [GitHub Pull Request](https://github.com/apache/knox/pull/627).

