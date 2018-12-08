/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.ambari;


class PropertyEqualsHandler implements ConditionalValueHandler {

    private String serviceName;
    private String propertyName;
    private String propertyValue;
    private ConditionalValueHandler affirmativeResult;
    private ConditionalValueHandler negativeResult;

    PropertyEqualsHandler(String                  serviceName,
                          String                  propertyName,
                          String                  propertyValue,
                          ConditionalValueHandler affirmativeResult,
                          ConditionalValueHandler negativeResult) {
        this.serviceName       = serviceName;
        this.propertyName      = propertyName;
        this.propertyValue     = propertyValue;
        this.affirmativeResult = affirmativeResult;
        this.negativeResult    = negativeResult;
    }

    @Override
    public String evaluate(ServiceURLPropertyConfig config, AmbariCluster cluster) {
        String result = null;

        ServiceURLPropertyConfig.Property p = config.getConfigProperty(serviceName, propertyName);
        if (p != null) {

            String value = null;
            if (p.getType().equalsIgnoreCase(ServiceURLPropertyConfig.Property.TYPE_DERIVED)) {
                ConditionalValueHandler valueHandler = p.getConditionHandler();
                if (valueHandler != null) {
                    value = valueHandler.evaluate(config, cluster);
                }
            } else {
                value = getActualPropertyValue(cluster, p);
            }

            if (propertyValue == null) {
                // If the property value isn't specified, then we're just checking if the property is set with any value
                if (value != null) {
                    // So, if there is a value in the config, respond with the affirmative
                    result = affirmativeResult.evaluate(config, cluster);
                } else if (negativeResult != null) {
                    result = negativeResult.evaluate(config, cluster);
                }
            }

            if (result == null) {
                if (propertyValue != null && propertyValue.equals(value)) {
                    result = affirmativeResult.evaluate(config, cluster);
                } else if (negativeResult != null) {
                    result = negativeResult.evaluate(config, cluster);
                }
            }

            // Check if the result is a reference to a local derived property
            ServiceURLPropertyConfig.Property derived = config.getConfigProperty(serviceName, result);
            if (derived != null) {
                result = getActualPropertyValue(cluster, derived);
            }
        }

        return result;
    }

    private String getActualPropertyValue(AmbariCluster cluster, ServiceURLPropertyConfig.Property property) {
        String value = null;
        String propertyType = property.getType();
        if (ServiceURLPropertyConfig.Property.TYPE_COMPONENT.equals(propertyType)) {
            AmbariComponent component = cluster.getComponent(property.getComponent());
            if (component != null) {
                value = component.getConfigProperty(property.getValue());
            }
        } else if (ServiceURLPropertyConfig.Property.TYPE_SERVICE.equals(propertyType)) {
            value = cluster.getServiceConfiguration(property.getService(), property.getServiceConfig()).getProperties().get(property.getValue());
        }
        return value;
    }
}