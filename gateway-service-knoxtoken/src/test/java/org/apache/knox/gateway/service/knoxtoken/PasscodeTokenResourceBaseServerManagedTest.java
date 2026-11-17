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
package org.apache.knox.gateway.service.knoxtoken;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.impl.TokenMAC;
import org.easymock.EasyMock;
import org.junit.Test;

import jakarta.servlet.ServletContext;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PasscodeTokenResourceBaseServerManagedTest {

    private static final String SERVER_MANAGED = TokenStateService.CONFIG_SERVER_MANAGED;

    private final TokenStateService stateService = EasyMock.createNiceMock(TokenStateService.class);
    private AliasService aliasService;

    // The hash key alias is ensured only with token state; the strict alias mock fails on any other lookup.
    private ServletContext topologyContext(Map<String, String> params, boolean gatewayDefault, boolean tokenState)
            throws Exception {
        aliasService = EasyMock.createMock(AliasService.class);
        if (tokenState) {
            EasyMock.expect(aliasService.getPasswordFromAliasForGateway(TokenMAC.KNOX_TOKEN_HASH_KEY_ALIAS_NAME))
                    .andReturn("hash-key".toCharArray()).atLeastOnce();
        }
        final GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
        EasyMock.expect(services.getService(ServiceType.TOKEN_STATE_SERVICE)).andReturn(stateService).anyTimes();
        EasyMock.expect(services.getService(ServiceType.ALIAS_SERVICE)).andReturn(aliasService).anyTimes();
        final GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(config.isServerManagedTokenStateEnabled()).andReturn(gatewayDefault).anyTimes();
        final ServletContext context = EasyMock.createNiceMock(ServletContext.class);
        EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services).anyTimes();
        EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(config).anyTimes();
        EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn("sandbox").anyTimes();
        EasyMock.expect(context.getInitParameterNames()).andAnswer(() -> Collections.enumeration(params.keySet())).anyTimes();
        EasyMock.expect(context.getInitParameter(EasyMock.anyString()))
                .andAnswer(() -> params.get((String) EasyMock.getCurrentArguments()[0])).anyTimes();
        EasyMock.replay(aliasService, services, config, context);
        return context;
    }

    private ServletContext topologyContext(Map<String, String> params, boolean tokenState) throws Exception {
        return topologyContext(params, false, tokenState);
    }

    private void wrap(PasscodeTokenResourceBase resource, ServletContext context) throws Exception {
        resource.wrapContextForDefaultParams(context);
        EasyMock.verify(aliasService);
    }

    @Test
    public void testTokenStateIsUsedWhenServerManagedIsNotDeclared() throws Exception {
        for (PasscodeTokenResourceBase resource : new PasscodeTokenResourceBase[] {
                new PasscodeTokenResourceBase(), new OAuthResource(), new APIKeyResource(), new ClientCredentialsResource() }) {
            wrap(resource, topologyContext(Collections.emptyMap(), true));
            assertSame(resource.getClass().getSimpleName(), stateService, resource.tokenStateService);
        }
    }

    @Test
    public void testTokenStateIsUsedWhenServerManagedIsTrue() throws Exception {
        final PasscodeTokenResourceBase resource = new PasscodeTokenResourceBase();
        wrap(resource, topologyContext(Map.of(SERVER_MANAGED, "true"), true));
        assertSame(stateService, resource.tokenStateService);
    }

    @Test
    public void testTokenStateIsDroppedWhenServerManagedIsFalse() throws Exception {
        for (PasscodeTokenResourceBase resource : new PasscodeTokenResourceBase[] {
                new PasscodeTokenResourceBase(), new OAuthResource(), new APIKeyResource(), new ClientCredentialsResource() }) {
            wrap(resource, topologyContext(Map.of(SERVER_MANAGED, "false"), false));
            assertNull(resource.getClass().getSimpleName(), resource.tokenStateService);
        }
    }

    // A blank value falls back to the gateway-level default, as in TokenResource#init, so the token state service
    // is set exactly when the token MAC is.
    @Test
    public void testBlankServerManagedFallsBackToGatewayDefault() throws Exception {
        PasscodeTokenResourceBase resource = new PasscodeTokenResourceBase();
        wrap(resource, topologyContext(Map.of(SERVER_MANAGED, ""), true, true));
        assertSame(stateService, resource.tokenStateService);

        resource = new PasscodeTokenResourceBase();
        wrap(resource, topologyContext(Map.of(SERVER_MANAGED, ""), false, false));
        assertNull(resource.tokenStateService);
    }

    @Test
    public void testPrefixedServerManagedWins() throws Exception {
        APIKeyResource resource = new APIKeyResource();
        wrap(resource, topologyContext(Map.of(SERVER_MANAGED, "false", APIKeyResource.PREFIX + SERVER_MANAGED, "true"), true));
        assertSame(stateService, resource.tokenStateService);
    }

    @Test
    public void testPrefixedServerManagedIsHonoredOnItsOwn() throws Exception {
        APIKeyResource apiKey = new APIKeyResource();
        wrap(apiKey, topologyContext(Map.of(APIKeyResource.PREFIX + SERVER_MANAGED, "false"), false));
        assertNull(apiKey.tokenStateService);

        ClientCredentialsResource clientCredentials = new ClientCredentialsResource();
        wrap(clientCredentials, topologyContext(Map.of("clientid." + SERVER_MANAGED, "false"), false));
        assertNull(clientCredentials.tokenStateService);
    }
}
