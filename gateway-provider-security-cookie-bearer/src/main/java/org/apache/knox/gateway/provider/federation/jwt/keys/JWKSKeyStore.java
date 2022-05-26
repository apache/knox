/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.provider.federation.jwt.keys;

import com.auth0.jwk.InvalidPublicKeyException;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwk.NetworkException;
import com.auth0.jwk.RateLimitReachedException;
import com.auth0.jwk.SigningKeyNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * A {@link KeyStore} that retrieves and caches keys from a JWKS provider.
 *
 * <p>JWKS (JSON Web Key Set) is a standard that allows a client to retrieve the
 * public key(s) that a token issuer uses to sign a JWT (JSON Web Token).  This
 * allows a client to authenticate tokens that are issued by an external service.
 * With this mechanism, the token issuer can rotate the public signing keys with
 * minimal impact to the client authenticating tokens.
 */
public class JWKSKeyStore implements KeyStore {
    private static final Logger log = LoggerFactory.getLogger(JWKSKeyStore.class);

    private static final String HTTP_PROXY_ENV = "HTTP_PROXY";
    private static final String HTTPS_PROXY_ENV = "HTTPS_PROXY";
    private static final String NO_PROXY = "NO_PROXY";

    private final JwkProvider jwkProvider;
    private final String providerURL;

    private EnvVarGetter envGetter = new EnvVarGetter();

    /**
     * Retrieves public signing keys from a JWK endpoint.
     * @param providerURL The URL of the JWK provider.
     */
    public JWKSKeyStore(URL providerURL) {
        this.providerURL = providerURL.toExternalForm();
        this.jwkProvider = new JwkProviderBuilder(providerURL)
                .cached(true)                      // by default, caches 5 keys for up to 10 hours
                .rateLimited(true)                 // by default, can fetch up to 10 keys per minute
                .proxied(getProxyFor(providerURL)) // get DIRECT or HTTP proxy according to the env
                .build();
        log.info("Will retrieve JWT signing keys from {}", providerURL.toExternalForm());
    }

    /**
     * Allows tests to provide a mock.
     * @param jwkProvider The mock provider.
     */
    public JWKSKeyStore(JwkProvider jwkProvider, EnvVarGetter envGetter) {
        this.jwkProvider = jwkProvider;
        this.providerURL = "";
        if (null != envGetter) {
            this.envGetter = envGetter;
        }
    }

    @Override
    public RSAPublicKey getPublic(String keyId) throws KeyStoreException, KeyNotFoundException {
        log.debug("Looking for signing key where kid = {}", keyId);
        try {
            Jwk jwk = jwkProvider.get(keyId);
            PublicKey key = jwk.getPublicKey();
            if (!RSAPublicKey.class.isInstance(key)) {
                throw invalidPublicKey(key);
            }
            return RSAPublicKey.class.cast(key);

        } catch (InvalidPublicKeyException e) {
            throw invalidPublicKey(e);

        } catch (NetworkException e) {
            throw networkError(e);

        } catch (RateLimitReachedException e) {
            throw rateLimitReached(e);

        } catch (SigningKeyNotFoundException e) {
            // a work-around for a bug in jwks-rsa-java that prevents us from correctly identifying network errors
            Throwable parent = e.getCause();
            if (parent != null) {
                Throwable grandparent = parent.getCause();
                if (grandparent instanceof NetworkException) {
                    throw networkError((NetworkException) grandparent);
                }
            }
            throw keyNotFound(keyId, e);

        } catch(JwkException e) {
            throw unexpectedError(e);
        }
    }

    public String getProviderURL() {
        return providerURL;
    }

    private KeyNotFoundException keyNotFound(String keyId, SigningKeyNotFoundException cause) {
        KeyNotFoundException e = new KeyNotFoundException(keyId);
        log.error(cause.getMessage(), cause);
        return e;
    }

    private KeyStoreException networkError(NetworkException e) {
        String msg = String.format(Locale.ROOT,"Network error prevented signing key retrieval from %s.", providerURL);
        log.error(msg, e);
        return new KeyStoreException(msg, e);
    }

