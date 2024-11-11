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
package org.apache.knox.gateway.sse;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SSEventTest {


    @Test
    public void testToStringWithAll() {
        SSEvent ssEvent = new SSEvent("data", "event", "id", ":comment", 5L);
        String expected = "id:id\nevent:event\ndata:data\nretry:5\n:comment";

        assertEquals(expected, ssEvent.toString());
    }

    @Test
    public void testToStringNoId() {
        SSEvent ssEventNull = new SSEvent("data", "event", null, ":test", 2L);
        SSEvent ssEventEmpty = new SSEvent("data", "event", "", ":new comment", 1L);
        String expectedForNull = "event:event\ndata:data\nretry:2\n:test";
        String expectedForEmpty = "id:\nevent:event\ndata:data\nretry:1\n:new comment";

        assertEquals(expectedForNull, ssEventNull.toString());
        assertEquals(expectedForEmpty, ssEventEmpty.toString());
    }

    @Test
    public void testToStringNoEvent() {
        SSEvent ssEventNull = new SSEvent("data", null, "id", ":comment", 11L);
        SSEvent ssEventEmpty = new SSEvent("data", "", "id", ":test comment", 30L);
        String expectedForNull = "id:id\ndata:data\nretry:11\n:comment";
        String expectedForEmpty = "id:id\nevent:\ndata:data\nretry:30\n:test comment";

        assertEquals(expectedForNull, ssEventNull.toString());
        assertEquals(expectedForEmpty, ssEventEmpty.toString());
    }

    @Test
    public void testToStringNoData() {
        SSEvent ssEventNull = new SSEvent(null, "event", "id", ":comment", 2L);
        SSEvent ssEventEmpty = new SSEvent("", "event", "id", ":testing", 1L);
        String expectedForNull = "id:id\nevent:event\nretry:2\n:comment";
        String expectedForEmpty = "id:id\nevent:event\ndata:\nretry:1\n:testing";

        assertEquals(expectedForNull, ssEventNull.toString());
        assertEquals(expectedForEmpty, ssEventEmpty.toString());
    }

    @Test
    public void testToStringNoComment() {
        SSEvent ssEventNull = new SSEvent("data", "event", "id", null, 3L);
        String expected = "id:id\nevent:event\ndata:data\nretry:3";

        assertEquals(expected, ssEventNull.toString());
    }

    @Test
    public void testToStringNoRetry() {
        SSEvent ssEventNull = new SSEvent("data", "event", "id", ":TEST", null);
        String expected = "id:id\nevent:event\ndata:data\n:TEST";

        assertEquals(expected, ssEventNull.toString());
    }
}