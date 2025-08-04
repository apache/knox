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
package org.apache.knox.gateway.util;

import org.apache.knox.gateway.SanitizedException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ExceptionSanitizerTest {

    private static final String SANITIZATION_PATTERN = "(?i)password=\\w+";

    @Test
    public void testSanitizationEnabledWithSensitiveMessage() {
        Exception input = new Exception("User login failed: password=secret123");
        SanitizedException sanitized = ExceptionSanitizer.from(input, true, SANITIZATION_PATTERN);
        assertEquals("User login failed: [hidden]", sanitized.getMessage());
    }

    @Test
    public void testSanitizationDisabledWithSensitiveMessage() {
        Exception input = new Exception("User login failed: password=secret123");
        SanitizedException sanitized = ExceptionSanitizer.from(input, false, SANITIZATION_PATTERN);
        assertEquals("User login failed: password=secret123", sanitized.getMessage());
    }

    @Test
    public void testNullException() {
        SanitizedException sanitized = ExceptionSanitizer.from(null, true, SANITIZATION_PATTERN);
        assertNull(sanitized.getMessage());
    }

    @Test
    public void testNullMessageInException() {
        Exception input = new Exception((String) null);
        SanitizedException sanitized = ExceptionSanitizer.from(input, true, SANITIZATION_PATTERN);
        assertNull(sanitized.getMessage());
    }

    @Test
    public void testSanitizationEnabledWithNoMatch() {
        Exception input = new Exception("Something went wrong, but no password present.");
        SanitizedException sanitized = ExceptionSanitizer.from(input, true, SANITIZATION_PATTERN);
        assertEquals("Something went wrong, but no password present.", sanitized.getMessage());
    }

    @Test
    public void testSanitizationWithMultipleMatches() {
        Exception input = new Exception("password=one password=two");
        SanitizedException sanitized = ExceptionSanitizer.from(input, true, SANITIZATION_PATTERN);
        assertEquals("[hidden] [hidden]", sanitized.getMessage());
    }
}
