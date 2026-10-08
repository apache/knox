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

import org.apache.directory.api.ldap.model.filter.AndNode;
import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.filter.OrNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits an LDAP filter that is too long for a remote server into several smaller filters whose
 * result sets, when unioned, are equivalent to the result set of the original filter.
 * <p>
 * An OR is split by distributing its operands over several ORs. An AND is split by splitting its
 * longest operand and distributing the remaining operands over each piece:
 * {@code (&X (|A B C D))} becomes {@code (&X (|A B))} and {@code (&X (|C D))}.
 * Anything else (leaf nodes, NOT) cannot be split and is returned as is, even if it is too long.
 */
public final class FilterSplitter {

    // Length of the "(|" prefix and ")" suffix wrapped around the operands of an OR
    private static final int BRANCH_OVERHEAD = 3;

    private FilterSplitter() {
    }

    /**
     * @param node the filter to split
     * @param maxLength the maximum length of each resulting filter; a value of zero or less disables splitting
     * @return filters each no longer than maxLength where possible; never empty
     */
    public static List<ExprNode> split(ExprNode node, int maxLength) {
        if (maxLength <= 0 || length(node) <= maxLength) {
            return List.of(node);
        }
        if (node instanceof OrNode) {
            return splitOr((OrNode) node, maxLength);
        }
        if (node instanceof AndNode) {
            return splitAnd((AndNode) node, maxLength);
        }
        return List.of(node);
    }

    private static List<ExprNode> splitOr(OrNode node, int maxLength) {
        // Flatten oversized operands into their own pieces, then pack all operands into as few ORs as fit.
        List<ExprNode> operands = new ArrayList<>();
        for (ExprNode child : node.getChildren()) {
            operands.addAll(split(child, maxLength));
        }

        List<ExprNode> result = new ArrayList<>();
        List<ExprNode> pack = new ArrayList<>();
        int packLength = BRANCH_OVERHEAD;
        for (ExprNode operand : operands) {
            int operandLength = length(operand);
            if (!pack.isEmpty() && packLength + operandLength > maxLength) {
                result.add(toOr(pack));
                pack = new ArrayList<>();
                packLength = BRANCH_OVERHEAD;
            }
            pack.add(operand);
            packLength += operandLength;
        }
        if (!pack.isEmpty()) {
            result.add(toOr(pack));
        }
        return result;
    }

    private static List<ExprNode> splitAnd(AndNode node, int maxLength) {
        List<ExprNode> children = node.getChildren();
        int largestIdx = 0;
        int largestLength = -1;
        for (int i = 0; i < children.size(); i++) {
            int childLength = length(children.get(i));
            if (childLength > largestLength) {
                largestLength = childLength;
                largestIdx = i;
            }
        }

        // Leave room for the other operands so each piece still fits once recombined with them.
        // When they alone exceed the limit, fall back to the full limit and let the recursion below
        // split the other operands.
        int pieceMaxLength = maxLength - (length(node) - largestLength);
        if (pieceMaxLength <= 0) {
            pieceMaxLength = maxLength;
        }
        List<ExprNode> pieces = split(children.get(largestIdx), pieceMaxLength);
        if (pieces.size() < 2) {
            // The largest operand can't be split so neither can the AND
            return List.of(node);
        }

        List<ExprNode> result = new ArrayList<>();
        for (ExprNode piece : pieces) {
            List<ExprNode> newChildren = new ArrayList<>(children);
            newChildren.set(largestIdx, piece);
            // Other operands may also be too long, so the new AND is split again
            result.addAll(split(new AndNode(newChildren), maxLength));
        }
        return result;
    }

    private static ExprNode toOr(List<ExprNode> operands) {
        return operands.size() == 1 ? operands.get(0) : new OrNode(operands);
    }

    private static int length(ExprNode node) {
        return node.toString().length();
    }
}
