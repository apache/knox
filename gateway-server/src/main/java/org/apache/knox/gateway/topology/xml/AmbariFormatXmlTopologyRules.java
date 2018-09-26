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
package org.apache.knox.gateway.topology.xml;

import org.apache.commons.digester3.binder.AbstractRulesModule;
import org.apache.knox.gateway.topology.builder.PropertyTopologyBuilder;
import org.apache.knox.gateway.topology.builder.property.Property;

public class AmbariFormatXmlTopologyRules extends AbstractRulesModule {

    private static final String ROOT_TAG = "configuration";
    private static final String PROPERTY_TAG = "property";
    private static final String NAME_TAG = "name";
    private static final String VALUE_TAG = "value";

    @Override
    protected void configure() {
        forPattern(ROOT_TAG).createObject().ofType(PropertyTopologyBuilder.class);
        forPattern(ROOT_TAG + "/" + PROPERTY_TAG).createObject().ofType(Property.class).then().setNext("addProperty");
        forPattern(ROOT_TAG + "/" + PROPERTY_TAG + "/" + NAME_TAG).setBeanProperty();
        forPattern(ROOT_TAG + "/" + PROPERTY_TAG + "/" + VALUE_TAG).setBeanProperty();
    }
}
