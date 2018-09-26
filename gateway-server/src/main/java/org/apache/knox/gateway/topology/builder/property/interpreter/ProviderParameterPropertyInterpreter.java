/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.builder.property.interpreter;

import org.apache.knox.gateway.GatewayResources;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;
import org.apache.knox.gateway.topology.Param;
import org.apache.knox.gateway.topology.Provider;

public class ProviderParameterPropertyInterpreter extends AbstractInterpreter {

    private static GatewayResources gatewayResources = ResourcesFactory.get(GatewayResources.class);

    private Provider provider;

    public ProviderParameterPropertyInterpreter(Provider provider) {
        if (provider == null) {
            throw new IllegalArgumentException(gatewayResources.providerIsRequiredError());
        }
        this.provider = provider;
    }

    @Override
    public void interpret(String token, String value) throws InterpretException {
        if (token == null || token.isEmpty()) {
            throw new InterpretException(gatewayResources.providerParameterNameIsRequiredError());
        }
        if (value == null || value.isEmpty()) {
            throw new InterpretException(gatewayResources.providerParameterValueIsRequiredError());
        }
        Param param = new Param();
        param.setName(token);
        param.setValue(value);
        provider.addParam( param );
    }
}
