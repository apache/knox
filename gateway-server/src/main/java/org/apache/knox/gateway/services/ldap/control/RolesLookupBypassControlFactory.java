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

import org.apache.directory.api.asn1.DecoderException;
import org.apache.directory.api.asn1.util.Asn1Buffer;
import org.apache.directory.api.ldap.codec.api.AbstractControlFactory;
import org.apache.directory.api.ldap.codec.api.LdapApiService;
import org.apache.directory.api.ldap.model.message.Control;

public class RolesLookupBypassControlFactory extends AbstractControlFactory<RolesLookupBypassControl> {
    public static final byte BOOLEAN_TAG_BYTE = 0x01;

    public RolesLookupBypassControlFactory(LdapApiService codec) {
        super(codec, RolesLookupBypassControl.OID);
    }

    @Override
    public Control newControl() {
        return new RolesLookupBypassControlDecorator(codec, new RolesLookupBypassControlImpl(), this);
    }

    @Override
    public void decodeValue(Control control, byte[] controlBytes) throws DecoderException {
        if (control instanceof RolesLookupBypassControl) {
            RolesLookupBypassControl rolesLookupBypassControl = (RolesLookupBypassControl) control;
            if (controlBytes == null || controlBytes.length < 3) {
                throw new DecoderException("Invalid BER encoding for Boolean Control");
            }

            if (controlBytes[0] != BOOLEAN_TAG_BYTE) {
                throw new DecoderException("Expected Boolean Tag (0x01), found: " + controlBytes[0]);
            }

            boolean value = (controlBytes[2] != 0x00);
            rolesLookupBypassControl.setBypassRolesLookup(value);
        } else {
            throw new DecoderException("Cannot decode into "  + control.getClass().getSimpleName() + ". Control must be instance of RolesLookupBypassControl.");
        }
    }

    @Override
    public void encodeValue(Asn1Buffer buffer, Control control) {
        if (control instanceof RolesLookupBypassControl) {
            RolesLookupBypassControl rolesLookupBypassControl = (RolesLookupBypassControl) control;

            buffer.put(BOOLEAN_TAG_BYTE);
            buffer.put((byte) 1); // Value is one byte long
            buffer.put((byte) (rolesLookupBypassControl.isBypassRolesLookup() ? 0xFF : 0x00));
        }
    }
}
