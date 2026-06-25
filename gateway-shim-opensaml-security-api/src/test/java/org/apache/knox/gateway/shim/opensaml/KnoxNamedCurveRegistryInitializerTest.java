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
package org.apache.knox.gateway.shim.opensaml;

import org.apache.knox.gateway.fips.FipsUtils;
import org.junit.After;
import org.junit.Test;
import org.opensaml.core.config.ConfigurationService;
import org.opensaml.security.crypto.ec.ECSupport;
import org.opensaml.security.crypto.ec.NamedCurve;
import org.opensaml.security.crypto.ec.NamedCurveRegistry;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class KnoxNamedCurveRegistryInitializerTest {

    @After
    public void clearFipsProperty() {
        System.clearProperty(FipsUtils.FIPS_SYSTEM_PROPERTY);
    }

    @Test
    public void fipsModeRegistersEmptyRegistry() throws Exception {
        System.setProperty(FipsUtils.FIPS_SYSTEM_PROPERTY, "true");

        new KnoxNamedCurveRegistryInitializer().init();

        NamedCurveRegistry registry = ConfigurationService.get(NamedCurveRegistry.class);
        assertNotNull("Wrapper must always register a NamedCurveRegistry", registry);

        NamedCurve curve = ECSupport.getNamedCurve((ECPublicKey) generateP256KeyPair().getPublic());
        assertNull(
                "In FIPS mode the wrapper must short-circuit GlobalNamedCurveRegistryInitializer "
                        + "so the registry is empty and no vanilla-BC EC code executes.",
                curve);
    }

    @Test
    public void nonFipsModeDelegatesToUpstreamAndPopulatesRegistry() throws Exception {
        System.clearProperty(FipsUtils.FIPS_SYSTEM_PROPERTY);

        new KnoxNamedCurveRegistryInitializer().init();

        NamedCurveRegistry registry = ConfigurationService.get(NamedCurveRegistry.class);
        assertNotNull("Wrapper must always register a NamedCurveRegistry", registry);

        NamedCurve curve = ECSupport.getNamedCurve((ECPublicKey) generateP256KeyPair().getPublic());
        assertNotNull(
                "In non-FIPS mode the wrapper must delegate to the upstream "
                        + "GlobalNamedCurveRegistryInitializer, populating the registry from the "
                        + "NamedCurve SPI so ECDH-ES SAML support remains functional.",
                curve);
        assertEquals("secp256r1", curve.getName());
    }

    private static KeyPair generateP256KeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        return kpg.generateKeyPair();
    }
}
