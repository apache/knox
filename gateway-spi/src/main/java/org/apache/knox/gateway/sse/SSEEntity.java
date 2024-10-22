/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.sse;

import org.apache.http.HttpEntity;
import org.apache.http.entity.AbstractHttpEntity;

import javax.servlet.AsyncContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.CharBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SSEEntity extends AbstractHttpEntity {

    private static final String SSE_DELIMITER = ":";

    private final BlockingQueue<SSEvent> eventQueue;
    private final StringBuilder eventBuilder = new StringBuilder();
    private final HttpEntity httpEntity;
    private char previousChar = '0';

    public SSEEntity(HttpEntity httpEntity) {
        this.httpEntity = httpEntity;
        this.eventQueue = new LinkedBlockingQueue<>();
    }

    public boolean readCharBuffer(CharBuffer charBuffer) {
        while (charBuffer.hasRemaining()) {
            processChar(charBuffer.get());
        }
        return !eventQueue.isEmpty();
    }

    //Two new line chars (\n\n) after each other means the event is finished streaming
    //We can process it and add it to the event queue
    private void processChar(char nextChar) {
        if (isNewLineChar(nextChar) && isNewLineChar(previousChar)) {
            processEvent();
            eventBuilder.setLength(0);
            previousChar = '0';
        } else {
            eventBuilder.append(nextChar);
            previousChar = nextChar;
        }
    }

    private boolean isNewLineChar(char c) {
        return c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029';
    }

    private void processEvent() {
        String unprocessedEvent = eventBuilder.toString();
        SSEvent ssEvent = new SSEvent();

        for (String line : unprocessedEvent.split("\\R")) {
            String[] lineTokens =  this.parseLine(line);
            switch (lineTokens[0]) {
                case "id":
                    ssEvent.setId(lineTokens[1].trim());
                    break;
                case "event":
                    ssEvent.setEvent(lineTokens[1].trim());
                    break;
                case "data":
                    ssEvent.setData(lineTokens[1].trim());
                    break;
                case "comment":
                    ssEvent.setComment(lineTokens[1].trim());
                    break;
                case "retry":
                    ssEvent.setRetry(Long.parseLong(lineTokens[1].trim()));
                    break;
                default:
                    break;
            }
        }
        eventQueue.add(ssEvent);
    }

    private String[] parseLine(String line) {
        String[] lineTokens = new String[2];
        if(line.startsWith(SSE_DELIMITER)) {
            lineTokens[0] = "comment";
            lineTokens[1] = line;
        } else {
            lineTokens = line.split(SSE_DELIMITER, 2);
        }

        return lineTokens;
    }

    public void sendEvent(AsyncContext asyncContext) throws IOException, InterruptedException {
        while (!eventQueue.isEmpty()) {
            SSEvent event = eventQueue.take();
            asyncContext.getResponse().getWriter().write(event.toString());
            asyncContext.getResponse().getWriter().println('\n');

            //Calling response.flushBuffer() instead of writer.flush().
            //This way an exception is thrown if the connection is already closed on the client side.
            asyncContext.getResponse().flushBuffer();
        }
    }

    public BlockingQueue<SSEvent> getEventQueue() {
        return eventQueue;
    }

    @Override
    public boolean isRepeatable() {
        return httpEntity.isRepeatable();
    }

    @Override
    public long getContentLength() {
        return httpEntity.getContentLength();
    }

    @Override
    public InputStream getContent() throws IOException, UnsupportedOperationException {
        return httpEntity.getContent();
    }

    @Override
    public void writeTo(OutputStream outStream) throws IOException {
        httpEntity.writeTo(outStream);
    }

    @Override
    public boolean isStreaming() {
        return httpEntity.isStreaming();
    }
}
