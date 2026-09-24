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
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The pieces of a single "aud" claim entry that carries a k8s-style destination URL:
 * {@code https://host[:port]/namespace/service-name[/resource-path]}.
 *
 * <p>The resource path is kept in both of its comparison forms -- exactly as it stood in the
 * entry, and with any character illegal in a URI path percent-encoded, leaving "%" and "/"
 * untouched -- since a match against either form is acceptable and the two forms are otherwise
 * indistinguishable once only one is kept.
 */
public record AudienceResource(String host, int effectivePort, String namespace, String serviceName,
    String resourcePathRaw, String resourcePathEncoded) {

  private static final Pattern DNS_LABEL_PATTERN = Pattern.compile("[A-Za-z0-9]([-A-Za-z0-9]*[A-Za-z0-9])?");
  private static final int DNS_LABEL_MAX_LENGTH = 63;
  private static final int DEFAULT_HTTPS_PORT = 443;
  private static final String UNRESERVED_MARKS = "-._~";
  private static final String SUB_DELIMS = "!$&'()*+,;=";

  public static Optional<AudienceResource> parse(String audEntry) {
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
    final String authorityPart = entry.substring(0, pathStart < 0 ? entry.length() : pathStart);
    final String rawPath = pathStart < 0 ? "" : entry.substring(pathStart);
    if (rawPath.isEmpty()) {
      return Optional.empty();
    }

    final URI uri;
    try {
      uri = new URI(minimalEncode(authorityPart));
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
    if (uri.getUserInfo() != null || uri.getHost() == null) {
      return Optional.empty();
    }
    final int explicitPort = uri.getPort();
    final int effectivePort = explicitPort < 0 ? DEFAULT_HTTPS_PORT : explicitPort;

    final Optional<ResourceShape> rawShape = parseShape(rawPath);
    if (rawShape.isEmpty()) {
      return Optional.empty();
    }
    final ResourceShape raw = rawShape.get();

    final String encodedPath = minimalEncode(rawPath);
    final String resourcePathEncoded;
    if (encodedPath.equals(rawPath)) {
      resourcePathEncoded = raw.resourcePath();
    } else {
      final Optional<ResourceShape> encodedShape = parseShape(encodedPath);
      if (encodedShape.isEmpty()) {
        return Optional.empty();
      }
      resourcePathEncoded = encodedShape.get().resourcePath();
    }

    return Optional.of(new AudienceResource(uri.getHost(), effectivePort, raw.namespace(), raw.serviceName(),
        raw.resourcePath(), resourcePathEncoded));
  }

  private record ResourceShape(String namespace, String serviceName, String resourcePath) {
  }

  private static Optional<ResourceShape> parseShape(String pathStartingWithSlash) {
    final String[] segments = pathStartingWithSlash.substring(1).split("/", -1);
    if (segments.length < 2) {
      return Optional.empty();
    }
    final String namespace = segments[0];
    final String serviceName = segments[1];
    if (!isValidDnsLabel(namespace) || !isValidDnsLabel(serviceName)) {
      return Optional.empty();
    }
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

  static boolean isValidDnsLabel(String label) {
    return label != null && label.length() <= DNS_LABEL_MAX_LENGTH && DNS_LABEL_PATTERN.matcher(label).matches();
  }

  /**
   * Percent-encodes every character illegal in a URI path -- space, non-ASCII, and the delimiters
   * HTTP implementations quote -- while leaving "%" and "/" exactly as they are, so this can never
   * introduce or remove a path segment boundary or re-encode an escape already present.
   */
  static String minimalEncode(String value) {
    final StringBuilder result = new StringBuilder(value.length());
    int i = 0;
    while (i < value.length()) {
      final int codePoint = value.codePointAt(i);
      final int charCount = Character.charCount(codePoint);
      if (isSafe(codePoint)) {
        result.appendCodePoint(codePoint);
      } else {
        for (byte b : value.substring(i, i + charCount).getBytes(StandardCharsets.UTF_8)) {
          result.append('%').append(String.format(Locale.ROOT, "%02X", b & 0xFF));
        }
      }
      i += charCount;
    }
    return result.toString();
  }

  private static boolean isSafe(int codePoint) {
    if (codePoint < '!' || codePoint > '~') {
      return false;
    }
    final char c = (char) codePoint;
    if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
      return true;
    }
    return UNRESERVED_MARKS.indexOf(c) >= 0 || SUB_DELIMS.indexOf(c) >= 0 || c == ':' || c == '@' || c == '/' || c == '%';
  }
}
