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
package org.apache.knox.gateway.services.ldap.backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.filter.FilterParser;
import org.junit.Test;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FilterSplitterTest {

    private static String orOfUids(int count) {
        return "(|" + IntStream.range(0, count).mapToObj(i -> String.format(Locale.ROOT, "(uid=user%03d)", i)).collect(Collectors.joining()) + ")";
    }

    private static List<ExprNode> split(String filter, int max) throws Exception {
        return FilterSplitter.split(FilterParser.parse(filter), max);
    }

    @Test
    public void testShortFilterNotSplit() throws Exception {
        assertEquals(1, split(orOfUids(3), 1000).size());
    }

    @Test
    public void testUnlimitedNotSplit() throws Exception {
        assertEquals(1, split(orOfUids(100), 0).size());
    }

    @Test
    public void testOrSplitCoversAllOperandsWithinLimit() throws Exception {
        List<ExprNode> pieces = split(orOfUids(100), 200);
        assertTrue(pieces.size() > 1);
        String joined = pieces.stream().map(ExprNode::toString).collect(Collectors.joining());
        for (int i = 0; i < 100; i++) {
            assertEquals(1, joined.split(String.format(Locale.ROOT, "\\(uid=user%03d\\)", i), -1).length - 1);
        }
        pieces.forEach(p -> assertTrue(p.toString().length() <= 200));
    }

    @Test
    public void testAndDistributesOtherOperandsOverEachPiece() throws Exception {
        List<ExprNode> pieces = split("(&(objectClass=person)" + orOfUids(50) + ")", 200);
        assertTrue(pieces.size() > 1);
        for (ExprNode piece : pieces) {
            assertTrue(piece.toString().startsWith("(&(objectClass=person)"));
            assertTrue(piece.toString().length() <= 200);
        }
    }

    @Test
    public void testAndWithTwoOversizedOperandsSplitsBoth() throws Exception {
        List<ExprNode> pieces = split("(&" + orOfUids(30) + orOfUids(30) + ")", 300);
        assertTrue(pieces.size() > 2);
        pieces.forEach(p -> assertTrue(p.toString().length() <= 300));
    }

    @Test
    public void testSingleOversizedOperandInOrIsKept() throws Exception {
        List<ExprNode> pieces = split("(|(uid=aVeryLongUserNameThatCannotBeSplit)(uid=b))", 20);
        assertEquals(2, pieces.size());
    }

    @Test
    public void testUnsplittableFilterReturnedAsIs() throws Exception {
        List<ExprNode> pieces = split("(!" + orOfUids(50) + ")", 100);
        assertEquals(1, pieces.size());
    }
}
