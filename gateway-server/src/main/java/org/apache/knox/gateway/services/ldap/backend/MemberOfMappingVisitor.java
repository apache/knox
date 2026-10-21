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

import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.filter.BranchNode;
import org.apache.directory.api.ldap.model.filter.EqualityNode;
import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.filter.FilterVisitor;
import org.apache.directory.api.ldap.model.filter.LeafNode;
import org.apache.directory.api.ldap.model.filter.OrNode;
import org.apache.directory.api.ldap.model.filter.SimpleNode;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.name.Rdn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * FilterVisitor that maps LDAP search filters that use the memberOf attribute
 * to individual DNs if the backend doesn't support memberOf.
 */
public class MemberOfMappingVisitor implements FilterVisitor {

    private final SchemaManager schemaManager;
    private final DnLookup dnLookup;
    private final Predicate<String> isUser;

    public MemberOfMappingVisitor(SchemaManager schemaManager, DnLookup dnLookup, Predicate<String> isUser) {
        this.schemaManager = schemaManager;
        this.dnLookup = dnLookup;
        this.isUser = isUser;
    }

    @Override
    public Object visit(ExprNode exprNode) {
        if (exprNode == null) {
            return null;
        }

        if (exprNode.isLeaf()) {
            try {
                return handleLeafNode((LeafNode) exprNode);
            } catch (Exception e) {
                throw new RuntimeException("Failed to map memberOf filter value to individual members.", e);
            }
        } else {
            return handleBranchNode((BranchNode) exprNode);
        }
    }

    private Object handleLeafNode(LeafNode leafNode) throws Exception {
        String currentAttribute = leafNode.getAttribute();

        if ("memberOf".equalsIgnoreCase(currentAttribute)) {
            if (leafNode instanceof SimpleNode) {
                Value currentValue = ((SimpleNode) leafNode).getValue();
                Set<String> members = getMembers(currentValue.getString());
                if (members != null && !members.isEmpty()) {
                    OrNode orNode = new OrNode();
                    for (String dnString : members) {
                        if (isUser.test(dnString)) {
                            Dn dn = new Dn(dnString);
                            Rdn rdn = dn.getRdn();
                            orNode.addNode(new EqualityNode<>(rdn.getType(), rdn.getValue()));
                        } else {
                            orNode.addNode(new EqualityNode<>("memberOf", dnString));
                        }
                    }
                    return orNode;
                }
            }
        }

        return leafNode;
    }

    private Set<String> getMembers(String groupDn) throws Exception {
        Entry groupEntry = dnLookup.lookupDn(groupDn, schemaManager);
        if (groupEntry != null) {
            return collectMembers(groupEntry);
        }
        return null;
    }

    private Set<String> collectMembers(Entry entry) {
        Set<String> members = new HashSet<>();
        Attribute membersAttribute = entry.get("member");
        if (membersAttribute != null) {
            for (Value value : membersAttribute) {
                members.add(value.getString());
            }
        }
        Attribute uniqueMembersAttribute = entry.get("uniqueMember");
        if (uniqueMembersAttribute != null) {
            for (Value value : uniqueMembersAttribute) {
                members.add(value.getString());
            }
        }
        return members;
    }

    private Object handleBranchNode(BranchNode branchNode) {
        // recursively call visit on all the children
        List<ExprNode> newChildren = new ArrayList<>();
        for (ExprNode child : branchNode.getChildren()) {
            Object newChild = child.accept(this);
            if (newChild instanceof ExprNode) {
                newChildren.add((ExprNode) newChild);
            }
        }
        branchNode.setChildren(newChildren);
        return branchNode;
    }

    @Override
    public boolean canVisit(ExprNode exprNode) {
        return true;
    }

    @Override
    public boolean isPrefix() {
        return false;
    }

    @Override
    public List<ExprNode> getOrder(BranchNode branchNode, List<ExprNode> list) {
        return list;
    }
}
