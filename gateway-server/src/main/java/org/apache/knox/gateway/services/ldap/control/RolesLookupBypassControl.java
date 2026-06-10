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
package org.apache.knox.gateway.services.ldap.control;

import org.apache.directory.api.ldap.model.message.Control;

public interface RolesLookupBypassControl extends Control {
    // OID created from a UUID to ensure no collisions:
    // Apache Root OID for core object classes 1.3.6.1.4.1.18060.2
    // UUID "5236bee0-8a22-4419-9f8e-f1de43312ce1"
    String OID = "1.3.6.1.4.1.18060.2.1379319520.35362.17433.40846.265936912329953";

    boolean isBypassRolesLookup();
    void setBypassRolesLookup(boolean bypassRolesLookup);
}
