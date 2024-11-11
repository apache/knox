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

public class SSEvent {

    private String data;
    private String event;
    private String id;
    private String comment;
    private Long retry;

    public SSEvent() {
    }

    public SSEvent(String data, String event, String id, String comment, Long retry) {
        this.data = data;
        this.event = event;
        this.id = id;
        this.comment = comment;
        this.retry = retry;
    }

    public void setData(String data) {
        this.data = data;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void setRetry(Long retry) {
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

    @Override
    public String toString() {
        StringBuilder eventString = new StringBuilder();

        this.appendField(eventString, this.id, "id:");
        this.appendField(eventString, this.event, "event:");
        this.appendField(eventString, this.data, "data:");
        this.appendField(eventString, this.retry, "retry:");
        this.appendField(eventString, this.comment, "");

        return eventString.toString();
    }

    private void appendField(StringBuilder eventString, Object field, String prefix) {
        if (field != null) {
            if (eventString.length() != 0) {
                eventString.append('\n');
            }
            eventString.append(prefix);
            eventString.append(field);
        }
    }
}
