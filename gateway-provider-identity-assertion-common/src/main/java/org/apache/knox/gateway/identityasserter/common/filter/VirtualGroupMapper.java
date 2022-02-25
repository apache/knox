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
package org.apache.knox.gateway.identityasserter.common.filter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.knox.gateway.IdentityAsserterMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.plang.Arity;
import org.apache.knox.gateway.plang.AbstractSyntaxTree;
import org.apache.knox.gateway.plang.Interpreter;

public class VirtualGroupMapper {
    private final IdentityAsserterMessages LOG = MessagesFactory.get(IdentityAsserterMessages.class);
    private final Map<String, AbstractSyntaxTree> virtualGroupToPredicateMap;

    public VirtualGroupMapper(Map<String, AbstractSyntaxTree> virtualGroupToPredicateMap) {
        this.virtualGroupToPredicateMap = virtualGroupToPredicateMap;
    }

    /**
     *  @return all virtual groups where the corresponding predicate matches
     */
    public Set<String> mapGroups(String username, Set<String> groups, ServletRequest request) {
        Set<String> virtualGroups = new HashSet<>();
        for (Map.Entry<String, AbstractSyntaxTree> each : virtualGroupToPredicateMap.entrySet()) {
            String virtualGroupName = each.getKey();
            AbstractSyntaxTree predicate = each.getValue();
            if (evalPredicate(virtualGroupName, username, groups, predicate, request)) {
                virtualGroups.add(virtualGroupName);
                LOG.addingUserToVirtualGroup(username, virtualGroupName, predicate);
            }
        }
        LOG.virtualGroups(username, groups, virtualGroups);
        return virtualGroups;
    }

    /**
     * @return true if the user should be added to the virtual group based on the given predicate
     */
    private boolean evalPredicate(String virtualGroupName, String userName, Set<String> ldapGroups, AbstractSyntaxTree predicate, ServletRequest request) {
        Interpreter interpreter = new Interpreter();
        interpreter.addConstant("username", userName);
        interpreter.addConstant("groups", new ArrayList<>(ldapGroups));
        addRequestFunctions(request, interpreter);
        LOG.checkingVirtualGroup(userName, ldapGroups, virtualGroupName, predicate);
        Object result = interpreter.eval(predicate);
        if (!(result instanceof Boolean)) {
            LOG.invalidResult(virtualGroupName, predicate, result);
            return false;
        }
        return (boolean)result;
    }

    private void addRequestFunctions(ServletRequest req, Interpreter interpreter) {
        if (req instanceof HttpServletRequest) {
            interpreter.addFunction("request-attribute", Arity.UNARY, params ->
                    ensureNotNull(req.getAttribute((String)params.get(0))));
            interpreter.addFunction("request-header", Arity.UNARY, params ->
                    ensureNotNull(((HttpServletRequest) req).getHeader((String)params.get(0))));
            interpreter.addFunction("session", Arity.UNARY, params ->
                    ensureNotNull(sessionAttribute((HttpServletRequest) req, (String)params.get(0))));
        }
    }

    private String ensureNotNull(Object value) {
        return value == null ? "" : value.toString();
    }

    private Object sessionAttribute(HttpServletRequest req, String key) {
        HttpSession session = req.getSession(false);
        return session != null ? session.getAttribute(key) : "";
    }
}
