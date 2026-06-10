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

import static org.easymock.EasyMock.mock;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.directory.api.asn1.util.Asn1Buffer;
import org.apache.directory.api.ldap.codec.api.LdapApiService;
import org.apache.directory.api.ldap.model.message.Control;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

public class RolesLookupBypassControlFactoryTest {

    private LdapApiService mockLdapApiService;
    private RolesLookupBypassControlFactory rolesLookupBypassControlFactory;

    @Before
    public void setUp() throws Exception {
        mockLdapApiService = mock(LdapApiService.class);
        replay(mockLdapApiService);
        rolesLookupBypassControlFactory = new RolesLookupBypassControlFactory(mockLdapApiService);
    }

    @Test
    public void newControl() {
        Control control = rolesLookupBypassControlFactory.newControl();
        assertTrue("Control must be a RolesLookupBypassControlDecorator", control instanceof RolesLookupBypassControlDecorator);
    }

    @Test
    public void decodeFalseValue() throws Exception {
        RolesLookupBypassControl control = new RolesLookupBypassControlImpl();
        byte[] bytes = new byte[]{0x01, 0x03, 0x00};

        rolesLookupBypassControlFactory.decodeValue(control, bytes);

        assertFalse(control.isBypassRolesLookup());
    }

    @Test
    public void decodeTrueValue() throws Exception {
        RolesLookupBypassControl control = new RolesLookupBypassControlImpl();
        byte[] bytes = new byte[]{0x01, 0x03, (byte) 0xff};

        rolesLookupBypassControlFactory.decodeValue(control, bytes);

        assertTrue(control.isBypassRolesLookup());
    }

    @Test
    public void encodeTrueValue() {
        Asn1Buffer asn1Buffer = new Asn1Buffer();
        RolesLookupBypassControl control = new RolesLookupBypassControlImpl();
        control.setBypassRolesLookup(true);

        rolesLookupBypassControlFactory.encodeValue(asn1Buffer, control);

        // expectedBytes in reverse because Asn1Buffer stores bytes in reverse order
        byte[] expectedBytes = new byte[]{(byte) 0xff, 0x03, 0x01};
        System.out.println(asn1Buffer.toString());
        ByteBuffer encodedBuffer = asn1Buffer.getBytes();
        byte[] encodedBytes = new byte[encodedBuffer.remaining()];
        encodedBuffer.get(encodedBytes);
        assertArrayEquals(expectedBytes, encodedBytes);
    }

    @Test
    public void encodeFalseValue() {
        Asn1Buffer asn1Buffer = new Asn1Buffer();
        RolesLookupBypassControl control = new RolesLookupBypassControlImpl();
        control.setBypassRolesLookup(false);

        rolesLookupBypassControlFactory.encodeValue(asn1Buffer, control);

        // expectedBytes in reverse because Asn1Buffer stores bytes in reverse order
        byte[] expectedBytes = new byte[]{0x00, 0x03, 0x01};
        ByteBuffer encodedBuffer = asn1Buffer.getBytes();
        byte[] encodedBytes = new byte[encodedBuffer.remaining()];
        encodedBuffer.get(encodedBytes);
        assertArrayEquals(expectedBytes, encodedBytes);
    }
}