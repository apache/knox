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

import org.apache.directory.api.asn1.Asn1Object;
import org.apache.directory.api.asn1.DecoderException;
import org.apache.directory.api.asn1.EncoderException;
import org.apache.directory.api.asn1.util.Asn1Buffer;
import org.apache.directory.api.ldap.codec.api.ControlDecorator;
import org.apache.directory.api.ldap.codec.api.LdapApiService;

import java.nio.ByteBuffer;

public class RolesLookupBypassControlDecorator extends ControlDecorator<RolesLookupBypassControl> implements RolesLookupBypassControl {

    private final RolesLookupBypassControlFactory rolesLookupBypassControlFactory;

    public RolesLookupBypassControlDecorator(LdapApiService codec, RolesLookupBypassControl decoratedControl, RolesLookupBypassControlFactory rolesLookupBypassControlFactory) {
        super(codec, decoratedControl);
        this.rolesLookupBypassControlFactory = rolesLookupBypassControlFactory;
    }

    @Override
    public Asn1Object decode(byte[] bytes) throws DecoderException {
        rolesLookupBypassControlFactory.decodeValue(getDecorated(), bytes);
        return this;
    }

    @Override
    public int computeLength() {
        return 3; // Tag, Length, Value
    }

    @Override
    public ByteBuffer encode(ByteBuffer byteBuffer) throws EncoderException {
        Asn1Buffer asn1Buffer = new Asn1Buffer();
        rolesLookupBypassControlFactory.encodeValue(asn1Buffer, getDecorated());

        // reverse the byte ordering because Asn1Buffers store bytes in reverse
        ByteBuffer factoryBuffer = asn1Buffer.getBytes();
        int totalBytes = factoryBuffer.remaining();
        byte[] factoryBytes = factoryBuffer.array();
        for (int i = totalBytes - 1; i >= 0; i-- ) {
            byteBuffer.put(factoryBytes[i]);
        }

        return byteBuffer;
    }

    @Override
    public boolean isBypassRolesLookup() {
        return getDecorated().isBypassRolesLookup();
    }

    @Override
    public void setBypassRolesLookup(boolean bypassRolesLookup) {
        getDecorated().setBypassRolesLookup(bypassRolesLookup);
    }
}
