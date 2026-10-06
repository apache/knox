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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.SpiffeId;

/**
 * A {@link RequestAudienceValidator} that checks a delegation token's {@code aud} claim against
 * the request's actual destination, rather than against a fixed configured list. Each {@code aud}
 * entry is tried first as a k8s service DNS name,
 * {@code https://<service>.<namespace>.svc.<cluster-domain>[:port][/resource-path]}, and second as
 * {@code https://cluster-domain[:port]/[skipped-segments/]namespace/service-name[/resource-path]}.
 * An entry is accepted as a match candidate if either form accepts it, and is rejected only if
 * neither does. The optional {@code skipped-segments} prefix in the second, custom form is only
 * present, and only searched for, when {@link #AUDIENCE_PATH_PREFIX_PARAM} is configured, and it
 * has no effect on the first form; see that constant and
 * {@link AudienceResource#parseCustomForm(String, String)}.
 * <p>The first form is tried at all only when {@link #DNS_FORMAT_ENABLED_PARAM} is {@code true},
 * its default; when it is {@code false}, every entry is evaluated only as the custom form.
 * <p>A token carrying a delegation {@code act} claim is always routed through this validator; see
 * {@link #validate(HttpServletRequest, JWT, List)}. A token with no {@code act} claim is too,
 * unless {@link #VALIDATE_AUDIENCES_WITHOUT_ACT_CLAIM_PARAM} is {@code false}.
 *
 * <p>Which segments are actually compared depends entirely on which of this validator's header
 * parameters are configured; for an entry in the second, custom form, the cluster-domain segment
 * is the only one always enforced. Segments with no configured source are not compared at all, so
 * an {@code aud} entry that differs from the request only in an unconfigured segment is accepted.
 *
 * <p>One case departs from the rule that a segment with no configured source is not
 * compared. An {@code aud} entry written as a k8s service DNS name whose host carries no
 * namespace takes its namespace from the source workload's SPIFFE id, and that value must
 * equal the destination namespace. If no destination namespace is configured there is
 * nothing to compare it against, and the entry is rejected rather than accepted. Without
 * this, such an entry would match the named service in any namespace.
 *
 * <p>An {@code aud} entry with no resource path, or with only {@code /}, matches any request
 * path, for either form; the path header is not consulted for it. This is deliberate: an {@code
 * aud} entry with an empty path is a statement about which destination service a token may
 * be presented to, not about which operations on that service it may invoke. This holds even
 * when {@link #PATH_HEADER_PARAM} is configured and the header is present on the request;
 * configuring that header lets an {@code aud} entry that does specify a path be checked
 * against it, but does not, by itself, require every entry to specify one. The one case this
 * does not cover well is a deployment that wants an {@code aud} entry of exactly {@code /}
 * to mean "the root path only" rather than "any path" -- that distinction is
 * not made here, since it is rarely the intended meaning in practice. When the audience is
 * specified with an empty path, typically the specific service operations allowed are
 * specified in a different way, such as with scopes.
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
   *
   * <p>Deliberately independent of {@link #CLUSTER_DOMAIN_PARAM}, which plays the same role for
   * an {@code aud} entry rather than for this header. In a typical deployment both carry the
   * same cluster domain. They are separate because this header carries whatever an upstream
   * component chose to send, and may be absent or empty, while an {@code aud} entry is
   * canonical.
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

  /**
   * Comma-separated allow-list of cluster domains an {@code aud} entry's authority (host and
   * effective port) may match; always enforced, with no way to disable it. Defaults to
   * {@code service.local}, a placeholder that fails closed for any deployment that has not set this
   * to its own cluster domain(s).
   *
   * <p>Applies only to an {@code aud} entry in the form described above. An entry written as a
   * k8s service DNS name is not checked against this list: its authority is checked against
   * {@link #CLUSTER_DOMAIN_PARAM} instead, and its port is not checked at all.
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

  /**
   * Optional path prefix that, when set, an {@code aud} entry's path is searched for before
   * namespace and service-name are parsed out of it -- see
   * {@link AudienceResource#parseCustomForm(String, String)} for exactly how. This lets an
   * {@code aud} entry have network routing flexibility
   * in the future. No default: if left unset, an entry's path must begin with namespace and
   * service-name straight after the authority, exactly as when this parameter did not exist.
   *
   * <p>Applies only to an {@code aud} entry in the form described above. An entry written as a
   * k8s service DNS name carries its namespace and service name in the host, so it has no
   * leading segments to skip; its whole path is its resource path, and this prefix is neither
   * searched for nor removed.
   */
  public static final String AUDIENCE_PATH_PREFIX_PARAM = PARAM_PREFIX + "audience.path.prefix";

  /**
   * Whether an {@code aud} entry written as a k8s service DNS name is accepted at all. Defaults to
   * {@code true}. When {@code false}, every {@code aud} entry is evaluated only as the custom
   * form, and {@link #CLUSTER_DOMAIN_PARAM} is unused.
   *
   * <p>A deployment that fronts more than one cluster, or more than one trust domain, behind this
   * validator may want to set this to {@code false}: a k8s service DNS name carries no cluster or
   * trust-domain identifier of its own, so there is no way to tell, from the entry alone, which
   * cluster or trust domain a bare {@code <service>.<namespace>} host belongs to. The custom form
   * does not have this ambiguity, since its cluster-domain segment is checked against an explicit
   * allow-list.
   */
  public static final String DNS_FORMAT_ENABLED_PARAM = PARAM_PREFIX + "dns.format.enabled";
  public static final boolean DNS_FORMAT_ENABLED_DEFAULT = true;

  /**
   * The Kubernetes cluster domain used when parsing an {@code aud} entry written as a k8s
   * service DNS name, that is, the {@code cluster.local} in
   * {@code https://<service>.<namespace>.svc.cluster.local/path}. Defaults to
   * {@code cluster.local}, the Kubernetes default.
   *
   * <p>An {@code aud} host may be shortened from the right at label boundaries, as a DNS
   * resolver search path allows: {@code <service>}, {@code <service>.<namespace>},
   * {@code <service>.<namespace>.svc}, or {@code <service>.<namespace>.svc} followed by any
   * label-boundary prefix of this value. Labels after {@code svc} that are not such a prefix
   * mean the entry is not a k8s service DNS name. A trailing dot is not accepted.
   *
   * <p>Deliberately independent of {@link #SERVER_NAME_CLUSTER_SUFFIX_PARAM}, which plays the
   * same role for a request header rather than for an {@code aud} entry. In a typical
   * deployment both carry the same cluster domain. They are separate because an {@code aud}
   * entry is canonical, while a header carries whatever an upstream component chose to send.
   */
  public static final String CLUSTER_DOMAIN_PARAM = PARAM_PREFIX + "cluster.domain";
  public static final String CLUSTER_DOMAIN_DEFAULT = "cluster.local";

  /**
   * Name of the request header carrying the source workload's SPIFFE id, e.g.
   * {@code spiffe://trust-domain/ns/namespace/sa/service-account}. No default: if left unset,
   * no source identity is available, an {@code aud} entry whose host omits a namespace cannot
   * be matched, and {@link #ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM} must be
   * {@code false} or initialization fails.
   *
   * <p>The name does not mention a single use because the value serves two: it supplies the
   * namespace for an {@code aud} entry whose host omits one, and it is the identity compared
   * against the actor subject when
   * {@link #ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM} is enabled.
   *
   * <p>When this parameter names a header, that header must be present and parseable as a
   * SPIFFE id on every request, or audience validation fails, whether or not the value turns
   * out to be needed for the {@code aud} entries actually presented. Every other header this
   * validator is configured to read behaves the same way.
   *
   * <p>The value of this header is trusted at face value, with no independent verification by
   * this filter. It must be populated only by a component trusted to set or overwrite it
   * correctly before the request reaches Knox; that trust must be established outside Knox.
   * Configuring this to a header an untrusted caller can set or influence undermines the
   * destination check this validator provides.
   */
  public static final String SOURCE_SPIFFE_ID_HEADER_PARAM = PARAM_PREFIX + "source.spiffe-id.header.name";

  /**
   * Whether the {@code aud} claim of a token with no {@code act} claim, meaning it is not a
   * delegated token, is validated against the request destination, or is left to match the
   * fixed configured allowed-audience list. Defaults to {@code true}, the fail-closed setting:
   * a token that does not name a destination matching this request is rejected, rather than
   * being accepted by a check that passes unconditionally when no audiences are configured.
   *
   * <p>Regardless of this parameter's value, audiences are always validated when the JWT has an
   * {@code act} claim. This parameter governs only the case where that claim is absent; it
   * cannot be used to weaken validation of a delegation token.
   *
   * <p>When {@code true}, the {@code aud} claim of a token with no {@code act} claim is
   * validated in the same way as the {@code aud} claim of a token that has one. A token
   * carrying no {@code aud} claim, or an empty one, is rejected, and
   * {@code AbstractJWTFilter.matchesConfiguredAudiences} is not consulted.
   *
   * <p>When {@code false}, the {@code aud} claim of a token with no {@code act} claim is
   * validated against the fixed configured allowed-audience list.
   */
  public static final String VALIDATE_AUDIENCES_WITHOUT_ACT_CLAIM_PARAM =
      PARAM_PREFIX + "validate.audiences.without.act.claim";
  public static final boolean VALIDATE_AUDIENCES_WITHOUT_ACT_CLAIM_DEFAULT = true;

  /**
   * Whether the service account named by the most recent actor in the token's {@code act}
   * chain must match the service account in the source workload's SPIFFE id. Defaults to
   * {@code false}: the check is inactive unless a deployment opts into it.
   *
   * <p>The actor subject is expected in the form
   * {@code system:serviceaccount:<namespace>:<service-account-name>} and the source identity as
   * a SPIFFE id. Only the namespace and the service-account name are compared. The trust domain
   * and the issuer are not, because the two forms carry no comparable value for them.
   *
   * <p>This check reads {@link #SOURCE_SPIFFE_ID_HEADER_PARAM}, which has no default. Setting
   * this to {@code true} without naming that header is a configuration error: initialization
   * fails, rather than leaving a check a deployment asked for silently inactive.
   *
   * <p>Setting this to {@code false} disables the actor-subject check entirely, including
   * {@link #ENFORCE_ACT_SUB_IS_SERVICE_ACCOUNT_PARAM}. A token with no {@code act} claim
   * carries no actor, so the check does not apply to it.
   *
   * <p>A token that does carry an {@code act} claim, but whose value is not a JSON object -- so
   * no actor subject can be read from it at all -- fails validation when this is {@code true},
   * the same as any other actor subject this check rejects. This differs from a token with no
   * {@code act} claim, which this check does not apply to in the first place.
   */
  public static final String ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM =
      PARAM_PREFIX + "enforce.act.sub.service-account.matches.source.spiffeid";

  /**
   * Whether an actor subject that cannot be read as
   * {@code system:serviceaccount:<namespace>:<service-account-name>} fails validation
   * ({@code true}) or passes unchecked ({@code false}). Defaults to {@code false}, because only
   * a service-account actor subject can be compared against a SPIFFE id and other subject forms
   * are legitimate.
   *
   * <p>Only consulted when {@link #ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM} is
   * {@code true}. Setting this to {@code true} while that parameter is {@code false} has no
   * effect.
   *
   * <p>Setting this to {@code true} asserts that every actor reaching this validator is a
   * Kubernetes service account within the same trust domain as the source identity it is
   * compared against. Do not enable it where actors may come from more than one trust domain,
   * or where an actor may legitimately be a user rather than a service account.
   */
  public static final String ENFORCE_ACT_SUB_IS_SERVICE_ACCOUNT_PARAM =
      PARAM_PREFIX + "enforce.act.sub.is.service.account";

  private String namespaceFromSpiffeIdHeader;
  private String serverNameHeader;
  private String serverNameClusterSuffix;
  private String pathHeader;
  private boolean pathHeaderFromUrl;
  private List<ClusterDomain> clusterDomains;
  private boolean requireAllAudiencesMatch;
  private String audiencePathPrefix;
  private boolean dnsFormatEnabled;
  private String clusterDomain;
  private String sourceSpiffeIdHeader;
  private boolean validateAudiencesWithoutActClaim;
  private boolean enforceActSubMatchesSourceSpiffeId;
  private boolean enforceActSubIsServiceAccount;

  @Override
  public void init(final FilterConfig filterConfig) throws Exception {
    namespaceFromSpiffeIdHeader = blankToNull(filterConfig.getInitParameter(NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM));
    serverNameHeader = blankToNull(filterConfig.getInitParameter(SERVER_NAME_HEADER_PARAM));
    pathHeader = blankToNull(filterConfig.getInitParameter(PATH_HEADER_PARAM));
    sourceSpiffeIdHeader = blankToNull(filterConfig.getInitParameter(SOURCE_SPIFFE_ID_HEADER_PARAM));

    if (namespaceFromSpiffeIdHeader == null && serverNameHeader == null && pathHeader == null) {
      throw new ServletException(String.format(Locale.ROOT,
          "At least one of %s, %s or %s must be configured for %s",
          NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM, SERVER_NAME_HEADER_PARAM, PATH_HEADER_PARAM,
          VALIDATION_METHOD_VALUE));
    }

    serverNameClusterSuffix = paramOrDefault(filterConfig, SERVER_NAME_CLUSTER_SUFFIX_PARAM, SERVER_NAME_CLUSTER_SUFFIX_DEFAULT);
    clusterDomain = paramOrDefault(filterConfig, CLUSTER_DOMAIN_PARAM, CLUSTER_DOMAIN_DEFAULT);

    pathHeaderFromUrl = Boolean.parseBoolean(filterConfig.getInitParameter(PATH_HEADER_FROM_URL_PARAM));

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

    requireAllAudiencesMatch = Boolean.parseBoolean(filterConfig.getInitParameter(REQUIRE_ALL_AUDIENCES_MATCH_PARAM));

    audiencePathPrefix = blankToNull(filterConfig.getInitParameter(AUDIENCE_PATH_PREFIX_PARAM));

    // Defaults to true, so cannot use only Boolean.parseBoolean() to handle a missing definition.
    final String dnsFormatEnabledParam = filterConfig.getInitParameter(DNS_FORMAT_ENABLED_PARAM);
    dnsFormatEnabled = (dnsFormatEnabledParam == null || dnsFormatEnabledParam.isEmpty())
        ? DNS_FORMAT_ENABLED_DEFAULT
        : Boolean.parseBoolean(dnsFormatEnabledParam);

    // Defaults to true, so cannot use only Boolean.parseBoolean() to handle a missing definition.
    final String validateAudiencesWithoutActClaimParam =
        filterConfig.getInitParameter(VALIDATE_AUDIENCES_WITHOUT_ACT_CLAIM_PARAM);
    validateAudiencesWithoutActClaim =
        (validateAudiencesWithoutActClaimParam == null || validateAudiencesWithoutActClaimParam.isEmpty())
            ? VALIDATE_AUDIENCES_WITHOUT_ACT_CLAIM_DEFAULT
            : Boolean.parseBoolean(validateAudiencesWithoutActClaimParam);

    enforceActSubMatchesSourceSpiffeId =
        Boolean.parseBoolean(filterConfig.getInitParameter(ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM));
    enforceActSubIsServiceAccount =
        Boolean.parseBoolean(filterConfig.getInitParameter(ENFORCE_ACT_SUB_IS_SERVICE_ACCOUNT_PARAM));

    if (enforceActSubMatchesSourceSpiffeId && sourceSpiffeIdHeader == null) {
      throw new ServletException(String.format(Locale.ROOT,
          "%s is true but %s is not configured", ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM,
          SOURCE_SPIFFE_ID_HEADER_PARAM));
    }
  }

  @Override
  public AudienceValidationResult validate(final HttpServletRequest request, final JWT token,
      final List<String> configuredAudiences) {
    final boolean hasActClaim = token.getClaimAsObject(JWTToken.ACT_CLAIM) != null;
    if (!hasActClaim && !validateAudiencesWithoutActClaim) {
      return AudienceValidationResult.of(AbstractJWTFilter.matchesConfiguredAudiences(token, configuredAudiences));
    }

    final String[] audienceClaims = token.getAudienceClaims();
    if (audienceClaims == null || audienceClaims.length == 0) {
      return new AudienceValidationResult(false, hasActClaim
          ? "Token has an act claim but no aud claim"
          : "Token has no act claim and no aud claim, and " + VALIDATE_AUDIENCES_WITHOUT_ACT_CLAIM_PARAM
              + " is true");
    }

    final Optional<String> namespaceFromSpiffeId = namespaceFromSpiffeIdHeader == null
        ? Optional.empty() : namespaceFromHeader(request);
    if (namespaceFromSpiffeIdHeader != null && namespaceFromSpiffeId.isEmpty()) {
      return new AudienceValidationResult(false, "Missing or unparseable destination SPIFFE id in header "
          + namespaceFromSpiffeIdHeader + ": " + request.getHeader(namespaceFromSpiffeIdHeader));
    }

    final Optional<DestinationServiceName> destinationServiceName = serverNameHeader == null
        ? Optional.empty() : destinationServiceNameFromHeader(request);
    if (serverNameHeader != null && destinationServiceName.isEmpty()) {
      return new AudienceValidationResult(false, "Missing or unparseable destination server name in header "
          + serverNameHeader + ": " + request.getHeader(serverNameHeader));
    }

    final Optional<SpiffeId> sourceSpiffeId = sourceSpiffeIdHeader == null
        ? Optional.empty() : sourceSpiffeIdFromHeader(request);
    if (sourceSpiffeIdHeader != null && sourceSpiffeId.isEmpty()) {
      return new AudienceValidationResult(false, "Missing or unparseable source SPIFFE id in header "
          + sourceSpiffeIdHeader + ": " + request.getHeader(sourceSpiffeIdHeader));
    }

    if (namespaceFromSpiffeId.isPresent() && destinationServiceName.isPresent()
        && !namespaceFromSpiffeId.get().equals(destinationServiceName.get().namespace())) {
      return new AudienceValidationResult(false,
          "Destination namespace " + namespaceFromSpiffeId.get() + " from header " + namespaceFromSpiffeIdHeader
              + " disagrees with namespace " + destinationServiceName.get().namespace() + " from header "
              + serverNameHeader);
    }

    if (enforceActSubMatchesSourceSpiffeId) {
      final List<Map<String, Object>> actorChain = TokenUtils.extractActorChain(token);
      if (!actorChain.isEmpty()) {
        final Optional<String> actSubFailure = actSubMatchFailureReason(actorChain.get(0), sourceSpiffeId.get());
        if (actSubFailure.isPresent()) {
          return new AudienceValidationResult(false, actSubFailure.get());
        }
      } else if (hasActClaim) {
        return new AudienceValidationResult(false,
            "Token has an act claim that is not a JSON object, so the actor subject cannot be read to "
                + "compare against the source SPIFFE id, and " + ENFORCE_ACT_SUB_MATCHES_SOURCE_SPIFFE_ID_PARAM
                + " is true");
      }
    }

    final Optional<String> requestPath = pathHeader == null ? Optional.empty() : pathFromHeader(request);
    if (pathHeader != null && requestPath.isEmpty()) {
      return new AudienceValidationResult(false, "Missing or unparseable destination path in header "
          + pathHeader + ": " + request.getHeader(pathHeader));
    }

    final String namespace = namespaceFromSpiffeId.isPresent() ? namespaceFromSpiffeId.get()
        : destinationServiceName.map(DestinationServiceName::namespace).orElse(null);
    final String serviceName = destinationServiceName.map(DestinationServiceName::serviceName).orElse(null);
    final String path = requestPath.orElse(null);

    if (requireAllAudiencesMatch) {
      for (final String audienceClaim : audienceClaims) {
        final Optional<String> failure = matchFailureReason(audienceClaim, namespace, serviceName, path, sourceSpiffeId);
        if (failure.isPresent()) {
          return new AudienceValidationResult(false, failure.get());
        }
      }
      return AudienceValidationResult.of(true);
    }

    String lastFailureReason = null;
    for (final String audienceClaim : audienceClaims) {
      final Optional<String> failure = matchFailureReason(audienceClaim, namespace, serviceName, path, sourceSpiffeId);
      if (failure.isEmpty()) {
        return AudienceValidationResult.of(true);
      }
      lastFailureReason = failure.get();
    }
    return new AudienceValidationResult(false, lastFailureReason);
  }

  /**
   * The failure reason for a single {@code aud} entry against the request's actual destination, or
   * {@link Optional#empty()} if the entry is accepted. When {@link #DNS_FORMAT_ENABLED_PARAM} is
   * {@code true} (the default), tries the DNS form first and falls back to the custom form on
   * either a parse failure or a match failure, so a custom-form entry whose base domain happens to
   * also parse as a DNS-shaped host is still evaluated as a custom-form entry rather than rejected
   * outright. When that parameter is {@code false}, the DNS form is never attempted and every entry
   * is evaluated only as the custom form.
   *
   * @param audienceClaim the single {@code aud} claim entry being checked against the destination
   * @param namespace the destination namespace to compare against, or {@code null} exactly when
   *     no destination-namespace source is configured at all -- that is, both
   *     {@link #NAMESPACE_FROM_SPIFFE_ID_HEADER_PARAM} and {@link #SERVER_NAME_HEADER_PARAM} are
   *     unset. This is a configuration-time fact, not a per-request one: a configured source whose
   *     header is merely missing or unparseable on a given request fails validation earlier, in
   *     {@link #validate(HttpServletRequest, JWT, List)}, before this method is ever reached, so by
   *     the time {@code namespace} is {@code null} here it can only mean no source was configured.
   *     That distinction is exactly what {@link #dnsMatchFailureReason} relies on: a namespace-less
   *     DNS-form host is rejected when {@code namespace} is {@code null}, which is the one case in
   *     this class where a segment with no configured source is still compared against instead of
   *     being skipped -- see the class javadoc.
   * @param serviceName the destination service name to compare against, or {@code null} if
   *     {@link #SERVER_NAME_HEADER_PARAM} is not configured
   * @param requestPath the destination request path to compare against, or {@code null} if
   *     {@link #PATH_HEADER_PARAM} is not configured
   * @param sourceSpiffeId the source workload's SPIFFE id, present only if
   *     {@link #SOURCE_SPIFFE_ID_HEADER_PARAM} is configured; supplies the namespace for a
   *     namespace-less DNS-form host
   * @return the failure reason, or {@link Optional#empty()} if {@code audienceClaim} matches
   */
  private Optional<String> matchFailureReason(final String audienceClaim, final String namespace,
      final String serviceName, final String requestPath, final Optional<SpiffeId> sourceSpiffeId) {
    Optional<String> dnsFailure = Optional.of("the k8s service DNS name form is disabled");
    if (dnsFormatEnabled) {
      final Optional<AudienceResource> dnsParsed = AudienceResource.parseDnsForm(audienceClaim, clusterDomain);
      dnsFailure = dnsParsed.isPresent()
          ? dnsMatchFailureReason(dnsParsed.get(), namespace, serviceName, requestPath, sourceSpiffeId)
          : Optional.of("does not parse as a k8s service DNS name");
      if (dnsFailure.isEmpty()) {
        return Optional.empty();
      }
    }

    final Optional<AudienceResource> customParsed = AudienceResource.parseCustomForm(audienceClaim, audiencePathPrefix);
    final Optional<String> customFailure = customParsed.isPresent()
        ? customMatchFailureReason(customParsed.get(), namespace, serviceName, requestPath)
        : Optional.of("does not parse as a custom form destination URL");
    if (customFailure.isEmpty()) {
      return Optional.empty();
    }

    if (!dnsFormatEnabled) {
      return Optional.of("aud entry " + audienceClaim + " does not match the destination: " + customFailure.get());
    }

    return Optional.of("aud entry " + audienceClaim + " does not match the destination in either supported "
        + "format: DNS name (" + dnsFailure.get() + "); custom form (" + customFailure.get() + ")");
  }

  /**
   * A namespace-less host (one label) takes its namespace from {@code sourceSpiffeId} and that
   * value must equal {@code namespace}; with no destination namespace configured there is nothing
   * to compare it against, so the entry does not match, even though the general rule in this class
   * is that a segment with no configured source is not compared.
   */
  private Optional<String> dnsMatchFailureReason(final AudienceResource candidate,
      final String namespace, final String serviceName, final String requestPath,
      final Optional<SpiffeId> sourceSpiffeId) {
    final String candidateNamespace;
    if (candidate.namespace() != null) {
      candidateNamespace = candidate.namespace();
    } else if (namespace == null) {
      return Optional.of("host has no namespace and no destination namespace is configured to compare a "
          + "source namespace against");
    } else if (sourceSpiffeId.isEmpty()) {
      return Optional.of("host has no namespace and no source SPIFFE id is available to supply one");
    } else {
      candidateNamespace = sourceSpiffeId.get().namespace();
    }
    if (namespace != null && !namespace.equals(candidateNamespace)) {
      return Optional.of("namespace " + candidateNamespace + " does not match destination namespace " + namespace);
    }
    if (serviceName != null && !serviceName.equals(candidate.serviceName())) {
      return Optional.of("service name " + candidate.serviceName() + " does not match destination service name "
          + serviceName);
    }
    if (!matchesPath(candidate.resourcePathRaw(), candidate.resourcePathEncoded(), requestPath)) {
      return Optional.of("path does not match request path");
    }
    return Optional.empty();
  }

  private Optional<String> customMatchFailureReason(final AudienceResource candidate, final String namespace,
      final String serviceName, final String requestPath) {
    if (!matchesClusterDomain(candidate)) {
      return Optional.of("cluster domain " + candidate.host() + ":" + candidate.effectivePort()
          + " is not an allowed cluster domain");
    }
    if (namespace != null && !namespace.equals(candidate.namespace())) {
      return Optional.of("namespace " + candidate.namespace() + " does not match destination namespace " + namespace);
    }
    if (serviceName != null && !serviceName.equals(candidate.serviceName())) {
      return Optional.of("service name " + candidate.serviceName() + " does not match destination service name "
          + serviceName);
    }
    if (!matchesPath(candidate.resourcePathRaw(), candidate.resourcePathEncoded(), requestPath)) {
      return Optional.of("path does not match request path");
    }
    return Optional.empty();
  }

  /**
   * A {@code null} requestPath means the path header is not configured, so the path is not
   * compared at all. A candidate resource path of exactly {@code "/"} (or, by the time this is
   * reached, no path at all -- see {@link AudienceResource}) is a wildcard that matches any
   * requestPath, including a configured and populated one -- a different condition from the path
   * not being compared, since here the path header may be present and the {@code aud} entry still
   * declined to constrain the path. This is intentional, not an oversight: see the class javadoc's
   * note on why a path-less entry means "the whole service" rather than being treated as under-
   * specified.
   */
  private static boolean matchesPath(final String resourcePathRaw, final String resourcePathEncoded,
      final String requestPath) {
    if (requestPath == null || "/".equals(resourcePathRaw)) {
      return true;
    }
    return requestPath.equals(resourcePathRaw) || requestPath.equals(resourcePathEncoded);
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

  /**
   * All three fields are lower-cased here, once, on read -- the same convention
   * {@link #namespaceFromHeader(HttpServletRequest)} and {@link DestinationServiceName} follow --
   * so every comparison against this value elsewhere in this class is a plain, case-sensitive
   * {@code equals} rather than each comparison site remembering to lower-case its own copy.
   * {@code trustDomain} is lowered too, for consistency, even though this validator never
   * compares it.
   */
  private Optional<SpiffeId> sourceSpiffeIdFromHeader(final HttpServletRequest request) {
    final String headerValue = request.getHeader(sourceSpiffeIdHeader);
    if (headerValue == null || headerValue.isEmpty()) {
      return Optional.empty();
    }
    return SpiffeId.parse(headerValue).map(id -> new SpiffeId(id.trustDomain().toLowerCase(Locale.ROOT),
        id.namespace().toLowerCase(Locale.ROOT), id.serviceAccount().toLowerCase(Locale.ROOT)));
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
      final int queryOrFragment = AudienceResource.indexOfFirst(headerValue, pathStart, '?', '#');
      rawPath = queryOrFragment < 0 ? headerValue.substring(pathStart) : headerValue.substring(pathStart, queryOrFragment);
    } else {
      final int queryOrFragment = AudienceResource.indexOfFirst(headerValue, 0, '?', '#');
      rawPath = queryOrFragment < 0 ? headerValue : headerValue.substring(0, queryOrFragment);
    }
    return AudienceResource.normalizeAndTidy(rawPath);
  }

  /**
   * The failure reason for the most recent actor in the act chain to match {@code source}, or
   * {@link Optional#empty()} if the check passes -- either because the two service accounts match,
   * or because the actor subject is not a service-account subject and
   * {@link #ENFORCE_ACT_SUB_IS_SERVICE_ACCOUNT_PARAM} is {@code false}. Only the namespace and
   * service-account name are compared; trust domain and issuer are not, since the two subject forms
   * carry no comparable value for them.
   */
  private Optional<String> actSubMatchFailureReason(final Map<String, Object> mostRecentActor, final SpiffeId source) {
    final Object subClaim = mostRecentActor.get(JWT.SUBJECT);
    final String sub = subClaim instanceof String ? (String) subClaim : null;
    final Optional<ServiceAccountSubject> actorSubject = ServiceAccountSubject.parse(sub);
    if (actorSubject.isEmpty()) {
      return enforceActSubIsServiceAccount
          ? Optional.of("Most recent actor subject is not a k8s service account subject: " + sub)
          : Optional.empty();
    }
    final String actorNamespace = actorSubject.get().namespace().toLowerCase(Locale.ROOT);
    final String actorName = actorSubject.get().name().toLowerCase(Locale.ROOT);
    // source's fields are already lower-cased by sourceSpiffeIdFromHeader.
    final String sourceNamespace = source.namespace();
    final String sourceServiceAccount = source.serviceAccount();
    if (actorNamespace.equals(sourceNamespace) && actorName.equals(sourceServiceAccount)) {
      return Optional.empty();
    }
    return Optional.of("Most recent actor service account " + actorNamespace + ":" + actorName
        + " does not match source SPIFFE id service account " + sourceNamespace + ":" + sourceServiceAccount);
  }

  @Override
  public String getName() {
    return VALIDATION_METHOD_VALUE;
  }

  private record ClusterDomain(String host, int port) {
    /**
     * Parses an admin-configured allow-list entry the same way
     * {@link AudienceResource#parseCustomForm(String, String)} parses an {@code aud} entry's
     * authority -- by handing it to {@link URI} rather than
     * hand-validating its characters -- since this value is only ever compared against a parsed
     * {@code aud} entry's host and port, never validated on its own.
     */
    static Optional<ClusterDomain> parse(final String entry) {
      if (entry == null) {
        return Optional.empty();
      }
      final String value = entry.trim();
      if (value.isEmpty()) {
        return Optional.empty();
      }
      final URI uri;
      try {
        uri = new URI("https://" + value);
      } catch (final URISyntaxException e) {
        return Optional.empty();
      }
      if (uri.getHost() == null || uri.getUserInfo() != null || !uri.getRawPath().isEmpty()) {
        return Optional.empty();
      }
      final int explicitPort = uri.getPort();
      final int port = explicitPort < 0 ? AudienceResource.DEFAULT_HTTPS_PORT : explicitPort;
      return Optional.of(new ClusterDomain(uri.getHost().toLowerCase(Locale.ROOT), port));
    }
  }

  private static String blankToNull(final String value) {
    return (value == null || value.trim().isEmpty()) ? null : value.trim();
  }

  private static String paramOrDefault(final FilterConfig filterConfig, final String name, final String defaultValue) {
    final String value = filterConfig.getInitParameter(name);
    return (value == null || value.isEmpty()) ? defaultValue : value;
  }

  /**
   * Splits on {@code ,} and trims each entry, but does not drop an entry that trims to empty --
   * for example a stray {@code ,,} or a trailing {@code ,} in {@link #CLUSTER_DOMAINS_PARAM}.
   * This is operator-supplied configuration, not request data, so it is validated strictly: an
   * empty entry is passed through to {@link ClusterDomain#parse(String)}, which rejects it, the
   * same as any other malformed entry, rather than being silently discarded here.
   */
  private static List<String> parseCommaSeparated(final String commaSeparatedList) {
    return Arrays.stream(commaSeparatedList.split(","))
        .map(String::trim)
        .collect(Collectors.toList());
  }
}
