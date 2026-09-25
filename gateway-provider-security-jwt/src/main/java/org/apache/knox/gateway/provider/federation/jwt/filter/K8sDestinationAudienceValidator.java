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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.SpiffeId;

/**
 * A {@link RequestAudienceValidator} that checks a delegation token's {@code aud} claim against
 * the request's actual destination, rather than against a fixed configured list. Each {@code aud}
 * entry is expected to be a URL of the form
 * {@code https://cluster-domain[:port]/[skipped-segments/]namespace/service-name[/resource-path]};
 * the segments this validator is configured to check are compared against the corresponding piece
 * of the request's actual destination, and every entry must have this shape to be considered a
 * match candidate, even if the segments an enabled check needs could otherwise be pulled out of a
 * differently shaped value. The optional {@code skipped-segments} prefix is only present, and only
 * searched for, when {@link #AUDIENCE_PATH_PREFIX_PARAM} is configured; see that constant and
 * {@link AudienceResource#parse(String, String)}.
 *
 * <p>Only tokens that carry a delegation {@code act} claim are routed through this validator; see
 * {@link #validate(HttpServletRequest, JWT, List)}.
 *
 * <p>Which segments are actually compared depends entirely on which of this validator's header
 * parameters are configured; the cluster-domain segment is the only one always enforced. Segments
 * with no configured source are not compared at all, so an {@code aud} entry that differs from the
 * request only in an unconfigured segment is accepted.
 */
public class K8sDestinationAudienceValidator implements RequestAudienceValidator {

  public static final String VALIDATION_METHOD_VALUE = "request.audience.k8s.destination.validation";

  private static final String PARAM_PREFIX = "request.audience.k8s.";

  /**
   * Name of the request header carrying the destination workload's SPIFFE id, e.g.
   * {@code spiffe://trust-domain/ns/namespace/sa/service-account}. When set, the namespace inside
   * this SPIFFE id is compared against the {@code aud} entry's namespace segment. No default: if
   * left unset, namespace is not matched from this source (it may still be matched via {@link
   * #SERVER_NAME_HEADER_PARAM}).
   *
   * <p>When both this header and {@link #SERVER_NAME_HEADER_PARAM} are configured, each supplies
   * its own view of the destination namespace and the two must agree; a request whose two
   * namespace sources disagree is rejected rather than resolved by preferring one source over the
   * other.
   *
   * <p>The value of this header is trusted at face value, with no independent verification by this
   * filter. It must be populated only by a component trusted to set or overwrite it correctly
   * before the request reaches Knox; that trust must be established outside Knox. Configuring this
   * to a header an untrusted caller can set or influence undermines the destination check this
   * validator provides.
   */
  public static final String NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM =
      PARAM_PREFIX + "namespace.from.destination.spiffe-id.header.name";

  /**
   * Name of the request header carrying the destination workload's FQDN, in the form
   * {@code server-name.namespace<cluster-suffix>}, optionally followed by {@code :port}. When set,
   * both the server-name and namespace segments are compared against the {@code aud} entry's
   * service-name and namespace segments. No default: if left unset, this header is not read, and
   * neither segment is matched from this source.
   *
   * <p>When both this header and {@link #NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM} are configured,
   * each supplies its own view of the destination namespace and the two must agree; a request
   * whose two namespace sources disagree is rejected rather than resolved by preferring one source
   * over the other.
   *
   * <p>The value of this header is trusted at face value, with no independent verification by this
   * filter. It must be populated only by a component trusted to set or overwrite it correctly
   * before the request reaches Knox; that trust must be established outside Knox. Configuring this
   * to a header an untrusted caller can set or influence undermines the destination check this
   * validator provides.
   */
  public static final String SERVER_NAME_HEADER_PARAM = PARAM_PREFIX + "server.name.header.name";

  /**
   * Suffix that terminates the FQDN read from {@link #SERVER_NAME_HEADER_PARAM}'s header, e.g.
   * {@code .svc.cluster.local}. Only read when that header parameter is itself configured. Defaults
   * to {@code .svc.cluster.local}, the standard Kubernetes in-cluster service FQDN suffix.
   */
  public static final String SERVER_NAME_CLUSTER_SUFFIX_PARAM = PARAM_PREFIX + "server.name.cluster-suffix";
  public static final String SERVER_NAME_CLUSTER_SUFFIX_DEFAULT = ".svc.cluster.local";

