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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class GroupUtilsTest {

    @Test
    public void testSplitByLengthLimitOnly() {
        final List<String> input = Arrays.asList("alpha", "beta", "gamma", "delta", "epsilon");
        final List<String> result = GroupUtils.getGroupStrings(input, 10, -1); // size limit disabled

        // Each chunk should respect the length limit (≤ 10 chars, including commas)
        assertTrue(result.stream().allMatch(s -> s.length() <= 10));
        assertEquals(Arrays.asList("alpha,beta", "gamma", "delta", "epsilon"), result);
    }

    @Test
    public void testSplitBySizeLimitOnly() {
        // We'll create UTF-8 multibyte strings to show that length != size
        final String wideChar = "á"; // 1 char = 2 bytes in UTF-8
        String group = String.join("", Collections.nCopies(2000, wideChar));
        final List<String> input = Arrays.asList(group, group, group);

        // 4KB = 4096 bytes; since each string 4000 bytes, only one fits per chunk
        final List<String> result = GroupUtils.getGroupStrings(input, 10, 4096);

        // Should have 3 chunks because size limit applies, not length
        assertEquals(3, result.size());

        // Each chunk can exceed the length limit (since length = 2000 > 10)
        assertTrue(result.stream().allMatch(s -> s.length() > 10));

        // Each chunk must be ≤ 4KB in bytes
        for (String s : result) {
            int bytes = s.getBytes(StandardCharsets.UTF_8).length;
            assertTrue("Chunk exceeds 4KB: " + bytes, bytes <= 4096);
        }
    }

    @Test
    public void testSplitBySizeLimitWithManySmallElements() throws Exception {
        // 1. Create many small ASCII elements (5 bytes each + commas)
        final List<String> input = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            input.add("item" + i); // e.g., "item1", "item2", ...
        }

        final int sizeLimitBytes = 512;
        final List<String> result = GroupUtils.getGroupStrings(input, 1000, sizeLimitBytes);

        assertEquals("Should be chunked into multiple groups", 2, result.size());
        assertEquals(508, result.get(0).getBytes(StandardCharsets.UTF_8).length);
        assertEquals(182, result.get(1).getBytes(StandardCharsets.UTF_8).length);

        // Verify all data preserved and ordered
        final String joined = String.join(",", result);
        final List<String> reconstructed = Arrays.asList(joined.split(","));
        assertEquals("Item count should remain the same", input.size(), reconstructed.size());
        assertEquals("All items should appear in the same order", input, reconstructed);
    }

    @Test
    public void testSizeLimitTakesPrecedenceOverLengthLimit() {
        // Use strings that easily exceed 10 chars but stay within 4KB
        final List<String> input = Arrays.asList("abcdefghijklmno", "pqrstuvwxyz", "1234567890");

        // lengthLimit=10 would normally split them all apart, but sizeLimit=4096 means we should keep them together
        final List<String> result = GroupUtils.getGroupStrings(input, 10, 4096);

        // Expect a single chunk (because size limit is large enough)
        assertEquals("Should not split by length when sizeLimit > 0", 1, result.size());
    }

    @Test
    public void testEmptyInputReturnsEmptyList() {
        final List<String> result = GroupUtils.getGroupStrings(Collections.emptyList(), 1000, 4096);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testShouldNotReturnEmptyElements() {
        final List<String> input = Arrays.asList("longGroupName1", "longGroupName2", "longGroupName3");
        final List<String> result = GroupUtils.getGroupStrings(input, 10, 1); // note the size limit is set to 1
        assertEquals("Should not return a list with empty elements", 3, result.size());
    }

    @Test
    public void testConcurrentGroupStringCalls() throws Exception {
        final int THREAD_COUNT = 20;
        final int ITERATIONS = 1000;
        final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        final List<Future<List<String>>> futures = new ArrayList<>();

        final List<String> input = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            input.add("group_" + i);
        }

        for (int t = 0; t < THREAD_COUNT; t++) {
            futures.add(executor.submit(() -> {
                for (int i = 0; i < ITERATIONS; i++) {
                    List<String> result = GroupUtils.getGroupStrings(input, 50, 512);
                    assertNotNull(result);
                    assertFalse(result.isEmpty());

                    Set<String> allGroups = new HashSet<>();
                    for (String chunk : result) {
                        int bytes = chunk.getBytes(StandardCharsets.UTF_8).length;
                        assertTrue("Chunk exceeds 512 bytes: " + bytes, bytes <= 512);

                        for (String g : chunk.split(",")) {
                            assertTrue("Duplicate group found: " + g, allGroups.add(g));
                        }
                    }

                    assertEquals("Missing or extra groups in result", input.size(), allGroups.size());
                    assertTrue("Not all expected groups are present", allGroups.containsAll(input));
                }
                return null;
            }));
        }

        for (Future<List<String>> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
    }

}
