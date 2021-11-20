package org.apache.knox.gateway.websockets;

import com.nimbusds.jose.JWSHeader;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
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
import org.apache.knox.gateway.util.Tokens;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;

import java.net.HttpCookie;
import java.text.ParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JWTValidator {
    private static final String KNOXSSO_COOKIE_NAME = "knoxsso.cookie.name";
    private static final String DEFAULT_SSO_COOKIE_NAME = "hadoop-jwt";
    private static final String JWT_DEFAULT_ISSUER = "KNOXSSO";
    private static final String JWT_EXPECTED_ISSUER = "jwt.expected.issuer";
    private static final String JWT_DEFAULT_SIGALG = "RS256";
    private static final String JWT_EXPECTED_SIGALG = "jwt.expected.sigalg";
    private static final JWTMessages log = MessagesFactory.get(JWTMessages.class);
    private Map<String,String> params;
    private String cookieName;
    private String expectedIssuer;
    private String expectedSigAlg;
    private TokenStateService tokenStateService;
    private JWTokenAuthority authorityService;
    private SignatureVerificationCache signatureVerificationCache;
    private JWT token;
    private String displayableTokenId;
    private String displayableToken;
    private final GatewayConfig gatewayConfig;
    private final GatewayServices gatewayServices;

    JWTValidator(ServletUpgradeRequest req, GatewayServices gatewayServices,
                 GatewayConfig gatewayConfig){
        this.gatewayConfig = gatewayConfig;
        this.gatewayServices = gatewayServices;
        configureParameters();
        extractToken(req);
    }

    // todo: call this function again if topology is reloaded. needs to verify detail
    private void configureParameters() {
        params = new LinkedHashMap<>();
        TopologyService ts = gatewayServices.getService(ServiceType.TOPOLOGY_SERVICE);
        for (Topology topology : ts.getTopologies()) {
            if (topology.getName().equals("knoxsso")) {
                for (Service service : topology.getServices()) {
                    if (service.getRole().equals("KNOXSSO")) {
                        params = service.getParams();
                    }
                    break;
                }
                break;
            }
        }
        cookieName = params.get(KNOXSSO_COOKIE_NAME);
        if (cookieName == null) {
            cookieName = DEFAULT_SSO_COOKIE_NAME;
        }
        expectedIssuer =  params.get(JWT_EXPECTED_ISSUER);
        if (expectedIssuer == null) {
            expectedIssuer = JWT_DEFAULT_ISSUER;
        }
        expectedSigAlg =  params.get(JWT_EXPECTED_SIGALG);
        if (expectedSigAlg == null) {
            expectedSigAlg = JWT_DEFAULT_SIGALG;
        }
        authorityService = gatewayServices.getService(ServiceType.TOKEN_SERVICE);
        if (isServerManagedTokenStateEnabled()) {
            tokenStateService = gatewayServices.getService(ServiceType.TOKEN_STATE_SERVICE);
        }
        // Setup the verified tokens cache
        signatureVerificationCache = SignatureVerificationCache.getInstance(
                "knoxsso", new WebSocketFilterConfig(params));
    }

    private void extractToken(ServletUpgradeRequest req){
        List<HttpCookie> ssoCookies = req.getCookies();
        for (HttpCookie ssoCookie : ssoCookies) {
            if (cookieName.equals(ssoCookie.getName())) {
                try {
                    token = new JWTToken(ssoCookie.getValue());
                    displayableTokenId = Tokens.getTokenIDDisplayText(TokenUtils.getTokenId(token));
                    displayableToken = Tokens.getTokenDisplayText(token.toString());
                    return;
                } catch (ParseException e) {
                    // Fall through to keep checking if there are more cookies
                }
            }
        }
        log.missingBearerToken();
        throw new RuntimeException("no Valid JWT Token found");
    }

    public JWT getToken(){
        return token;
    }

    public String getUsername(){
        return token.getPrincipal();
    }

    public boolean validate() {
        // todo: call configureParameters() if topology is reloaded
        // confirm that issuer matches the intended target
        if (expectedIssuer.equals(token.getIssuer())) {
            // if there is no expiration data then the lifecycle is tied entirely to
            // the cookie validity - otherwise ensure that the current time is before
            // the designated expiration time
            try {
                if (tokenIsStillValid()) {
                    Date nbf = token.getNotBeforeDate();
                    if (nbf == null || new Date().after(nbf)) {
                        if (isTokenEnabled()){
                            if (verifyTokenSignature()) {
                                return true;
                            }
                        }
                    } else {
                        log.notBeforeCheckFailed();
                    }
                }
            } catch (UnknownTokenException e){
                return false;
            }
        }
        return false;
    }

    // adapted from TokenUtils.isServerManagedTokenStateEnabled(FilterConfig)
    private boolean isServerManagedTokenStateEnabled() {
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

    public boolean tokenIsStillValid() throws UnknownTokenException {
        Date expires = getServerManagedStateExpiration();
        if (expires == null) {
            // if there is no expiration date then the lifecycle is tied entirely to
            // the cookie validity - otherwise ensure that the current time is before
            // the designated expiration time
            expires = token.getExpiresDate();
        }
        if (expires == null || new Date().before(expires)){
            return true;
        } else {
            log.tokenHasExpired(displayableToken, displayableTokenId);
            // Explicitly evict the record of this token's signature verification (if present).
            // There is no value in keeping this record for expired tokens, and explicitly
            // removing them may prevent records for other valid tokens from being prematurely
            // evicted from the cache.
            signatureVerificationCache.removeSignatureVerificationRecord(token.toString());
            return false;
        }
    }

    private Date getServerManagedStateExpiration() throws UnknownTokenException {
        Date expires = null;
        if (tokenStateService != null) {
            long value = tokenStateService.getTokenExpiration(TokenUtils.getTokenId(token));
            if (value > 0) {
                expires = new Date(value);
            }
        }
        return expires;
    }

    private boolean isTokenEnabled() throws UnknownTokenException {
        final TokenMetadata tokenMetadata = tokenStateService == null ? null :
                tokenStateService.getTokenMetadata(TokenUtils.getTokenId(token));
        if (tokenMetadata == null ? true : tokenMetadata.isEnabled()){
            return true;
        } else {
            log.disabledToken(displayableTokenId);
            return false;
        }
    }


    private boolean verifyTokenSignature() {
        boolean verified;
        final String serializedJWT = token.toString();
        // Check if the token has already been verified
        verified = signatureVerificationCache.hasSignatureBeenVerified(serializedJWT);
        // If it has not yet been verified, then perform the verification now
        if (!verified) {
            try {
                verified = authorityService.verifyToken(token);
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
                signatureVerificationCache.recordSignatureVerification(serializedJWT);
            }
        }
        if (!verified){
            log.failedToVerifyTokenSignature(displayableToken, displayableTokenId);
        }
        return verified;
    }
}