  /**
   * Name of the request header carrying the path to match against the {@code aud} entry's
   * resource-path segment. No default: if left unset, the resource path is not matched.
   *
   * <p>The value of this header is trusted at face value, with no independent verification by this
   * filter. It must be populated only by a component trusted to set or overwrite it correctly
   * before the request reaches Knox; that trust must be established outside Knox. Configuring this
   * to a header an untrusted caller can set or influence undermines the destination check this
   * validator provides.
   */
  public static final String PATH_HEADER_PARAM = PARAM_PREFIX + "path.header.name";

  /**
   * Whether the value of the header named by {@link #PATH_HEADER_PARAM} is a full URL whose path
   * component should be extracted (without decoding it), rather than already being the bare path to
   * match. Only read when that header parameter is itself configured. Defaults to {@code false},
   * since the header is expected to carry the routed request path directly.
   */
  public static final String PATH_HEADER_FROM_URL_PARAM = PARAM_PREFIX + "path.header.from.url";
  public static final boolean PATH_HEADER_FROM_URL_DEFAULT = false;

  /**
   * Comma-separated allow-list of cluster domains an {@code aud} entry's authority (host and
   * effective port) may match; always enforced, with no way to disable it. Defaults to
   * {@code service.local}, a placeholder that fails closed for any deployment that has not set this
   * to its own cluster domain(s).
   */
  public static final String CLUSTER_DOMAINS_PARAM = PARAM_PREFIX + "cluster-domains";
  public static final String CLUSTER_DOMAINS_DEFAULT = "service.local";

  /**
   * Whether the {@code aud} claim must be non-empty and every entry must match (@code true), or
   * whether at least one entry matching is sufficient ({@code false}), which is the ordinary RFC
   * 7519 semantic: the destination is an allowed audience. Defaults to {@code false}.
   *
   * <p>This is independent of, and does not substitute for, the minting-side controls over how
   * many audiences a delegation token may request that JWTFederationFilter itself exposes. A
   * deployment that wants every delegation token to carry exactly one audience should combine both
   * of those minting-side controls with setting this parameter to {@code true}: the minting-side
   * controls stop a multi-audience token from being issued in the first place, and this parameter
   * independently rejects one at the destination even if it is minted anyway, for example by an
   * older or misconfigured issuer.
   */
  public static final String REQUIRE_ALL_AUDIENCES_MATCH_PARAM = PARAM_PREFIX + "require-all-audiences-match";
  public static final boolean REQUIRE_ALL_AUDIENCES_MATCH_DEFAULT = false;

  /**
   * Optional path prefix that, when set, an {@code aud} entry's path is searched for before
   * namespace and service-name are parsed out of it -- see {@link AudienceResource#parse(String,
   * String)} for exactly how. This lets an {@code aud} entry have network routing flexibility
   * in case it is to be treated as a resource for RFC 8707 in the future. No default:
   * if left unset, an entry's path must begin with namespace and service-name straight after the
   * authority, exactly as when this parameter did not exist.
   */
  public static final String AUDIENCE_PATH_PREFIX_PARAM = PARAM_PREFIX + "audience.path.prefix";

  private static final int DEFAULT_HTTPS_PORT = 443;

  private String namespaceFromSpiffeIdHeader;
  private String serverNameHeader;
  private String serverNameClusterSuffix;
  private String pathHeader;
  private boolean pathHeaderFromUrl;
  private List<ClusterDomain> clusterDomains;
  private boolean requireAllAudiencesMatch;
  private String audiencePathPrefix;

  @Override
  public void init(final FilterConfig filterConfig) throws Exception {
    namespaceFromSpiffeIdHeader = blankToNull(filterConfig.getInitParameter(NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM));
    serverNameHeader = blankToNull(filterConfig.getInitParameter(SERVER_NAME_HEADER_PARAM));
    pathHeader = blankToNull(filterConfig.getInitParameter(PATH_HEADER_PARAM));

    if (namespaceFromSpiffeIdHeader == null && serverNameHeader == null && pathHeader == null) {
      throw new ServletException(String.format(Locale.ROOT,
          "At least one of %s, %s or %s must be configured for %s",
          NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SERVER_NAME_HEADER_PARAM, PATH_HEADER_PARAM,
          VALIDATION_METHOD_VALUE));
    }

    serverNameClusterSuffix = paramOrDefault(filterConfig, SERVER_NAME_CLUSTER_SUFFIX_PARAM, SERVER_NAME_CLUSTER_SUFFIX_DEFAULT);

    final String pathHeaderFromUrlParam = filterConfig.getInitParameter(PATH_HEADER_FROM_URL_PARAM);
    pathHeaderFromUrl = pathHeaderFromUrlParam == null ? PATH_HEADER_FROM_URL_DEFAULT : Boolean.parseBoolean(pathHeaderFromUrlParam);

    final String clusterDomainsParam = paramOrDefault(filterConfig, CLUSTER_DOMAINS_PARAM, CLUSTER_DOMAINS_DEFAULT);
    clusterDomains = new ArrayList<>();
    for (final String entry : parseCommaSeparated(clusterDomainsParam)) {
      final Optional<ClusterDomain> parsed = ClusterDomain.parse(entry);
      if (parsed.isEmpty()) {
        throw new ServletException(String.format(Locale.ROOT,
            "%s contains an invalid cluster domain entry: %s", CLUSTER_DOMAINS_PARAM, entry));
      }
      clusterDomains.add(parsed.get());
    }

    final String requireAllParam = filterConfig.getInitParameter(REQUIRE_ALL_AUDIENCES_MATCH_PARAM);
    requireAllAudiencesMatch = requireAllParam == null ? REQUIRE_ALL_AUDIENCES_MATCH_DEFAULT : Boolean.parseBoolean(requireAllParam);

    audiencePathPrefix = blankToNull(filterConfig.getInitParameter(AUDIENCE_PATH_PREFIX_PARAM));
  }