    private KeyStoreException invalidPublicKey(PublicKey key) {
        String msg = String.format(Locale.ROOT,"Expected an RSA signing key, but got %s.", key.getAlgorithm());
        log.error(msg);
        return new KeyStoreException(msg);
    }

    private KeyStoreException invalidPublicKey(InvalidPublicKeyException e) {
        String msg = "Expected an RSA signing key, but got something else.";
        log.error(msg, e);
        return new KeyStoreException(msg, e);
    }

    private KeyStoreException rateLimitReached(RateLimitReachedException e) {
        long waitSeconds = TimeUnit.MILLISECONDS.toSeconds(e.getAvailableIn());
        String msg = String.format(Locale.ROOT,"Rate limit reached. Must wait %s second(s).", waitSeconds);
        log.error(msg, e);
        return new KeyStoreException(msg, e);
    }

    private KeyStoreException unexpectedError(Exception cause) {
        String msg = String.format(Locale.ROOT,"Failed to retrieve signing keys from %s.", providerURL);
        log.error(msg, cause);
        return new KeyStoreException(cause);
    }

    Proxy getProxyFor(URL url) {
        // By default do not use a proxy
        Proxy proxy = Proxy.NO_PROXY;

        // Non-proxy suffix patterns
        List<String> nonProxyPatterns = new ArrayList<>();
        String nonProxyValue = getEnv(NO_PROXY);
        if (nonProxyValue != null) {
            for (String hostSuffix : nonProxyValue.split(",")) {
                if (!hostSuffix.trim().isEmpty()) {
                    nonProxyPatterns.add(hostSuffix.trim());
                }
            }
        }

        // Retrieve the appropriate proxy environment variable for the target URL
        String proxyValue = null;
        if ("http".equals(url.getProtocol())) {
            log.debug("Looking for proxy configuration in {}", HTTP_PROXY_ENV);
            proxyValue = getEnv(HTTP_PROXY_ENV);
        } else if ("https".equals(url.getProtocol())) {
            log.debug("Looking for proxy configuration in {}", HTTPS_PROXY_ENV);
            proxyValue = getEnv(HTTPS_PROXY_ENV);
        } else {
            log.warn("Non-HTTP protocol for URL {}, using direct connection", url);
            return proxy;
        }

        // Return directly if no proxy environment variables set
        if (proxyValue == null) {
            log.info("No proxy environment variables set, using direct HTTP(S) connections");
            return proxy;
        }

        // Now check if we should ignore the proxy for this host
        for (String nonProxySuffix : nonProxyPatterns) {
            // Future TODO support CIDR patterns, e.g. 10.100.0.0/16
            if (url.getHost().endsWith(nonProxySuffix)) {
                log.info("Specified URL host {} matches non-proxy host pattern {},"
                        + " using direct HTTP(S) connections", url.getHost(), nonProxySuffix);
                return proxy;
            }
        }

        // Configure the proxy according to the environment settings
        try {
            URL proxyUrl = new URL(proxyValue);
            String proxyHost = proxyUrl.getHost();
            int proxyPort = proxyUrl.getPort();
            if (proxyPort < 0) {
                proxyPort = proxyUrl.getProtocol().equals("http") ? 80 : 443;
            }
            proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
        } catch (MalformedURLException e) {
            log.warn("Invalid HTTP proxy format. Expected 'http[s]://host[:port]', found: '{}'. "
                    + "Will proceed with direct HTTP(S) connections", proxyValue);
        }

        log.info("Using proxy: {}", proxy);
        return proxy;
    }

    private String getEnv(String key) {
        String value = null;
        if (this.envGetter.get(key) != null) {
            value = this.envGetter.get(key);
        } else if (this.envGetter.get(key.toUpperCase(Locale.ROOT)) != null) {
            value = this.envGetter.get(key.toUpperCase(Locale.ROOT));
        } else if (this.envGetter.get(key.toLowerCase(Locale.ROOT)) != null) {
            value = this.envGetter.get(key.toLowerCase(Locale.ROOT));
        }
        return value;
    }
}
