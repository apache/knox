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

import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.filter.BranchNode;
import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.filter.FilterVisitor;
import org.apache.directory.api.ldap.model.filter.LeafNode;
import org.apache.directory.api.ldap.model.filter.SimpleNode;
import org.apache.directory.api.ldap.model.schema.SchemaManager;

import java.util.ArrayList;
import java.util.List;

/**
 * FilterVisitor that maps LDAP search filters from the proxy attributes to
 * remote attributes.
 */
public class FilterMappingVisitor implements FilterVisitor {

    private final String userIdentifierAttribute;
    private final String userObjectClass;
    private final String groupObjectClass;
    private final SchemaManager schemaManager;

    public FilterMappingVisitor(String userIdentifierAttribute, String userObjectClass, String groupObjectClass, SchemaManager schemaManager) {
        this.userIdentifierAttribute = userIdentifierAttribute;
        this.userObjectClass = userObjectClass;
        this.groupObjectClass = groupObjectClass;
        this.schemaManager = schemaManager;
    }

    @Override
    public Object visit(ExprNode exprNode) {
        if (exprNode == null) {
            return null;
        }

        if (exprNode.isLeaf()) {
            return handleLeafNode((LeafNode) exprNode);
        } else {
            return handleBranchNode((BranchNode) exprNode);
        }
    }

    private Object handleLeafNode(LeafNode leafNode) {
        String currentAttribute = leafNode.getAttribute();

        // Map the uid attribute search to the configured user identifier (e.g., sAMAccountName)
        if ("uid".equalsIgnoreCase(currentAttribute) && !"uid".equalsIgnoreCase(userIdentifierAttribute)) {
            leafNode.setAttribute(userIdentifierAttribute);
            leafNode.setAttributeType(schemaManager.getAttributeType(userIdentifierAttribute));
        }

        // Map group or user object class values to the configured values
        if ("objectClass".equalsIgnoreCase(currentAttribute)) {
            if (leafNode instanceof SimpleNode) {
                SimpleNode valueNode = (SimpleNode) leafNode;
                Value currentValue = valueNode.getValue();
                if (currentValue != null) {
                    if ("groupofnames".equalsIgnoreCase(currentValue.getString())) {
                        valueNode.setValue(new Value(groupObjectClass));
                    }
                    if ("inetOrgPerson".equalsIgnoreCase(currentValue.getString())) {
                        valueNode.setValue(new Value(userObjectClass));
                    }
                }
            }
        }

        return leafNode;
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
        return true;
    }

    @Override
    public List<ExprNode> getOrder(BranchNode branchNode, List<ExprNode> list) {
        return list;
    }
}
