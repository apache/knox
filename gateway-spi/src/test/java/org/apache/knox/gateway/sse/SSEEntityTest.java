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

import org.apache.http.HttpEntity;
import org.easymock.EasyMock;
import org.junit.Test;

import java.nio.CharBuffer;
import java.util.concurrent.BlockingQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


public class SSEEntityTest {

    private final HttpEntity entityMock = EasyMock.createNiceMock(HttpEntity.class);

    @Test
    public void testParseSingleEvent() {
        SSEEntity sseEntity = new SSEEntity(entityMock);
        BlockingQueue<SSEvent> eventQueue = sseEntity.getEventQueue();
        String unprocessedEvent = "id: 1\nevent: event\ndata: data\nretry:1\n:testing\n\n";
        CharBuffer cb = CharBuffer.wrap(unprocessedEvent);

        sseEntity.readCharBuffer(cb);

        assertFalse(eventQueue.isEmpty());

        SSEvent actualSSEvent = eventQueue.peek();
        assertEquals("1", actualSSEvent.getId());
        assertEquals("event", actualSSEvent.getEvent());
        assertEquals("data", actualSSEvent.getData());
        assertEquals(1L, actualSSEvent.getRetry().longValue());
        assertEquals(":testing", actualSSEvent.getComment());
    }

    @Test
    public void testParseMultipleEvents() {
        SSEEntity sseEntity = new SSEEntity(this.entityMock);
        BlockingQueue<SSEvent> eventQueue = sseEntity.getEventQueue();
        String unprocessedEvents = "id: 1\nevent: event\ndata: data\n\nid: 2\nevent: event2\ndata: data2\n\nid: 3\nevent: event3\ndata: data3\nretry:1045\n:TEST\n\n";
        CharBuffer cb = CharBuffer.wrap(unprocessedEvents);

        sseEntity.readCharBuffer(cb);

        assertEquals(3, eventQueue.size());

        SSEvent actualSSEvent = eventQueue.poll();
        assertEquals("1", actualSSEvent.getId());
        assertEquals("event", actualSSEvent.getEvent());
        assertEquals("data", actualSSEvent.getData());
        assertNull(actualSSEvent.getRetry());
        assertNull(actualSSEvent.getComment());

        actualSSEvent = eventQueue.poll();
        assertEquals("2", actualSSEvent.getId());
        assertEquals("event2", actualSSEvent.getEvent());
        assertEquals("data2", actualSSEvent.getData());
        assertNull(actualSSEvent.getRetry());
        assertNull(actualSSEvent.getComment());

        actualSSEvent = eventQueue.poll();
        assertEquals("3", actualSSEvent.getId());
        assertEquals("event3", actualSSEvent.getEvent());
        assertEquals("data3", actualSSEvent.getData());
        assertEquals(1045L, actualSSEvent.getRetry().longValue());
        assertEquals(":TEST", actualSSEvent.getComment());
    }

    @Test
    public void testMissingNewLine() {
        SSEEntity sseEntity = new SSEEntity(entityMock);
        BlockingQueue<SSEvent> eventQueue = sseEntity.getEventQueue();
        String unprocessedEvent = "id: 1\nevent: event\ndata: data\nretry:1045\n:TEST\n";
        CharBuffer cb = CharBuffer.wrap(unprocessedEvent);

        sseEntity.readCharBuffer(cb);

        assertTrue(eventQueue.isEmpty());
    }

    @Test
    public void testParseEventWithSpecialChars() {
        SSEEntity sseEntity = new SSEEntity(entityMock);
        BlockingQueue<SSEvent> eventQueue = sseEntity.getEventQueue();
        String unprocessedEvent = "id: 75a0a510-0065-498f:be39-c6f42a3fe4af\ndata: data:{\"records\":[{\"col_str\":\"0e01eeef73f6833a98e1df6a5a00ea46f5b52dbee27ee89ebce894aaa555c90130b08fae8aaf600ef845b774ab0082fcaf8c\",\"col_int\":-580163093,\"col_ts\":\"2024-08-14T07:41:15.125\"}],\"job_status\":\"RUNNING\",\"end_of_samples\":false}\nretry:33\n::::test\n\n";
        CharBuffer cb = CharBuffer.wrap(unprocessedEvent);

        sseEntity.readCharBuffer(cb);

        assertFalse(eventQueue.isEmpty());

        SSEvent actualSSEvent = eventQueue.peek();
        assertEquals("75a0a510-0065-498f:be39-c6f42a3fe4af", actualSSEvent.getId());
        assertNull(actualSSEvent.getEvent());
        assertEquals("data:{\"records\":[{\"col_str\":\"0e01eeef73f6833a98e1df6a5a00ea46f5b52dbee27ee89ebce894aaa555c90130b08fae8aaf600ef845b774ab0082fcaf8c\",\"col_int\":-580163093,\"col_ts\":\"2024-08-14T07:41:15.125\"}],\"job_status\":\"RUNNING\",\"end_of_samples\":false}", actualSSEvent.getData());
        assertEquals(33L, actualSSEvent.getRetry().longValue());
        assertEquals("::::test", actualSSEvent.getComment());
    }

    @Test
    public void testInvalidFormat() {
        SSEEntity sseEntity = new SSEEntity(entityMock);
        BlockingQueue<SSEvent> eventQueue = sseEntity.getEventQueue();
        String unprocessedEvent = "id: 1\nevent: event\ndata: data\nretry:1045\n:TEST\nid: 1\nevent: event\ndata: data\nretry:1045\n:TEST\n";
        CharBuffer cb = CharBuffer.wrap(unprocessedEvent);

        sseEntity.readCharBuffer(cb);

        assertTrue(eventQueue.isEmpty());
    }

    @Test
    public void testParseEventsWithDifferentNewLineChars() {
        SSEEntity sseEntity = new SSEEntity(entityMock);
        BlockingQueue<SSEvent> eventQueue = sseEntity.getEventQueue();
        String unprocessedEvents = "id: 1\nevent: event\ndata: data\r\nid: 2\nevent: event2\ndata: data2\u2028\nid: 3\nevent: event3\ndata: data3\u2029\nid: 4\nevent: event4\ndata: data4\u0085\n";
        CharBuffer cb = CharBuffer.wrap(unprocessedEvents);

        sseEntity.readCharBuffer(cb);

        assertEquals(4, eventQueue.size());

        SSEvent actualSSEvent = eventQueue.poll();
        assertEquals("1", actualSSEvent.getId());
        assertEquals("event", actualSSEvent.getEvent());
        assertEquals("data", actualSSEvent.getData());

        actualSSEvent = eventQueue.poll();
        assertEquals("2", actualSSEvent.getId());
        assertEquals("event2", actualSSEvent.getEvent());
        assertEquals("data2", actualSSEvent.getData());

        actualSSEvent = eventQueue.poll();
        assertEquals("3", actualSSEvent.getId());
        assertEquals("event3", actualSSEvent.getEvent());
        assertEquals("data3", actualSSEvent.getData());

        actualSSEvent = eventQueue.poll();
        assertEquals("4", actualSSEvent.getId());
        assertEquals("event4", actualSSEvent.getEvent());
        assertEquals("data4", actualSSEvent.getData());
    }
}
