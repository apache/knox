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
package org.apache.knox.gateway.topology.builder;

import java.util.ArrayList;
import java.util.List;

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.GatewayResources;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.topology.builder.property.Property;
import org.apache.knox.gateway.topology.builder.property.interpreter.InterpretException;
import org.apache.knox.gateway.topology.builder.property.interpreter.PropertyInterpreter;

public class PropertyTopologyBuilder implements TopologyBuilder {

    private static GatewayMessages log = MessagesFactory.get(GatewayMessages.class);
    private static GatewayResources gatewayResources = ResourcesFactory.get(GatewayResources.class);

    private List<Property> properties;

    public PropertyTopologyBuilder() {
        properties = new ArrayList<>();
    }

    public PropertyTopologyBuilder addProperty(Property property) {
        properties.add(property);
        return this;
    }

    public List<Property> properties() {
        return properties;
    }

    public Topology build() {
        Topology topology = new Topology();
        PropertyInterpreter propertyInterpreter = new PropertyInterpreter(topology);
        for (Property property : properties) {
            try {
                propertyInterpreter.interpret(property.getName(), property.getValue());
            } catch (InterpretException ie) {
                log.failedToInterpretProperty(property.getName(), ie);
                throw new IllegalArgumentException(gatewayResources.wrongTopologyDataFormatError());
            }
        }
        return topology;
    }
}