  @Override
  public AudienceValidationResult validate(final HttpServletRequest request, final JWT token,
      final List<String> configuredAudiences) {
    if (token.getClaimAsObject(JWTToken.ACT_CLAIM) == null) {
      return AudienceValidationResult.of(AbstractJWTFilter.matchesConfiguredAudiences(token, configuredAudiences));
    }

    final String[] audienceClaims = token.getAudienceClaims();
    if (audienceClaims == null || audienceClaims.length == 0) {
      return new AudienceValidationResult(false, "Token has an act claim but no aud claim");
    }

    final Optional<String> namespaceFromSpiffeId = namespaceFromSpiffeIdHeader == null
        ? Optional.empty() : namespaceFromHeader(request);
    if (namespaceFromSpiffeIdHeader != null && namespaceFromSpiffeId.isEmpty()) {
      return new AudienceValidationResult(false,
          "Missing or unparseable destination SPIFFE id in header " + namespaceFromSpiffeIdHeader);
    }

    final Optional<DestinationServiceName> destinationServiceName = serverNameHeader == null
        ? Optional.empty() : destinationServiceNameFromHeader(request);
    if (serverNameHeader != null && destinationServiceName.isEmpty()) {
      return new AudienceValidationResult(false,
          "Missing or unparseable destination server name in header " + serverNameHeader);
    }

    if (namespaceFromSpiffeId.isPresent() && destinationServiceName.isPresent()
        && !namespaceFromSpiffeId.get().equals(destinationServiceName.get().namespace())) {
      return new AudienceValidationResult(false,
          "Destination namespace from " + namespaceFromSpiffeIdHeader + " disagrees with namespace from "
              + serverNameHeader);
    }

    final Optional<String> requestPath = pathHeader == null ? Optional.empty() : pathFromHeader(request);
    if (pathHeader != null && requestPath.isEmpty()) {
      return new AudienceValidationResult(false, "Missing or unparseable destination path in header " + pathHeader);
    }

    final String namespace = namespaceFromSpiffeId.isPresent() ? namespaceFromSpiffeId.get()
        : destinationServiceName.map(DestinationServiceName::namespace).orElse(null);
    final String serviceName = destinationServiceName.map(DestinationServiceName::serviceName).orElse(null);

    String firstFailureReason = null;
    for (final String audienceClaim : audienceClaims) {
      final Optional<AudienceResource> parsed = AudienceResource.parse(audienceClaim, audiencePathPrefix);
      if (parsed.isEmpty()) {
        final String reason = "aud entry is not a valid k8s destination URL: " + audienceClaim;
        if (requireAllAudiencesMatch) {
          return new AudienceValidationResult(false, reason);
        }
        firstFailureReason = firstFailureReason == null ? reason : firstFailureReason;
        continue;
      }
      if (matches(parsed.get(), namespace, serviceName, requestPath.orElse(null))) {
        if (!requireAllAudiencesMatch) {
          return AudienceValidationResult.of(true);
        }
      } else {
        final String reason = "aud entry does not match request destination: " + audienceClaim;
        if (requireAllAudiencesMatch) {
          return new AudienceValidationResult(false, reason);
        }
        firstFailureReason = firstFailureReason == null ? reason : firstFailureReason;
      }
    }

    if (requireAllAudiencesMatch) {
      return AudienceValidationResult.of(true);
    }
    return new AudienceValidationResult(false, firstFailureReason);
  }

