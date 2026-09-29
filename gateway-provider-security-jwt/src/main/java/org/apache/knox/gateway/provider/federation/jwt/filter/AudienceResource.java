/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.provider.federation.jwt.filter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.eclipse.jetty.util.URIUtil;

/**
 * The pieces of a single "aud" claim entry that carries a k8s-style destination URL:
 * {@code https://host[:port]/[skipped-segments/]namespace/service-name[/resource-path]}.
 *
 * <p>The optional {@code skipped-segments} prefix gives an {@code aud} entry more flexibility
 * in case it's needed for network routing. When {@link #parse(String, String)} is given a
 * non-blank {@code pathPrefix}, the path is searched, as a plain literal substring with no
 * decoding, for the first (leftmost) place {@code "/" + pathPrefix + "/"} occurs; namespace,
 * service-name and resource-path parsing then resumes right after it, at that shared "/".
 * Everything up to and including it is discarded unparsed: {@code pathPrefix} only locates
 * where the parse resumes, and is otherwise not validated or retained. {@code pathPrefix} is
 * ordinarily a single path segment, but it may itself contain "/" to require several contiguous
 * segments as one indivisible token. An entry whose path never contains it does not parse, the
 * same as any other ill-shaped entry. A {@code null} or blank {@code pathPrefix} disables the
 * search entirely: the path must begin with namespace and service-name straight after the
 * authority.
 *
 * <p>Host, namespace, and service-name are lower-cased at parse time, since all three are
 * compared case-insensitively against trusted-header-derived values; this keeps that comparison
 * a plain, symmetric {@code equals} wherever it happens, rather than an {@code equalsIgnoreCase}
 * that every comparison site has to remember to use. The resource path is compared
 * case-sensitively, so it is left exactly as it stood in the entry.
 *
 * <p>The resource path is kept in both of its comparison forms -- exactly as it stood in the
 * entry, and percent-encoded -- since a match against either form is acceptable. Namespace and
 * service-name are read once, from the raw path, with no encoded counterpart: a real destination's
 * namespace and service name are always valid DNS labels, which never contain a character an
 * encoder would touch, so parsing does not need to validate that they are -- a value that is not
 * a valid label simply will not match a real namespace or service name.
 *
 * <p>An aud entry for a delegation token is requested by the service that is about to call the
 * destination it describes, so a value that escapes some characters but not others within the same
 * resource path is out of scope: a path is treated as already encoded the moment it contains any
 * {@code %XY} escape, even if it also contains unencoded characters.
 */
