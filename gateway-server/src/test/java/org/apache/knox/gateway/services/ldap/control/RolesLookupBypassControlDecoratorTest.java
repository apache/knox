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

import static org.apache.knox.gateway.services.ldap.control.RolesLookupTestConstants.ROLES_LOOKUP_BYPASS_CONTROL_OID;
import static org.easymock.EasyMock.mock;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.directory.api.asn1.DecoderException;
import org.apache.directory.api.ldap.codec.api.LdapApiService;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;

public class RolesLookupBypassControlDecoratorTest {

    private RolesLookupBypassControl rolesLookupBypassControl;
    private LdapApiService mockLdapApiService;
    private RolesLookupBypassControlFactory rolesLookupBypassControlFactory;

    private RolesLookupBypassControlDecorator rolesLookupBypassControlDecorator;

    @Before
    public void setUp() throws Exception {
        mockLdapApiService = mock(LdapApiService.class);
        replay(mockLdapApiService);

        rolesLookupBypassControl = new RolesLookupBypassControlImpl(ROLES_LOOKUP_BYPASS_CONTROL_OID);
        rolesLookupBypassControlFactory = new RolesLookupBypassControlFactory(mockLdapApiService, ROLES_LOOKUP_BYPASS_CONTROL_OID);

        rolesLookupBypassControlDecorator = new RolesLookupBypassControlDecorator(mockLdapApiService, rolesLookupBypassControl, rolesLookupBypassControlFactory);
    }

    @Test
    public void decodeFalseValue() throws Exception {
        byte[] bytes = new byte[]{0x01, 0x01, 0x00};
        rolesLookupBypassControlDecorator.decode(bytes);

        assertFalse(rolesLookupBypassControl.isBypassRolesLookup());
    }

    @Test
    public void decodeTrueValue() throws Exception {
        byte[] bytes = new byte[]{0x01, 0x01, (byte) 0xff};
        rolesLookupBypassControlDecorator.decode(bytes);

        assertTrue(rolesLookupBypassControl.isBypassRolesLookup());
    }

    @Test(expected = DecoderException.class)
    public void decodeWrongTag() throws Exception {
        byte[] bytes = new byte[]{0x02, 0x01, 0x00};
        rolesLookupBypassControlDecorator.decode(bytes);
    }

    @Test(expected = DecoderException.class)
    public void decodeWrongLength() throws Exception {
        byte[] bytes = new byte[]{0x02, 0x02, 0x00, 0x00};
        rolesLookupBypassControlDecorator.decode(bytes);
    }


    @Test
    public void computeLength() {
        assertEquals("Length must always be 3", 3, rolesLookupBypassControlDecorator.computeLength());
    }

    @Test
    public void encodeTrueValue() throws Exception {
        rolesLookupBypassControl.setBypassRolesLookup(true);
        byte[] expectedBytes = new byte[]{0x01, 0x01, (byte) 0xff};

        ByteBuffer byteBuffer = ByteBuffer.allocate(3);
        ByteBuffer encodedBuffer = rolesLookupBypassControlDecorator.encode(byteBuffer);
        // transition from write mode to read mode
        encodedBuffer.flip();
        byte[] encodedBytes = new byte[encodedBuffer.remaining()];
        encodedBuffer.get(encodedBytes);
        assertArrayEquals(expectedBytes, encodedBytes);
    }

    @Test
    public void encodeFalseValue() throws Exception {
        byte[] expectedBytes = new byte[]{0x01, 0x01, 0x00};

        ByteBuffer byteBuffer = ByteBuffer.allocate(3);
        ByteBuffer encodedBuffer = rolesLookupBypassControlDecorator.encode(byteBuffer);
        // transition from write mode to read mode
        encodedBuffer.flip();
        byte[] encodedBytes = new byte[encodedBuffer.remaining()];
        encodedBuffer.get(encodedBytes);
        assertArrayEquals(expectedBytes, encodedBytes);
    }

    @Test
    public void isBypassRolesLookup() {
        rolesLookupBypassControl.setBypassRolesLookup(true);
        assertEquals("isBypassRolesLookup should match the value from the decorated Impl", rolesLookupBypassControl.isBypassRolesLookup(), rolesLookupBypassControlDecorator.isBypassRolesLookup());
        rolesLookupBypassControl.setBypassRolesLookup(false);
        assertEquals("isBypassRolesLookup should match the value from the decorated Impl", rolesLookupBypassControl.isBypassRolesLookup(), rolesLookupBypassControlDecorator.isBypassRolesLookup());
    }

    @Test
    public void setBypassRolesLookup() {
        rolesLookupBypassControlDecorator.setBypassRolesLookup(true);
        assertEquals("isBypassRolesLookup should match the value from the decorated Impl", rolesLookupBypassControl.isBypassRolesLookup(), rolesLookupBypassControlDecorator.isBypassRolesLookup());
        rolesLookupBypassControlDecorator.setBypassRolesLookup(false);
        assertEquals("isBypassRolesLookup should match the value from the decorated Impl", rolesLookupBypassControl.isBypassRolesLookup(), rolesLookupBypassControlDecorator.isBypassRolesLookup());
    }
}