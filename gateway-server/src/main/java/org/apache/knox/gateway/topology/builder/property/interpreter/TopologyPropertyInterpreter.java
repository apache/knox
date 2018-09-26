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
import org.apache.knox.gateway.topology.Topology;

public class TopologyPropertyInterpreter extends AbstractInterpreter {

    private static final String TOPOLOGY_NAME = "name";
    private static final String AGGREGATOR_GATEWAY = "gateway";
    private static final String AGGREGATOR_SERVICE = "service";

    private static GatewayResources gatewayResources = ResourcesFactory.get(GatewayResources.class);

    private Topology topology;

    public TopologyPropertyInterpreter(Topology topology) {
        if (topology == null) {
            throw new IllegalArgumentException(gatewayResources.topologyIsRequiredError());
        }
        this.topology = topology;
    }

    @Override
    public void interpret(String token, String value) throws InterpretException {
        if (TOPOLOGY_NAME.equalsIgnoreCase(token)) {
            topology.setName(value);
        } else {
            int firstDotPosition = token.indexOf(DOT);
            if (firstDotPosition != -1) {
                String aggregator = token.substring(0, firstDotPosition);
                String nextToken = token.substring(firstDotPosition + 1);

                if (AGGREGATOR_GATEWAY.equals(aggregator)) {
                    new GatewayPropertyInterpreter(topology).interpret(nextToken, value);
                } else if (AGGREGATOR_SERVICE.equals(aggregator)) {
                    new ServicePropertyInterpreter(topology).interpret(nextToken, value);
                } else {
                    throw new InterpretException(gatewayResources.unsupportedPropertyTokenError(token));
                }
            } else {
                throw new InterpretException(gatewayResources.unsupportedPropertyTokenError(token));
            }
        }
    }
}
