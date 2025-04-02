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

import java.util.Arrays;

public class SSEvent {

    private static final String SSE_DELIMITER = ":";

    private final String data;
    private final String event;
    private final String id;
    private final String comment;
    private final Long retry;

    public SSEvent(String data, String event, String id, String comment, Long retry) {
        this.data = data;
        this.event = event;
        this.id = id;
        this.comment = comment;
        this.retry = retry;
    }

    public String getData() {
        return this.data;
    }

    public String getEvent() {
        return this.event;
    }

    public String getId() {
        return this.id;
    }

    public String getComment() {
        return this.comment;
    }

    public Long getRetry() {
        return this.retry;
    }

    public static SSEvent fromString(String unprocessedEvent) {
        String id = null;
        String event = null;
        String data = null;
        String comment = null;
        Long retry = null;

        for (String line : unprocessedEvent.split("\\R")) {
            String[] lineTokens = parseLine(line);
            switch (lineTokens[0]) {
                case "id":
                    id = lineTokens[1];
                    break;
                case "event":
                    event = lineTokens[1];
                    break;
                case "data":
                    data = lineTokens[1];
                    break;
                case "comment":
                    comment = lineTokens[1];
                    break;
                case "retry":
                    retry = Long.parseLong(lineTokens[1]);
                    break;
                default:
                    break;
            }
        }
        return new SSEvent(data, event, id, comment, retry);
    }

    private static String[] parseLine(String line) {
        String[] lineTokens = Arrays.stream(line.split(SSE_DELIMITER, 2)).map(String::trim).toArray(String[]::new);

        if (lineTokens[0].isEmpty()) {
            lineTokens[0] = "comment";
        }

        return lineTokens;
    }

    @Override
    public String toString() {
        StringBuilder eventString = new StringBuilder();

        this.appendField(eventString, this.id, "id");
        this.appendField(eventString, this.event, "event");
        this.appendField(eventString, this.data, "data");
        this.appendField(eventString, this.retry, "retry");
        this.appendField(eventString, this.comment, "");

        return eventString.toString();
    }

    private void appendField(StringBuilder eventString, Object field, String prefix) {
        if (field != null) {
            if (eventString.length() != 0) {
                eventString.append('\n');
            }
            eventString.append(prefix);
            eventString.append(SSE_DELIMITER);
            eventString.append(field);
        }
    }
}