public record AudienceResource(String host, int effectivePort, String namespace, String serviceName,
    String resourcePathRaw, String resourcePathEncoded) {

  private static final Pattern PERCENT_ENCODED_OCTET = Pattern.compile("%[0-9A-Fa-f]{2}");
  static final int DEFAULT_HTTPS_PORT = 443;

  /**
   * Parses a single {@code aud} claim entry as a k8s-style destination URL. Returns
   * {@link Optional#empty()} for any entry that is not a well-formed
   * {@code https://host[:port]/[skipped-segments/]namespace/service-name[/resource-path]} URL --
   * including a {@code null} or blank entry, a non-{@code https} scheme, a missing or
   * userinfo-carrying authority, a path that never contains a non-blank {@code pathPrefix},
   * fewer than two path segments once past any such prefix, or a resource path that is not
   * already in RFC 3986 remove_dot_segments normal form -- rather than throwing.
   *
   * <p>See the class javadoc for exactly how {@code pathPrefix} is searched for and consumed.
   *
   * <p>A {@code ?query} or {@code #fragment} on the entry is discarded before the path is parsed,
   * so it plays no part in {@code pathPrefix} matching or in the resulting resource path.
   */
  public static Optional<AudienceResource> parse(String audEntry, String pathPrefix) {
    if (audEntry == null) {
      return Optional.empty();
    }
    final String entry = audEntry.trim();
    if (entry.isEmpty()) {
      return Optional.empty();
    }

    final int schemeSep = entry.indexOf("://");
    if (schemeSep <= 0 || !"https".equalsIgnoreCase(entry.substring(0, schemeSep))) {
      return Optional.empty();
    }

    final int pathStart = entry.indexOf('/', schemeSep + 3);
    if (pathStart < 0) {
      return Optional.empty();
    }
    final String authorityPart = entry.substring(0, pathStart);
    String rawPath = entry.substring(pathStart);
    final int queryOrFragment = indexOfFirst(rawPath, 0, '?', '#');
    if (queryOrFragment >= 0) {
      rawPath = rawPath.substring(0, queryOrFragment);
    }

    // authorityPart comes from entry.substring(...), which never returns null, so
    // URISyntaxException is the only exception new URI(String) can throw here.
    final URI uri;
    try {
      uri = new URI(authorityPart);
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
    if (uri.getUserInfo() != null || uri.getHost() == null) {
      return Optional.empty();
    }
    final int explicitPort = uri.getPort();
    final int effectivePort = explicitPort < 0 ? DEFAULT_HTTPS_PORT : explicitPort;

    if (pathPrefix != null && !pathPrefix.isBlank()) {
      final String needle = "/" + pathPrefix + "/";
      final int found = rawPath.indexOf(needle);
      if (found < 0) {
        return Optional.empty();
      }
      rawPath = rawPath.substring(found + needle.length() - 1);
    }

    final Optional<ResourceShape> shape = parseShape(rawPath);
    if (shape.isEmpty()) {
      return Optional.empty();
    }
    final ResourceShape resource = shape.get();
    final String resourcePathRaw = resource.resourcePath();
    final String resourcePathEncoded = PERCENT_ENCODED_OCTET.matcher(resourcePathRaw).find()
        ? resourcePathRaw
        : URIUtil.encodePath(resourcePathRaw);

    return Optional.of(new AudienceResource(uri.getHost().toLowerCase(Locale.ROOT), effectivePort,
        resource.namespace(), resource.serviceName(), resourcePathRaw, resourcePathEncoded));
  }

  private record ResourceShape(String namespace, String serviceName, String resourcePath) {
  }

  private static Optional<ResourceShape> parseShape(String rawPath) {
    final String[] segments = rawPath.substring(1).split("/", -1);
    if (segments.length < 2) {
      return Optional.empty();
    }
    final String namespace = segments[0].toLowerCase(Locale.ROOT);
    final String serviceName = segments[1].toLowerCase(Locale.ROOT);
    final StringBuilder remainder = new StringBuilder();
    for (int i = 2; i < segments.length; i++) {
      remainder.append('/').append(segments[i]);
    }
    final Optional<String> tidied = normalizeAndTidy(remainder.toString());
    if (tidied.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new ResourceShape(namespace, serviceName, tidied.get()));
  }

  /**
   * Requires the path to already be in RFC 3986 remove_dot_segments normal form and to contain no
   * empty segment, rejecting it otherwise rather than normalizing it; the only change made is
   * giving the path exactly one leading "/" and removing a trailing "/" that is not the root path
   * itself, since a difference in trailing slash alone must not be treated as a mismatch.
   */
  static Optional<String> normalizeAndTidy(String rawPath) {
    if (rawPath == null) {
      return Optional.empty();
    }
    final String path = rawPath.startsWith("/") ? rawPath : "/" + rawPath;
    final String rest = path.substring(1);
    if (rest.isEmpty()) {
      return Optional.of("/");
    }
    final String[] segments = rest.split("/", -1);
    int segmentCount = segments.length;
    if (segments[segmentCount - 1].isEmpty()) {
      segmentCount--;
    }
    final StringBuilder tidied = new StringBuilder();
    for (int i = 0; i < segmentCount; i++) {
      final String segment = segments[i];
      if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
        return Optional.empty();
      }
      tidied.append('/').append(segment);
    }
    return Optional.of(tidied.toString());
  }

  /**
   * The first index at or after {@code fromIndex} where either {@code a} or {@code b} occurs, or
   * {@code -1} if neither does. Shared by {@link K8sDestinationAudienceValidator} for stripping a
   * query string or fragment off of a request path taken from a header.
   */
  static int indexOfFirst(String value, int fromIndex, char a, char b) {
    final int idxA = value.indexOf(a, fromIndex);
    final int idxB = value.indexOf(b, fromIndex);
    if (idxA < 0) {
      return idxB;
    }
    if (idxB < 0) {
      return idxA;
    }
    return Math.min(idxA, idxB);
  }
}
