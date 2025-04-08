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
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.impl.TokenMAC;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.ws.rs.core.Response;
import java.util.UUID;

public class PasscodeTokenResourceBase extends TokenResource {
    protected GatewayServices services;
    @Override
    protected ServletContext wrapContextForDefaultParams(ServletContext context) throws ServletException {
        ServletContext wrapperContext = new ServletContextWrapper(context);
        wrapperContext.setInitParameter(TokenStateService.CONFIG_SERVER_MANAGED, "true");
        wrapperContext.setInitParameter(TokenResource.TOKEN_TTL_PARAM, "-1");
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
        return wrapperContext;
    }

    private void generateAndStoreHMACKeyAlias() {
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
}
