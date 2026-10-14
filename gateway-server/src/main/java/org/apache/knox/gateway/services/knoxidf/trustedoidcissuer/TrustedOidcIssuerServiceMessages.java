/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.services.knoxidf.trustedoidcissuer;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger = "org.apache.knox.gateway.knoxidf.trustedoidcissuer.service")
interface TrustedOidcIssuerServiceMessages {

  @Message(level = MessageLevel.ERROR,
      text = "Failed to fetch OIDC discovery document for issuer {0} from {1}: {2}")
  void errorFetchingDiscoveryDocument(String issuerUrl, String discoveryUrl, String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Failed to parse OIDC discovery document for issuer {0}: {1}")
  void errorParsingDiscoveryDocument(String issuerUrl, String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Error registering trusted OIDC issuer {0}: {1}")
  void errorRegisteringIssuer(String issuerUrl, String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Error deregistering trusted OIDC issuer {0}: {1}")
  void errorDeregisteringIssuer(String issuerUrl, String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "Error reloading trusted OIDC issuer registry snapshot: {0}")
  void errorReloadingRegistrySnapshot(String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.INFO,
      text = "Loaded CA bundle for trusted OIDC issuer {0} from {1} (SHA-256 fingerprint {2})")
  void loadedIssuerCa(String issuerUrl, String source, String fingerprint);

  @Message(level = MessageLevel.ERROR,
      text = "Failed to load CA bundle for trusted OIDC issuer {0} from {1}: {2}")
  void errorLoadingIssuerCa(String issuerUrl, String source, String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.WARN,
      text = "Failed to re-read CA bundle for trusted OIDC issuer {0} from {1}, keeping the previously loaded bundle: {2}")
  void keepingPreviousIssuerCa(String issuerUrl, String source, String cause);

  @Message(level = MessageLevel.INFO,
      text = "TLS trust failure fetching {1} for trusted OIDC issuer {0}; retrying with the re-read CA bundle")
  void retryingWithReloadedIssuerCa(String issuerUrl, String url);
  @Message(level = MessageLevel.WARN,
      text = "Background refresh of the trusted OIDC issuer registry snapshot failed; serving the cached snapshot "
          + "(age {0,number,#}s). If the database stays unreachable, the registry fails closed (no issuer is trusted) in {1,number,#}s: {2}")
  void errorRefreshingRegistrySnapshotServingStale(long ageSecs, long secsUntilFailClosed, String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
      text = "The trusted OIDC issuer registry snapshot expired and could not be reloaded from the database; "
          + "failing closed: no issuer is trusted until the database is reachable again (next reload attempt after {0,number,#}s): {1}")
  void registrySnapshotFailedClosed(long retrySecs, String cause,
      @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.DEBUG,
      text = "Trusted OIDC issuer registry is still failing closed; reload attempt failed: {0}")
  void registrySnapshotStillFailedClosed(String cause);

  @Message(level = MessageLevel.INFO,
      text = "Trusted OIDC issuer registry snapshot reloaded from the database after a failure; {0} issuer(s) trusted")
  void registrySnapshotRecovered(int issuerCount);

  @Message(level = MessageLevel.DEBUG,
      text = "Trusted OIDC issuer registry snapshot reloaded from the database; {0} issuer(s), {1} changed issuer(s) invalidated in the discovery and CA trust caches")
  void registrySnapshotReloaded(int issuerCount, int invalidatedDiscoveryEntries);

  @Message(level = MessageLevel.INFO,
      text = "Trusted OIDC issuer registry snapshot TTL is {0,number,#}ms (configured {1,number,#}s, minus per-node jitter); background refresh after {2,number,#}ms")
  void registrySnapshotTtl(long effectiveTtlMs, int configuredTtlSecs, long refreshMs);
}
