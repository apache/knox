/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {IdentityAssertionProviderConfig} from "./identity-assertion-provider-config";
import {ValidationUtils} from "../utils/validation-utils";

export class GroupLookupAssertionProviderConfig extends IdentityAssertionProviderConfig {

  private static GROUP_MAPPING: string       = 'Group Mapping';
  private static URL: string                 = 'LDAP URL';
  private static BIND_USER: string           = 'Bind User';
  private static BIND_PWD: string            = 'Bind Password';
  private static USER_SEARCH_FILTER: string  = 'User Search Filter';
  private static USER_BASE: string           = 'User Search Base';
  private static GROUP_SEARCH_FILTER: string = 'Group Search Filter';
  private static MEMBER_SEARCH_ATTR: string  = 'Group Member Attribute';
  private static GROUP_SEARCH_ATTR: string   = 'Group Name Attribute';


  private static displayPropertyNames = [ GroupLookupAssertionProviderConfig.URL,
                                          GroupLookupAssertionProviderConfig.BIND_USER,
                                          GroupLookupAssertionProviderConfig.BIND_PWD,
                                          GroupLookupAssertionProviderConfig.USER_BASE,
                                          GroupLookupAssertionProviderConfig.USER_SEARCH_FILTER,
                                          GroupLookupAssertionProviderConfig.GROUP_SEARCH_FILTER,
                                          GroupLookupAssertionProviderConfig.MEMBER_SEARCH_ATTR,
                                          GroupLookupAssertionProviderConfig.GROUP_SEARCH_ATTR
                                        ];

  private static displayPropertyNameBindings: Map<string, string> =
    new Map([
      [GroupLookupAssertionProviderConfig.GROUP_MAPPING, 'hadoop.security.group.mapping'],
      [GroupLookupAssertionProviderConfig.BIND_USER, 'hadoop.security.group.mapping.ldap.bind.user'],
      [GroupLookupAssertionProviderConfig.BIND_PWD, 'hadoop.security.group.mapping.ldap.bind.password'],
      [GroupLookupAssertionProviderConfig.URL, 'hadoop.security.group.mapping.ldap.url'],
      [GroupLookupAssertionProviderConfig.USER_BASE, 'hadoop.security.group.mapping.ldap.base'],
      [GroupLookupAssertionProviderConfig.USER_SEARCH_FILTER, 'hadoop.security.group.mapping.ldap.search.filter.user'],
      [GroupLookupAssertionProviderConfig.GROUP_SEARCH_FILTER, 'hadoop.security.group.mapping.ldap.search.filter.group'],
      [GroupLookupAssertionProviderConfig.MEMBER_SEARCH_ATTR, 'hadoop.security.group.mapping.ldap.search.attr.member'],
      [GroupLookupAssertionProviderConfig.GROUP_SEARCH_ATTR, 'hadoop.security.group.mapping.ldap.search.attr.group.name']
    ]);

  constructor() {
    super('HadoopGroupProvider');
    this.setParam(this.getDisplayNamePropertyBinding(GroupLookupAssertionProviderConfig.GROUP_MAPPING),
                  'org.apache.hadoop.security.LdapGroupsMapping');
  }

  getDisplayPropertyNames(): string[] {
    return GroupLookupAssertionProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string) {
    return GroupLookupAssertionProviderConfig.displayPropertyNameBindings.get(name);
  }

  isPasswordParam(name: string): boolean {
    return (name === GroupLookupAssertionProviderConfig.BIND_PWD);
  }

  isValidParamValue(paramName: string): boolean {
    let isValid: boolean;

    switch (paramName) {
      case GroupLookupAssertionProviderConfig.BIND_USER:
        isValid = this.isBindUserValid();
        break;
      case GroupLookupAssertionProviderConfig.URL:
        isValid = this.isLdapURLValid();
        break;
      case GroupLookupAssertionProviderConfig.BIND_PWD:
      case GroupLookupAssertionProviderConfig.USER_BASE:
      case GroupLookupAssertionProviderConfig.USER_SEARCH_FILTER:
      case GroupLookupAssertionProviderConfig.GROUP_SEARCH_FILTER:
      case GroupLookupAssertionProviderConfig.MEMBER_SEARCH_ATTR:
      case GroupLookupAssertionProviderConfig.GROUP_SEARCH_ATTR:
      default:
        isValid = true;
    }

    return isValid;
  }

  private isBindUserValid(): boolean {
    let isValid: boolean = true;

    let url = this.getParam(this.getDisplayNamePropertyBinding(GroupLookupAssertionProviderConfig.BIND_USER));
    if (url) {
      isValid = ValidationUtils.isValidDNTemplate(url);
      if (!isValid) {
        console.debug(GroupLookupAssertionProviderConfig.BIND_USER + ' value is not a valid DN');
      }
    }

    return isValid;
  }

  private isLdapURLValid(): boolean {
    let isValid: boolean = true;

    let url = this.getParam(this.getDisplayNamePropertyBinding(GroupLookupAssertionProviderConfig.URL));
    if (url) {
      isValid = ValidationUtils.isValidLdapURL(url);
      if (!isValid) {
        console.debug(GroupLookupAssertionProviderConfig.URL+ ' value is not valid.');
      }
    } else {
      isValid = false; // URL must be specified
    }

    return isValid;
  }

  private isDnTemplateValid(): boolean {
    let isValid: boolean = true;

    let dnTemplate = this.getParam(this.getDisplayNamePropertyBinding(GroupLookupAssertionProviderConfig.BIND_USER));
    if (dnTemplate) {
      isValid = ValidationUtils.isValidDNTemplate(dnTemplate);
      if (!isValid) {
        console.debug(GroupLookupAssertionProviderConfig.BIND_USER + ' value is not valid.');
      }
    }
    return isValid;
  }

}