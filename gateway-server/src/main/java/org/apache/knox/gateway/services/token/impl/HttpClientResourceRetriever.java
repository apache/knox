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
package org.apache.knox.gateway.services.token.impl;

import com.nimbusds.jose.util.Resource;
import com.nimbusds.jose.util.ResourceRetriever;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import java.util.Locale;

/**
 * Fetches a JWKS with Apache {@link CloseableHttpClient} instead of Nimbus's default
 * {@code DefaultResourceRetriever}.
 * <p>
 * The difference that matters is TLS endpoint identification. Nimbus's default retriever uses
 * {@link java.net.HttpURLConnection}, which creates its socket with the no-argument
 * {@code createSocket()} and then connects it by address - the socket never learns the host. Some
 * JSSE providers take the hostname for HTTPS endpoint identification from the socket rather than
 * from the URL, and BouncyCastle's FIPS provider is one: with no hostname it fails the handshake
 * with "No hostname specified for HTTPS endpoint ID check" before the certificate is examined,
 * even when the certificate carries a SAN matching the endpoint.
 * <p>
 * Apache HttpClient instead layers TLS over an already-connected socket and hands it the target
 * host, so the provider has a hostname to check. That makes JWKS retrieval work under any JSSE
 * provider without provider-specific handling, and it is the same mechanism that already lets OIDC
 * discovery succeed under FIPS while the JWKS fetch did not.
 * <p>
 * Verification is unchanged and still performed: the certificate must carry a subjectAltName
 * matching the host, HttpClient's own hostname verification still runs, and nothing here relaxes
 * trust. Timeouts, retries and connection reuse come from the supplied client.
 */
class HttpClientResourceRetriever implements ResourceRetriever {

  /* Identifies the fetch like the sibling discovery request does, for IdPs that filter on it. */
  private static final String USER_AGENT = "Apache-Knox-JWKS/1.0";

  private static final int SC_HIGHEST_SUCCESS = 299;
  private static final int DEFAULT_BUFFER_SIZE = 4096;
  private static final int MAX_INITIAL_BUFFER_SIZE = 65536;

  private final Supplier<CloseableHttpClient> httpClient;
  private final int sizeLimitBytes;

  /**
   * @param httpClient supplies the client to fetch with, resolved per retrieval so a caller can
   *                   build it lazily; not closed by this retriever. Any
   *                   {@link IllegalStateException} it raises - a service shutting down, a closed
   *                   pool - is converted to {@link IOException}, because every layer around this
   *                   one recovers from checked failures only.
   * @param sizeLimitBytes the most of a response body that will be read, or zero or less for no
   *                       limit, matching how Nimbus's own retriever reads it
   */
  HttpClientResourceRetriever(Supplier<CloseableHttpClient> httpClient, int sizeLimitBytes) {
    this.httpClient = httpClient;
    this.sizeLimitBytes = sizeLimitBytes;
  }

  HttpClientResourceRetriever(CloseableHttpClient httpClient, int sizeLimitBytes) {
    this(() -> httpClient, sizeLimitBytes);
  }

  @Override
  public Resource retrieveResource(URL url) throws IOException {
    final HttpGet request = get(url);
    final CloseableHttpClient client;
    try {
      client = httpClient.get();
    } catch (IllegalStateException e) {
      throw new IOException("Couldn't retrieve JWK set from URL " + url + ": " + e.getMessage(), e);
    }
    try (CloseableHttpResponse response = client.execute(request)) {
      final int status = response.getStatusLine().getStatusCode();
      /* Nimbus's retriever accepted any 2xx; narrowing that would reject e.g. a 203 from a proxy. */
      if (status < HttpStatus.SC_OK || status > SC_HIGHEST_SUCCESS) {
        throw new IOException(String.format(Locale.ROOT,
            "Couldn't retrieve JWK set from URL %s: HTTP %d", url, status));
      }
      final HttpEntity entity = response.getEntity();
      if (entity == null) {
        throw new IOException("Couldn't retrieve JWK set from URL " + url + ": empty response");
      }
      final String content = read(entity, url);
      /*
       * Lenient: ContentType.get throws unchecked ParseException / UnsupportedCharsetException on
       * a malformed header, which would escape every IOException-based recovery around this call.
       */
      final ContentType contentType = ContentType.getLenient(entity);
      return new Resource(content, contentType == null ? null : contentType.toString());
    }
  }

  /*
   * Bounded so a hostile or misconfigured endpoint cannot exhaust the heap: Content-Length is
   * advisory, so the body is read a chunk at a time and the limit enforced on what actually
   * arrives.
   */
  private String read(HttpEntity entity, URL url) throws IOException {
    try (InputStream content = entity.getContent()) {
      final ByteArrayOutputStream body = new ByteArrayOutputStream(initialBufferSize(entity));
      final byte[] chunk = new byte[DEFAULT_BUFFER_SIZE];
      int read;
      while ((read = content.read(chunk)) != -1) {
        /* A limit of zero or less means unlimited, matching Nimbus's own reading of it. */
        if (sizeLimitBytes > 0 && body.size() + read > sizeLimitBytes) {
          throw new IOException(String.format(Locale.ROOT,
              "Couldn't retrieve JWK set from URL %s: exceeded the %d byte limit", url, sizeLimitBytes));
        }
        body.write(chunk, 0, read);
      }
      return new String(body.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  /* Content-Length is advisory, so it only sizes the buffer; the limit above is what is enforced. */
  private int initialBufferSize(HttpEntity entity) {
    final long advertised = entity.getContentLength();
    if (advertised <= 0) {
      return DEFAULT_BUFFER_SIZE;
    }
    final long bounded = sizeLimitBytes > 0 ? Math.min(advertised, sizeLimitBytes) : advertised;
    return (int) Math.min(bounded, MAX_INITIAL_BUFFER_SIZE);
  }

  private static HttpGet get(URL url) throws IOException {
    try {
      /*
       * Built from the URL's parts rather than url.toURI() or the raw string. java.net.URL
       * tolerates characters java.net.URI rejects - a space, '|', '{', a raw non-ASCII byte - and
       * a jwks_uri taken verbatim from a discovery document may contain one. The multi-argument
       * URI constructor quotes them, where toURI() would throw and HttpGet(String) would throw an
       * unchecked IllegalArgumentException, either of which turns a URL that worked through
       * HttpURLConnection into a hard retrieval failure.
       */
      final URI uri = new URI(url.getProtocol(), url.getAuthority(), url.getPath(),
          url.getQuery(), url.getRef());
      final HttpGet request = new HttpGet(uri);
      request.setHeader(HttpHeaders.USER_AGENT, USER_AGENT);
      return request;
    } catch (URISyntaxException | IllegalArgumentException e) {
      throw new IOException("Couldn't retrieve JWK set from URL " + url + ": " + e.getMessage(), e);
    }
  }
}
