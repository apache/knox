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
package org.apache.knox.gateway.filter;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.util.urltemplate.Matcher;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Template;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PathAclParser {
  private static PathAclsAuthorizationMessages log = MessagesFactory.get(
      PathAclsAuthorizationMessages.class);
  /* A Map of path to ACLs (users, groups, ips) to match */
  public Map<Matcher, AclParser> rulesMap = new HashMap<>();
  public PathAclParser() {
    super();
  }

  public void parsePathAcls(final String resourceRole,
      Map<String, String> rawRules) throws InvalidACLException {
    if (rawRules != null && !rawRules.isEmpty()) {
      for (final Map.Entry<String, String> rules : rawRules.entrySet()) {
        final String acls = rules.getValue();
        final Matcher urlMatcher = new Matcher();
        final AclParser aclParser = new AclParser();
        final Template urlPatternTemplate;
        if (acls != null) {
          final String path = acls.substring(0, acls.indexOf(';'));
          final String aclRules = acls.substring(acls.indexOf(';') + 1);

          log.aclsFoundForResource(rules.getKey());
          if (StringUtils.isBlank(path) || StringUtils.isBlank(aclRules)) {
            log.invalidAclsFoundForResource(rules.getKey());
            throw new InvalidACLException(
                "Invalid Path ACLs specified for rule: " + rules.getKey());
          }
          log.aclsFoundForResource(rules.getKey());

          try {
            urlPatternTemplate = Parser.parseTemplate(path);
          } catch (URISyntaxException e) {
            log.invalidURLPattern(rules.getKey());
            throw new InvalidACLException(
                "Invalid URL pattern for rule: " + rules.getKey());
          }

          urlMatcher.add(urlPatternTemplate, rules.getKey());
          /* Reuse the code that parses users, groups and ips*/
          aclParser.parseAcls(resourceRole, aclRules);
          /* Save our rule and the parsed path */
          rulesMap.put(urlMatcher, aclParser);
        }

      }
    } else {
      log.noAclsFoundForResource(resourceRole);
    }
  }

  public Map getRulesMap() {
    return Collections.unmodifiableMap(rulesMap);
  }

}