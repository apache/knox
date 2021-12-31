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
package org.apache.knox.gateway.websockets;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;
import org.apache.knox.gateway.provider.federation.jwt.filter.SignatureVerificationCache;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.util.CertificateUtils;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import javax.servlet.ServletException;
import java.net.HttpCookie;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JWTValidatorFactory {
    private static final String KNOXSSO_COOKIE_NAME = "knoxsso.cookie.name";
    private static final String DEFAULT_SSO_COOKIE_NAME = "hadoop-jwt";
    private static final String JWT_EXPECTED_ISSUER = "jwt.expected.issuer";
    private static final String JWT_EXPECTED_SIGALG = "jwt.expected.sigalg";
    public static final String SSO_VERIFICATION_PEM = "sso.token.verification.pem";
    private static final JWTMessages jwtMessagesLog = MessagesFactory.get(JWTMessages.class);

    public static JWTValidator create(ServletUpgradeRequest req, GatewayServices gatewayServices,
                                      GatewayConfig gatewayConfig){
        Map<String,String> params = getParams(gatewayServices);
        String cookieName = params.containsKey(KNOXSSO_COOKIE_NAME)? params.get(KNOXSSO_COOKIE_NAME):DEFAULT_SSO_COOKIE_NAME;
        // instantiate jwtValidator with required parameters
        JWTValidator jwtValidator = new JWTValidator(
                extractToken(req, cookieName),
                gatewayServices.getService(ServiceType.TOKEN_SERVICE),
                SignatureVerificationCache.getInstance(
                        "knoxsso", new WebSocketFilterConfig(params)));
        // set optional parameters
        if (params.containsKey(SSO_VERIFICATION_PEM)){
            try {
                RSAPublicKey publicKey = CertificateUtils.parseRSAPublicKey(params.get(SSO_VERIFICATION_PEM));
                jwtValidator.setPublicKey(publicKey);
            } catch (ServletException e){
                throw new RuntimeException("Failed to obtain public key: "+e);
            }
        }

        if (params.containsKey(JWT_EXPECTED_ISSUER)){
            jwtValidator.setExpectedIssuer(params.get(JWT_EXPECTED_ISSUER));
        }
        if (params.containsKey(JWT_EXPECTED_SIGALG)){
            jwtValidator.setExpectedSigAlg(params.get(JWT_EXPECTED_SIGALG));
        }

        if (isServerManagedTokenStateEnabled(gatewayConfig, params.get(TokenStateService.CONFIG_SERVER_MANAGED))
        ) {
            jwtValidator.setTokenStateService(gatewayServices.getService(ServiceType.TOKEN_STATE_SERVICE));
        }

        return jwtValidator;
    }

    private static Map<String,String> getParams(GatewayServices gatewayServices){
        Map<String,String> params = new LinkedHashMap<>();
        TopologyService ts = gatewayServices.getService(ServiceType.TOPOLOGY_SERVICE);
        for (Topology topology : ts.getTopologies()) {
            if (topology.getName().equals("knoxsso")) {
                for (Service service : topology.getServices()) {
                    if (service.getRole().equals("KNOXSSO")) {
                        return service.getParams();
                    }
                }
            }
        }
        return params;
    }

    private static JWT extractToken(ServletUpgradeRequest req, String cookieName){
        List<HttpCookie> ssoCookies = req.getCookies();
        if (ssoCookies != null){
            for (HttpCookie ssoCookie : ssoCookies) {
                if (cookieName.equals(ssoCookie.getName())) {
                    try {
                        return new JWTToken(ssoCookie.getValue());
                    } catch (ParseException e) {
                        // Fall through to keep checking if there are more cookies
                    }
                }
            }
        }
        jwtMessagesLog.missingBearerToken();
        throw new RuntimeException("No Valid JWT found");
    }

    // adapted from TokenUtils.isServerManagedTokenStateEnabled(FilterConfig)
    private static boolean isServerManagedTokenStateEnabled(GatewayConfig gatewayConfig, String providerParamValue) {
        boolean isServerManaged = false;
        // If there is no provider-level configuration
        if (providerParamValue == null || providerParamValue.isEmpty()) {
            // Fall back to the gateway-level default
            isServerManaged = (gatewayConfig != null) && gatewayConfig.isServerManagedTokenStateEnabled();
        } else {
            // Otherwise, apply the provider-level configuration
            isServerManaged = Boolean.parseBoolean(providerParamValue);
        }
        return isServerManaged;
    }

}
