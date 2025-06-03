/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file to
 * you under the Apache License, Version 2.0 (the
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
package org.apache.knox.gateway.service.knoxtoken;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.gen.OctetSequenceKeyGenerator;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.impl.TokenMAC;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;

public class PasscodeTokenResourceBase extends TokenResource {
    protected GatewayServices services;
    private static TokenServiceMessages log = MessagesFactory.get(TokenServiceMessages.class);

    protected void addExpiryIfNotNever(Map<String, Object> map) {
        long expiresIn = getTokenLifetimeInSeconds();
        if (expiresIn != -1) {
            map.put(EXPIRES_IN, expiresIn);
        }
    }

    @Override
    protected ServletContext wrapContextForDefaultParams(ServletContext context) throws ServletException {
        ServletContextWrapper wrapperContext = new ServletContextWrapper(context);
        wrapperContext.setInitParameter(getPrefix() + TokenResource.TOKEN_TTL_PARAM, "-1");
        // let's translate API extension param names into what is expected by the KNOXTOKEN API parent:
        // find all params that start with the extension specific prefix and remove the prefix, setting the value
        // for the expected param name.
        setExpectedParamsFromExtensionParams(wrapperContext);
        setupTokenStateService(wrapperContext);
        return wrapperContext;
    }

    private void setExpectedParamsFromExtensionParams(ServletContextWrapper context) {
        String prefix = getPrefix();
        if (prefix == null) {
            return;
        }

        Enumeration<String> names = context.getInitParameterNames();
        String name;
        int start;
        while(names.hasMoreElements()) {
            name = names.nextElement();
            if (name.startsWith(prefix)) {
                start = prefix.indexOf('.');
                if (start != -1) {
                    context.setExtensionParameter(name.substring(start + 1), context.getInitParameter(name));
                }
            }
        }
    }

    public String getPrefix() {
        return null;
    }

    protected void setupTokenStateService(ServletContext wrapperContext) throws ServletException {
        wrapperContext.setInitParameter(TokenStateService.CONFIG_SERVER_MANAGED, "true");
        services = (GatewayServices) wrapperContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
        tokenStateService = services.getService(ServiceType.TOKEN_STATE_SERVICE);
        final GatewayConfig gatewayConfig = (GatewayConfig) wrapperContext.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
        gatewayConfig.getKnoxTokenHashAlgorithm();
        final AliasService aliasService = services.getService(ServiceType.ALIAS_SERVICE);
        char[] hashkey;
        try {
            hashkey = aliasService.getPasswordFromAliasForGateway(TokenMAC.KNOX_TOKEN_HASH_KEY_ALIAS_NAME);
        } catch (AliasServiceException e) {
            throw new ServletException(e);
        }
        if (hashkey == null) {
            generateAndStoreHMACKeyAlias();
        }
    }

    protected void generateAndStoreHMACKeyAlias() {
        final int keyLength = Integer.parseInt(JWSAlgorithm.HS256.getName().substring(2));
        String jwkAsText = null;
        try {
            final OctetSequenceKey jwk = new OctetSequenceKeyGenerator(keyLength).keyID(UUID.randomUUID().toString())
                    .algorithm(JWSAlgorithm.HS256).generate();
            jwkAsText = jwk.getKeyValue().toJSONString().replace("\"", "");
            getAliasService().addAliasForCluster("__gateway", TokenMAC.KNOX_TOKEN_HASH_KEY_ALIAS_NAME, jwkAsText);
        } catch (JOSEException | AliasServiceException e) {
            throw new RuntimeException("Error while generating " + keyLength + " bits JWK secret", e);
        }
    }

    protected AliasService getAliasService() {
        return services.getService(ServiceType.ALIAS_SERVICE);
    }

    protected Response checkForInvalidRequestResponse(UserContext context) {
        Response response = enforceClientCertIfRequired();
        if (response != null) { return response; }

        response = onlyAllowGroupsToBeAddedWhenEnabled();
        if (response != null) { return response; }

        response = enforceTokenLimitsAsRequired(context.userName);
        if (response != null) { return response; }

        return response;
    }

    protected long getTokenLifetimeInSeconds() {
        long secs = tokenTTL/1000;

        String lifetimeStr = request.getParameter(LIFESPAN);
        if (lifetimeStr == null || lifetimeStr.isEmpty()) {
            if (tokenTTL == -1) {
                return -1;
            }
        }
        else {
            try {
                long lifetime = Duration.parse(lifetimeStr).toMillis()/1000;
                if (tokenTTL == -1) {
                    // if TTL is set to -1 the topology owner grants unlimited lifetime therefore no additional check is needed on lifespan
                    secs = lifetime;
                } else if (lifetime <= tokenTTL/1000) {
                    //this is expected due to security reasons: the configured TTL acts as an upper limit regardless of the supplied lifespan
                    secs = lifetime;
                }
            }
            catch (DateTimeParseException e) {
                log.invalidLifetimeValue(lifetimeStr);
            }
        }
        return secs;
    }
}
