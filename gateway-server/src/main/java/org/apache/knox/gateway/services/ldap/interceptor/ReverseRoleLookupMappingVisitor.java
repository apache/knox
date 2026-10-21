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
package org.apache.knox.gateway.services.ldap.interceptor;

import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.filter.BranchNode;
import org.apache.directory.api.ldap.model.filter.EqualityNode;
import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.filter.FilterVisitor;
import org.apache.directory.api.ldap.model.filter.LeafNode;
import org.apache.directory.api.ldap.model.filter.OrNode;
import org.apache.directory.api.ldap.model.filter.SimpleNode;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.AttributeType;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.knox.gateway.services.ldap.LDAPRolesLookupService;
import org.apache.knox.gateway.services.ldap.RoleAssignees;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * FilterVisitor that maps LDAP search filters that use the memberOf attribute
 * to individual DNs if the backend doesn't support memberOf.
 */
public class ReverseRoleLookupMappingVisitor implements FilterVisitor {

    private final SchemaManager schemaManager;
    private final LDAPRolesLookupService ldapRolesLookupService;
    private final Function<String, String> groupDnCreatorFunction;

    public ReverseRoleLookupMappingVisitor(SchemaManager schemaManager, LDAPRolesLookupService ldapRolesLookupService, Function<String, String> groupDnCreatorFunction) {
        this.schemaManager = schemaManager;
        this.ldapRolesLookupService = ldapRolesLookupService;
        this.groupDnCreatorFunction = groupDnCreatorFunction;
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
                throw new RuntimeException("Failed to map memberOf filter value from role to users and groups.", e);
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
                RoleAssignees assignees = getAssignees(currentValue.getString());
                if (assignees != null) {
                    List<String> users = assignees.getUsers() == null ? List.of() : assignees.getUsers();
                    List<String> groups = assignees.getGroups() == null ? List.of() : assignees.getGroups();
                    if (!users.isEmpty() || !groups.isEmpty()) {
                        OrNode orNode = new OrNode();
                        AttributeType uidAttributeType = schemaManager.getAttributeType("uid");
                        AttributeType memberOfAttributeType = schemaManager.getAttributeType("memberOf");
                        for (String user : users) {
                            orNode.addNode(new EqualityNode<>(uidAttributeType, new Value(uidAttributeType, user)));
                        }
                        for (String group : groups) {
                            orNode.addNode(new EqualityNode<>(memberOfAttributeType, new Value(memberOfAttributeType, groupDnCreatorFunction.apply(group))));
                        }
                        return orNode;
                    }
                }
            }
        }

        return leafNode;
    }

    private RoleAssignees getAssignees(String roleDn) throws Exception {
        String roleName = new Dn(roleDn).getRdn().getValue();
        return ldapRolesLookupService.lookupRoleAssignees(roleName);
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
