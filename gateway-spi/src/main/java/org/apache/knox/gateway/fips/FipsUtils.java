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
package org.apache.knox.gateway.fips;

import org.apache.knox.gateway.config.GatewayConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class FipsUtils {

    public static final String FIPS_SYSTEM_PROPERTY = "com.safelogic.cryptocomply.fips.approved_only";

    private static final List<String> FORBIDDEN_ALGORITHMS = Arrays.asList("MD5", "RC4", "ARC4", "ARCFOUR", "SHA-1", "SHA1");
    public static final String PROHIBITED_ALGORITHM_TEMPLATE = "In a FIPS environment, you are not allowed to use %s as %s";

    public static boolean isFipsEnabledWithBCProvider() {
        return Boolean.parseBoolean(System.getProperty(FIPS_SYSTEM_PROPERTY));
    }

    /**
     * Validates if the given algorithm is allowed in a FIPS environment.
     *
     * @param algorithm The algorithm to validate.
     * @return true if the algorithm is allowed or FIPS is not enabled, false otherwise.
     */
    private static boolean isAlgorithmAllowed(String algorithm) {
        return !isFipsEnabledWithBCProvider() || algorithm == null
                || algorithm.isEmpty() || !FORBIDDEN_ALGORITHMS.contains(algorithm.toUpperCase(Locale.ROOT));
    }

    public static void validateAlgorithm(String algorithm, String paramName) {
        if (!isAlgorithmAllowed(algorithm)) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, PROHIBITED_ALGORITHM_TEMPLATE, algorithm, paramName));
        }
    }

    public static void validateAlgorithms(GatewayConfig config) {
        config.getCredentialStoreAlgorithm();
        config.getAlgorithm();
        config.getPBEAlgorithm();
    }
}
