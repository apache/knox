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
package org.apache.knox.gateway.topology.simple;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger="org.apache.gateway.topology.simple")
public interface SimpleDescriptorMessages {

    @Message(level = MessageLevel.ERROR,
            text = "Unable to complete service discovery for cluster {0}.")
    void failedToDiscoverClusterServices(String descriptorName);

    @Message(level = MessageLevel.WARN,
            text = "No valid URLs were discovered for {0} in the {1} cluster.")
    void failedToDiscoverClusterServiceURLs(String serviceName, String clusterName);

    @Message(level = MessageLevel.ERROR,
            text = "Failed to resolve the referenced provider configuration {0}.")
    void failedToResolveProviderConfigRef(String providerConfigRef);

    @Message(level = MessageLevel.ERROR,
            text = "Failed to parse the referenced provider configuration {0}: {1}")
    void failedToParseProviderConfig(String providerConfigRef,
                                     @StackTrace( level = MessageLevel.DEBUG ) Exception e);

    @Message(level = MessageLevel.ERROR,
            text = "URL validation failed for {0} URL {1} : {2}")
    void serviceURLValidationFailed(String serviceName,
                                    String url,
                                    @StackTrace( level = MessageLevel.DEBUG ) Exception e);

    @Message(level = MessageLevel.ERROR,
            text = "Error generating topology {0} from simple descriptor: {1}")
    void failedToGenerateTopologyFromSimpleDescriptor(String topologyFile,
                                                      @StackTrace( level = MessageLevel.DEBUG ) Exception e);

    @Message(level = MessageLevel.ERROR,
            text = "Error creating a password for query string encryption for {0}: {1}" )
    void exceptionCreatingPasswordForEncryption(String topologyName,
                                                @StackTrace( level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.ERROR,
            text = "Failed to create a password for query string encryption for {0}." )
    void unableCreatePasswordForEncryption(String topologyName);

    @Message(level = MessageLevel.ERROR,
        text = "Error comparing the generated {0} topology with the existing version: {1}" )
    void errorComparingGeneratedTopology(String topologyName,
                                         @StackTrace( level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.INFO,
            text = "Persisting the generated {0} topology because it either does not exist or it has changed." )
    void persistingGeneratedTopology(String topologyName);

    @Message(level = MessageLevel.INFO,
            text = "Skipping redeployment of the {0} topology because it already exists and has not changed." )
    void skippingDeploymentOfGeneratedTopology(String topologyName);

}
