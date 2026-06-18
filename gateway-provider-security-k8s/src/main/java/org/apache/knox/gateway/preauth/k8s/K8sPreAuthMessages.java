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
package org.apache.knox.gateway.preauth.k8s;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger = "org.apache.knox.gateway.preauth.k8s")
public interface K8sPreAuthMessages {

    @Message(level = MessageLevel.WARN, text = "Rejecting request: SPIFFE header ''{0}'' is missing")
    void missingSpiffeHeader(String headerName);

    @Message(level = MessageLevel.WARN, text = "Rejecting request: user header ''{0}'' is missing")
    void missingUserHeader(String headerName);

    @Message(level = MessageLevel.WARN,
            text = "Rejecting request: SPIFFE header value ''{0}'' is not a parseable k8s SPIFFE ID (asserted user ''{1}'')")
    void unparseableSpiffeId(String spiffeRaw, String assertedUser);

    @Message(level = MessageLevel.WARN,
            text = "Rejecting request: ServiceAccount {0}/{1} has no ''{2}'' annotation (asserted user ''{3}'', SPIFFE ID ''{4}'')")
    void missingServiceAccountAnnotation(String namespace,
                                         String serviceAccount,
                                         String annotationKey,
                                         String assertedUser,
                                         String spiffeId);

    @Message(level = MessageLevel.WARN,
            text = "Rejecting request: asserted user ''{0}'' does not match ServiceAccount {1}/{2} ''{3}'' (SPIFFE ID ''{4}'')")
    void assertedUserDoesNotMatchAnnotation(String assertedUser,
                                            String namespace,
                                            String serviceAccount,
                                            String annotationKey,
                                            String spiffeId);

    @Message(level = MessageLevel.ERROR,
            text = "Failed to load ServiceAccount {0}/{1} from Kubernetes API: {2}")
    void failedToLoadServiceAccount(String namespace,
                                    String serviceAccount,
                                    @StackTrace(level = MessageLevel.ERROR) Throwable e);
}