  private boolean matches(final AudienceResource candidate, final String namespace, final String serviceName,
      final String requestPath) {
    if (!matchesClusterDomain(candidate)) {
      return false;
    }
    if (namespace != null && !namespace.equals(candidate.namespace())) {
      return false;
    }
    if (serviceName != null && !serviceName.equals(candidate.serviceName())) {
      return false;
    }
    if (requestPath != null
        && !requestPath.equals(candidate.resourcePathRaw()) && !requestPath.equals(candidate.resourcePathEncoded())) {
      return false;
    }
    return true;
  }

  private boolean matchesClusterDomain(final AudienceResource candidate) {
    for (final ClusterDomain allowed : clusterDomains) {
      if (allowed.host().equals(candidate.host()) && allowed.port() == candidate.effectivePort()) {
        return true;
      }
    }
    return false;
  }

  /**
   * The SPIFFE id's namespace, lower-cased to match {@link AudienceResource#namespace()} and
   * {@link DestinationServiceName#namespace()} so every namespace comparison in this class is a
   * plain, case-sensitive {@code equals}.
   */
  private Optional<String> namespaceFromHeader(final HttpServletRequest request) {
    final String headerValue = request.getHeader(namespaceFromSpiffeIdHeader);
    if (headerValue == null || headerValue.isEmpty()) {
      return Optional.empty();
    }
    return SpiffeId.parse(headerValue).map(id -> id.namespace().toLowerCase(Locale.ROOT));
  }

  private Optional<DestinationServiceName> destinationServiceNameFromHeader(final HttpServletRequest request) {
    final String headerValue = request.getHeader(serverNameHeader);
    if (headerValue == null || headerValue.isEmpty()) {
      return Optional.empty();
    }
    return DestinationServiceName.parse(headerValue, serverNameClusterSuffix);
  }

  private Optional<String> pathFromHeader(final HttpServletRequest request) {
    final String headerValue = request.getHeader(pathHeader);
    if (headerValue == null || headerValue.isEmpty()) {
      return Optional.empty();
    }
    final String rawPath;
    if (pathHeaderFromUrl) {
      final int schemeSep = headerValue.indexOf("://");
      final int pathStart = schemeSep < 0 ? -1 : headerValue.indexOf('/', schemeSep + 3);
      if (pathStart < 0) {
        return Optional.empty();
      }
      final int queryOrFragment = indexOfFirst(headerValue, pathStart, '?', '#');
      rawPath = queryOrFragment < 0 ? headerValue.substring(pathStart) : headerValue.substring(pathStart, queryOrFragment);
    } else {
      final int queryOrFragment = indexOfFirst(headerValue, 0, '?', '#');
      rawPath = queryOrFragment < 0 ? headerValue : headerValue.substring(0, queryOrFragment);
    }
    return AudienceResource.normalizeAndTidy(rawPath);
  }

  private static int indexOfFirst(final String value, final int fromIndex, final char a, final char b) {
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

  @Override
  public String getName() {
    return VALIDATION_METHOD_VALUE;
  }

  private record ClusterDomain(String host, int port) {
    static Optional<ClusterDomain> parse(final String entry) {
      if (entry == null) {
        return Optional.empty();
      }
      final String value = entry.trim();
      if (value.isEmpty() || value.indexOf('/') >= 0 || value.indexOf('@') >= 0 || value.contains("://")) {
        return Optional.empty();
      }
      final int colon = value.lastIndexOf(':');
      if (colon < 0) {
        return Optional.of(new ClusterDomain(value.toLowerCase(Locale.ROOT), DEFAULT_HTTPS_PORT));
      }
      final String host = value.substring(0, colon);
      final String portPart = value.substring(colon + 1);
      if (host.isEmpty() || portPart.isEmpty() || !isAllDigits(portPart)) {
        return Optional.empty();
      }
      try {
        final int port = Integer.parseInt(portPart);
        if (port < 1 || port > 65535) {
          return Optional.empty();
        }
        return Optional.of(new ClusterDomain(host.toLowerCase(Locale.ROOT), port));
      } catch (final NumberFormatException e) {
        return Optional.empty();
      }
    }

    private static boolean isAllDigits(final String value) {
      if (value.isEmpty()) {
        return false;
      }
      for (int i = 0; i < value.length(); i++) {
        if (!Character.isDigit(value.charAt(i))) {
          return false;
        }
      }
      return true;
    }
  }

  private static String blankToNull(final String value) {
    return (value == null || value.trim().isEmpty()) ? null : value.trim();
  }

  private static String paramOrDefault(final FilterConfig filterConfig, final String name, final String defaultValue) {
    final String value = filterConfig.getInitParameter(name);
    return (value == null || value.isEmpty()) ? defaultValue : value;
  }

  private static List<String> parseCommaSeparated(final String commaSeparatedList) {
    return Arrays.stream(commaSeparatedList.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }
}
