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

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GroupUtils {

    public static List<String> getGroupStrings(Collection<String> groupNames, final int lengthLimit, final int sizeLimitBytes) {
        if (groupNames.isEmpty()) {
            return Collections.emptyList();
        }

        // Defensive copy to isolate from concurrent modifications
        final List<String> safeGroupNames = new ArrayList<>(groupNames);

        if (sizeLimitBytes > 0) {
            return getGroupStringsBySize(safeGroupNames, sizeLimitBytes);
        } else {
            return getGroupStringsByLength(safeGroupNames, lengthLimit);
        }
    }

    private static List<String> getGroupStringsBySize(Collection<String> groupNames, int sizeLimitBytes) {
        final List<String> groupStrings = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int currentSize = 0; // UTF-8 byte count

        for (String groupName : groupNames) {
            int commaBytes = sb.length() > 0 ? 1 : 0; // comma between groups
            int groupBytes = groupName.getBytes(StandardCharsets.UTF_8).length;
            int projectedSize = currentSize + commaBytes + groupBytes;

            if (projectedSize > sizeLimitBytes) {
                saveGroups(groupStrings, sb);
                sb = new StringBuilder();
                currentSize = 0;
            }

            currentSize = addCommaIfNeeded(sb, currentSize);
            sb.append(groupName);
            currentSize += groupBytes;
        }

        if (sb.length() > 0) {
            saveGroups(groupStrings, sb);
        }

        return groupStrings;
    }

    private static void saveGroups(final List<String> groupStrings, final StringBuilder sb) {
        final String groups = sb.toString();
        if (StringUtils.isNotBlank(groups)) {
            groupStrings.add(groups);
        }
    }

    private static int addCommaIfNeeded(final StringBuilder sb, int currentSize) {
        if (sb.length() > 0) {
            sb.append(',');
            return currentSize + 1; // comma byte
        } else {
            return currentSize;
        }
    }

    private static List<String> getGroupStringsByLength(Collection<String> groupNames, int lengthLimit) {
        final List<String> groupStrings = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        for (String groupName : groupNames) {
            int commaChars = sb.length() > 0 ? 1 : 0;
            if (sb.length() + groupName.length() + commaChars > lengthLimit) {
                saveGroups(groupStrings, sb);
                sb = new StringBuilder();
            }
            addCommaIfNeeded(sb, 0);
            sb.append(groupName);
        }

        if (sb.length() > 0) {
            saveGroups(groupStrings, sb);
        }

        return groupStrings;
    }
}
