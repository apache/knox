package org.apache.knox.gateway.webshell;

import com.nimbusds.jose.JWSHeader;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
//todo: add gateway-provider-security-jwt dependency?
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;
import org.apache.knox.gateway.provider.federation.jwt.filter.SignatureVerificationCache;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.util.CertificateUtils;
import org.apache.knox.gateway.util.Tokens;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import java.net.HttpCookie;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class WebShellTokenUtils {
    private static final String KNOXSSO_COOKIE_NAME = "knoxsso.cookie.name";
    private static final String DEFAULT_SSO_COOKIE_NAME = "hadoop-jwt";
    private static final String JWT_DEFAULT_ISSUER = "KNOXSSO";
    private static final String JWT_EXPECTED_ISSUER = "jwt.expected.issuer";
    private static final String JWT_DEFAULT_SIGALG = "RS256";
    private static final String JWT_EXPECTED_SIGALG = "jwt.expected.sigalg";
    private static final String SSO_VERIFICATION_PEM = "sso.token.verification.pem";

    private static JWTMessages log = MessagesFactory.get(JWTMessages.class);
    private static String cookieName;
    private static String expectedIssuer;
    private static String expectedSigAlg;
    private static TokenStateService tokenStateService;
    private static JWTokenAuthority authorityService;
    private static RSAPublicKey publicKey;
    // todo: how to initialize these two attributes.
    private static SignatureVerificationCache signatureVerificationCache;
    private static String expectedJWKSUrl;

    public static void validateJWT(ServletUpgradeRequest req, GatewayServices services, GatewayConfig gatewayConfig) throws UnknownTokenException {
        configureParameters(services, gatewayConfig);
        List<HttpCookie> ssoCookies = req.getCookies();
        for (HttpCookie ssoCookie : ssoCookies) {
            if (cookieName.equals(ssoCookie.getName())) {
                try {
                    JWT token = new JWTToken(ssoCookie.getValue());
                    if (validateToken(token)) {
                        // we found a valid cookie we don't need to keep checking anymore
                        return;
                    }
                } catch (ParseException | UnknownTokenException ignore) {
                    // Ignore the error since cookie was invalid
                    // Fall through to keep checking if there are more cookies
                }
            }
        }
        // no cookie contain a valid JWT
        throw new UnknownTokenException("No valid JWT found for webshell");
    }

    private static void configureParameters(GatewayServices services, GatewayConfig gatewayConfig) {
        Map<String, String> params = null;
        TopologyService ts = services.getService(ServiceType.TOPOLOGY_SERVICE);
        for (Topology topology : ts.getTopologies()) {
            if (topology.getName().equals("knoxsso")) {
                for (Service service : topology.getServices()) {
                    if (service.getRole().equals("KNOXSSO")) {
                        params = service.getParams();
                        cookieName = params.get(KNOXSSO_COOKIE_NAME);
                        if (cookieName == null) {
                            cookieName = DEFAULT_SSO_COOKIE_NAME;
                        }
                        expectedIssuer = params.get(JWT_EXPECTED_ISSUER);
                        if (expectedIssuer == null) {
                            expectedIssuer = JWT_DEFAULT_ISSUER;
                        }
                        expectedSigAlg = params.get(JWT_EXPECTED_SIGALG);
                        if (expectedSigAlg == null) {
                            expectedSigAlg = JWT_DEFAULT_SIGALG;
                        }
                        // token verification pem
                        String verificationPEM = params.get(SSO_VERIFICATION_PEM);
                        // setup the public key of the token issuer for verification
                        if (verificationPEM != null) {
                            // todo: what to do with ServletException
                            publicKey = CertificateUtils.parseRSAPublicKey(verificationPEM);
                        }
                        // todo: how to initialize SignatureVerificationCache (needs FilterConfig)
                    }
                    break;
                }
                break;
            }
        }
        // todo: how to handle if params == null
        authorityService = services.getService(ServiceType.TOKEN_SERVICE);
        if (isServerManagedTokenStateEnabled(params, gatewayConfig)) {
            tokenStateService = services.getService(ServiceType.TOKEN_STATE_SERVICE);
        }
    }

    /**
     * Determine if server-managed token state is enabled for a provider, based on configuration.
     * The analysis includes checking the provider params and the gateway configuration.
     */
    // todo: needs review. I modified this method from the same method in TokenUtils, but with different parameters
    private static boolean isServerManagedTokenStateEnabled(Map<String, String> params, GatewayConfig gatewayConfig) {
        boolean isServerManaged = false;
        // First, check for explicit provider-level configuration
        String providerParamValue = params.get(TokenStateService.CONFIG_SERVER_MANAGED);

        // If there is no provider-level configuration
        if (providerParamValue == null || providerParamValue.isEmpty()) {
            // Fall back to the gateway-level default
            isServerManaged = (gatewayConfig != null) && gatewayConfig.isServerManagedTokenStateEnabled();
        } else {
            // Otherwise, apply the provider-level configuration
            isServerManaged = Boolean.valueOf(providerParamValue);
        }

        return isServerManaged;
    }

    private static boolean validateToken(JWT token) throws UnknownTokenException {
        final String tokenId = TokenUtils.getTokenId(token);
        final String displayableTokenId = Tokens.getTokenIDDisplayText(tokenId);
        final String displayableToken = Tokens.getTokenDisplayText(token.toString());
        // confirm that issuer matches the intended target
        if (expectedIssuer.equals(token.getIssuer())) {
            // if there is no expiration data then the lifecycle is tied entirely to
            // the cookie validity - otherwise ensure that the current time is before
            // the designated expiration time
            if (tokenIsStillValid(token)) {
                // skipped validating audience
                Date nbf = token.getNotBeforeDate();
                if (nbf == null || new Date().after(nbf)) {
                    if (isTokenEnabled(tokenId)) {
                        if (verifyTokenSignature(token)) {
                            return true;
                        } else {
                            log.failedToVerifyTokenSignature(displayableToken, displayableTokenId);
                        }
                    } else {
                        log.disabledToken(displayableTokenId);
                    }
                } else {
                    log.notBeforeCheckFailed();
                }
            } else {
                log.tokenHasExpired(displayableToken, displayableTokenId);
                // Explicitly evict the record of this token's signature verification (if present).
                // There is no value in keeping this record for expired tokens, and explicitly removing them may prevent
                // records for other valid tokens from being prematurely evicted from the cache.
                removeSignatureVerificationRecord(token.toString());
            }
        }
        return false;
    }

    private static boolean tokenIsStillValid(final JWT jwtToken) throws UnknownTokenException {
        Date expires = getServerManagedStateExpiration(TokenUtils.getTokenId(jwtToken));
        if (expires == null) {
            // if there is no expiration date then the lifecycle is tied entirely to
            // the cookie validity - otherwise ensure that the current time is before
            // the designated expiration time
            expires = jwtToken.getExpiresDate();
        }
        return expires == null || new Date().before(expires);
    }

    private static Date getServerManagedStateExpiration(final String tokenId) throws UnknownTokenException {
        Date expires = null;
        if (tokenStateService != null) {
            long value = tokenStateService.getTokenExpiration(tokenId);
            if (value > 0) {
                expires = new Date(value);
            }
        }
        return expires;
    }

    private static boolean isTokenEnabled(String tokenId) throws UnknownTokenException {
        final TokenMetadata tokenMetadata = tokenStateService == null ? null : tokenStateService.getTokenMetadata(tokenId);
        return tokenMetadata == null ? true : tokenMetadata.isEnabled();
    }


    private static boolean verifyTokenSignature(final JWT token) {
        boolean verified;
        final String serializedJWT = token.toString();
        // Check if the token has already been verified
        verified = hasSignatureBeenVerified(serializedJWT);
        // If it has not yet been verified, then perform the verification now
        if (!verified) {
            try {
                if (publicKey != null) {
                    verified = authorityService.verifyToken(token, publicKey);
                } else if (expectedJWKSUrl != null) { //todo: what is expectedJWKSUrl? how to assign it.
                    verified = authorityService.verifyToken(token, expectedJWKSUrl, expectedSigAlg);
                } else {
                    verified = authorityService.verifyToken(token);
                }
            } catch (TokenServiceException e) {
                log.unableToVerifyToken(e);
            }
            // Check received signature algorithm if expectation is configured
            if (verified && expectedSigAlg != null) {
                try {
                    final String receivedSigAlg = JWSHeader.parse(token.getHeader()).getAlgorithm().getName();
                    if (!receivedSigAlg.equals(expectedSigAlg)) {
                        verified = false;
                    }
                } catch (ParseException e) {
                    log.unableToVerifyToken(e);
                    verified = false;
                }
            }
            if (verified) { // If successful, record the verification for future reference
                recordSignatureVerification(serializedJWT);
            }
        }
        return verified;
    }

    private static boolean hasSignatureBeenVerified(final String token) {
        return signatureVerificationCache.hasSignatureBeenVerified(token);
    }
    private static void recordSignatureVerification(final String token) {
        signatureVerificationCache.recordSignatureVerification(token);
    }

    private static void removeSignatureVerificationRecord(final String token) {
        signatureVerificationCache.removeSignatureVerificationRecord(token);
    }
}
