/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.util;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class Tokens {

    /**
     * Get a String derived from a JWT String, which is suitable for presentation (e.g., logging) without compromising
     * security.
     *
     * @param token A BASE64-encoded JWT String.
     *
     * @return An abbreviated form of the specified JWT String.
     */
    public static String getTokenDisplayText(final String token) {
        String displayText = null;
        if (token !=null) {
            if (token.length() >= 7) { // Avoid empty or otherwise invalid values that would break this
                displayText =
                        String.format(Locale.ROOT, "%s...%s", token.substring(0, 6), token.substring(token.length() - 6));
            }
        }
        return displayText;
    }

    /**
     * Get a String derived from a Knox token UUID String, which is suitable for presentation (e.g., logging) without
     * compromising security.
     *
     * @param uuid A Knox token UUID String.
     *
     * @return An abbreviated form of the specified UUID String.
     */
    public static String getTokenIDDisplayText(final String uuid) {
        String displayText = null;
        if (uuid != null && uuid.length() == 36 && uuid.contains("-")) {
            displayText = String.format(Locale.ROOT,
                                        "%s...%s",
                                        uuid.substring(0, uuid.indexOf('-')),
                                        uuid.substring(uuid.lastIndexOf('-') + 1));
        }
        return displayText;
    }

    public static Set<String> getDisplayableTokenIDsText(final Set<String> tokenIds) {
        Set<String> displayableTokenIds = new HashSet<>();
        for (String tokenId : tokenIds) {
            displayableTokenIds.add(Tokens.getTokenIDDisplayText(tokenId));
        }
        return displayableTokenIds;
    }

}
