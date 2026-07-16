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
import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.Initializer;
import org.opensaml.security.config.GlobalNamedCurveRegistryInitializer;
import org.opensaml.security.crypto.ec.NamedCurveRegistry;

public class KnoxNamedCurveRegistryInitializer implements Initializer {

    @Override
    public void init() throws InitializationException {
        if (FipsUtils.isFipsEnabledWithBCProvider()) {
            ConfigurationService.register(NamedCurveRegistry.class, new NamedCurveRegistry());
            return;
        }
        new GlobalNamedCurveRegistryInitializer().init();
    }
}
