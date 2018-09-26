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

import java.util.ArrayList;
import java.util.Collections;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.util.IpAddressValidator;

/**
 *
 */
public class AclParser {
  private static AclsAuthorizationMessages log = MessagesFactory.get( AclsAuthorizationMessages.class );

  public ArrayList<String> users;
  public ArrayList<String> groups;
  public boolean anyUser = true;
  public boolean anyGroup = true;
  public IpAddressValidator ipv;


  public AclParser() {
    users = new ArrayList<>();
    groups = new ArrayList<>();
    ipv = new IpAddressValidator(null);
  }
  
  public void parseAcls(String resourceRole, String acls) throws InvalidACLException {
    if (acls != null) {
      String[] parts = acls.split(";");
      if (parts.length != 3) {
        log.invalidAclsFoundForResource(resourceRole);
        throw new InvalidACLException("Invalid ACLs specified for requested resource: " + resourceRole);
      } else {
        log.aclsFoundForResource(resourceRole);
      }
      parseUserAcls(parts);
      
      parseGroupAcls(parts);

      parseIpAddressAcls(parts);
    } else {
      log.noAclsFoundForResource(resourceRole);
    }
  }

  private void parseUserAcls(String[] parts) {
    Collections.addAll(users, parts[0].split(","));
    if (!users.contains("*")) {
      anyUser = false;
    }
  }

  private void parseGroupAcls(String[] parts) {
    Collections.addAll(groups, parts[1].split(","));
    if (!groups.contains("*")) {
      anyGroup = false;
    }
  }

  private void parseIpAddressAcls(String[] parts) {
    ipv = new IpAddressValidator(parts[2]);
  }
}