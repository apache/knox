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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SetCookieHeaderTest {

    @Test
    public void testBasicSetCookie() {
        SetCookieHeader setCookieHeader = new SetCookieHeader("name", "value");
        assertEquals("name=value", setCookieHeader.toString());
    }

    @Test
    public void testEmptyValueSetCookie() {
        SetCookieHeader setCookieHeader = new SetCookieHeader("name", null);
        assertEquals("name=null", setCookieHeader.toString());
    }

    @Test
    public void testSetCookieWithPath() {
        SetCookieHeader setCookieHeader = new SetCookieHeader("name", "value");
        setCookieHeader.setPath("/");
        assertEquals("name=value; Path=/", setCookieHeader.toString());
    }

    @Test
    public void testSetCookieWithDomain() {
        SetCookieHeader setCookieHeader = new SetCookieHeader("name", "value");
        setCookieHeader.setDomain("example.com");
        assertEquals("name=value; Domain=example.com", setCookieHeader.toString());
    }

    @Test
    public void testSetCookieWithMaxAge() {
        SetCookieHeader setCookieHeader = new SetCookieHeader("name", "value");
        setCookieHeader.setMaxAge(231);
        assertEquals("name=value; Max-Age=231", setCookieHeader.toString());
    }

    @Test
    public void testSetCookieWithHttpOnly() {
        SetCookieHeader setCookieHeader = new SetCookieHeader("name", "value");
        setCookieHeader.setHttpOnly(true);
        assertEquals("name=value; HttpOnly", setCookieHeader.toString());
    }

    @Test
    public void testSetCookieWithSecure() {
        SetCookieHeader setCookieHeader = new SetCookieHeader("name", "value");
        setCookieHeader.setSecure(true);
        assertEquals("name=value; Secure", setCookieHeader.toString());
    }

    @Test
    public void testSetCookieWithSameSite() {
        SetCookieHeader setCookieHeader = new SetCookieHeader("name", "value");
        setCookieHeader.setSameSite("Strict");
        assertEquals("name=value; SameSite=Strict", setCookieHeader.toString());
    }

    @Test
    public void testSetCookieWithAdditionalAttribute() {
        SetCookieHeader setCookieHeader = new SetCookieHeader("name", "value");
        setCookieHeader.setAttribute("Attribute", "value");
        assertEquals("name=value; Attribute=value", setCookieHeader.toString());
    }

    @Test
    public void testSetCookieWithAll() {
        SetCookieHeader setCookieHeader = new SetCookieHeader("name", "value");
        setCookieHeader.setDomain("example.com");
        setCookieHeader.setMaxAge(231);
        setCookieHeader.setHttpOnly(true);
        setCookieHeader.setSecure(true);
        setCookieHeader.setPath("/path");
        setCookieHeader.setSameSite("Lax");
        setCookieHeader.setAttribute("Attribute", "attrValue");

        String setCookieHeaderString = setCookieHeader.toString();

        assertTrue("Name/value pair is incorrect", setCookieHeaderString.contains("name=value"));
        assertTrue("Domain is incorrect", setCookieHeaderString.contains("; Domain=example.com"));
        assertTrue("Max-Age is incorrect", setCookieHeaderString.contains("; Max-Age=231"));
        assertTrue("HttpOnly is incorrect", setCookieHeaderString.contains("; HttpOnly"));
        assertTrue("Secure is incorrect", setCookieHeaderString.contains("; Secure"));
        assertTrue("Path is incorrect", setCookieHeaderString.contains("; Path=/path"));
        assertTrue("SameSite is incorrect", setCookieHeaderString.contains("; SameSite=Lax"));
        assertTrue("Additional attribute is incorrect", setCookieHeaderString.contains("; Attribute=attrValue"));
    }
}