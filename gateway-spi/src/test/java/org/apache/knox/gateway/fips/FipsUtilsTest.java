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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FipsUtilsTest {

    @Test
    public void testIsFipsEnabledWithBCProviderEmpty() {
        System.clearProperty(FipsUtils.FIPS_SYSTEM_PROPERTY);
        assertFalse(FipsUtils.isFipsEnabledWithBCProvider());
    }

    @Test
    public void testIsFipsEnabledWithBCProviderSetToTrue() {
        try {
            System.setProperty(FipsUtils.FIPS_SYSTEM_PROPERTY, "true");
            assertTrue(FipsUtils.isFipsEnabledWithBCProvider());
        } finally {
            System.clearProperty(FipsUtils.FIPS_SYSTEM_PROPERTY);
        }
    }

    @Test
    public void testIsFipsEnabledWithBCProviderSetToFalse() {
        try {
            System.setProperty(FipsUtils.FIPS_SYSTEM_PROPERTY, "false");
            assertFalse(FipsUtils.isFipsEnabledWithBCProvider());
        } finally {
            System.clearProperty(FipsUtils.FIPS_SYSTEM_PROPERTY);
        }
    }

    @Test
    public void testValidateAlgorithm() {
        try {
            System.setProperty(FipsUtils.FIPS_SYSTEM_PROPERTY, "true");
            final String[] forbiddenAlgorithms = {"MD5", "RC4", "ARC4", "ARCFOUR", "SHA1", "SHA-1"};
            for (String algorithm : forbiddenAlgorithms) {
                testForbiddenAlgorithm(algorithm);
            }
        } finally {
            System.clearProperty(FipsUtils.FIPS_SYSTEM_PROPERTY);
        }
    }

    private void testForbiddenAlgorithm(String algorithm) {
        assertThrows(
                "Should have thrown IllegalArgumentException for " + algorithm,
                IllegalArgumentException.class,
                () -> FipsUtils.validateAlgorithm(algorithm, null)
        );
    }
}
