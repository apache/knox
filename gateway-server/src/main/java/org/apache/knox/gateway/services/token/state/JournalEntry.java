/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements. See the NOTICE file distributed with this
 *  * work for additional information regarding copyright ownership. The ASF
 *  * licenses this file to you under the Apache License, Version 2.0 (the
 *  * "License"); you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations under
 *  * the License.
 *
 */
package org.apache.knox.gateway.services.token.state;

/**
 * An entry in the TokenStateJournal
 */
public interface JournalEntry {

    /**
     *
     * @return The unique token identifier for which this entry is defined.
     */
    String getTokenId();

    /**
     *
     * @return The token's issue time (milliseconds since the epoch) as a String.
     */
    String getIssueTime();

    /**
     *
     * @return The token's expiration time (milliseconds since the epoch) as a String.
     */
    String getExpiration();

    /**
     * The token's maximum allowed lifetime, beyond which its expiration cannot be extended,
     * (milliseconds since the epoch) as a String.
     *
     * @return The token's maximum allowed lifetime
     */
    String getMaxLifetime();
}
