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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TruststorePasswordSetterTest {
  private String originalPassword;

  @Before
  public void setUp() {
    originalPassword = System.getProperty(TruststorePasswordSetter.TRUSTSTORE_PASSWORD_SYSTEM_PROPERTY);
    System.clearProperty(TruststorePasswordSetter.TRUSTSTORE_PASSWORD_SYSTEM_PROPERTY);
  }

  @After
  public void tearDown() {
    if (originalPassword != null) {
      System.setProperty(TruststorePasswordSetter.TRUSTSTORE_PASSWORD_SYSTEM_PROPERTY, originalPassword);
    } else {
      System.clearProperty(TruststorePasswordSetter.TRUSTSTORE_PASSWORD_SYSTEM_PROPERTY);
    }
  }

  @Test
  public void testSetPassword() {
    final String password = "test-password";
    try (TruststorePasswordSetter setter = new TruststorePasswordSetter(password.toCharArray())) {
      Assert.assertEquals("The system property should be set to the provided password.",
                          password, System.getProperty(TruststorePasswordSetter.TRUSTSTORE_PASSWORD_SYSTEM_PROPERTY));
    }
    Assert.assertNull("The system property should be cleared after closing the setter.",
                      System.getProperty(TruststorePasswordSetter.TRUSTSTORE_PASSWORD_SYSTEM_PROPERTY));
  }

  @Test
  public void testNullPassword() {
    try (TruststorePasswordSetter setter = new TruststorePasswordSetter(null)) {
      Assert.assertNull("The system property should not be set if the password is null.",
                        System.getProperty(TruststorePasswordSetter.TRUSTSTORE_PASSWORD_SYSTEM_PROPERTY));
    }
    Assert.assertNull(System.getProperty(TruststorePasswordSetter.TRUSTSTORE_PASSWORD_SYSTEM_PROPERTY));
  }

  @Test
  public void testEmptyPassword() {
    try (TruststorePasswordSetter setter = new TruststorePasswordSetter(new char[0])) {
      Assert.assertNull("The system property should not be set if the password array is empty.",
                        System.getProperty(TruststorePasswordSetter.TRUSTSTORE_PASSWORD_SYSTEM_PROPERTY));
    }
    Assert.assertNull(System.getProperty(TruststorePasswordSetter.TRUSTSTORE_PASSWORD_SYSTEM_PROPERTY));
  }
}
