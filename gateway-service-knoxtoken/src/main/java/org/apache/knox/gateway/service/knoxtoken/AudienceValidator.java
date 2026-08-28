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

import java.util.List;


interface AudienceValidator {

    String name();

    default boolean requiresConfiguredAudiences() {
        return false;
    }

    List<String> validateAndResolve(AudienceValidationContext context) throws RequestedAudienceValidationException;

    static AudienceValidator forName(String name) {
        final String selected = (name == null || name.trim().isEmpty()) ? StaticAudienceValidator.NAME : name.trim();
        if (StaticAudienceValidator.NAME.equalsIgnoreCase(selected)) {
            return new StaticAudienceValidator();
        }
        if (WhitelistAudienceValidator.NAME.equalsIgnoreCase(selected)) {
            return new WhitelistAudienceValidator();
        }
        throw new IllegalArgumentException("Unknown audience validator '" + selected + "'. Supported validators: "
                + StaticAudienceValidator.NAME + ", " + WhitelistAudienceValidator.NAME + '.');
    }
}
