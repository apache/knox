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

import {AuthenticationProviderConfig} from "./authentication-provider-config";

export class LDAPProviderConfig extends AuthenticationProviderConfig {

  static SESSION_TIMEOUT  = 'Session Timeout';
  static DN_TEMPLATE      = 'User DN Template';
  static URL              = 'URL';
  static MECHANISM        = 'Mechanism';

  private static displayPropertyNames = [ LDAPProviderConfig.SESSION_TIMEOUT,
                                          LDAPProviderConfig.DN_TEMPLATE,
                                          LDAPProviderConfig.URL,
                                          LDAPProviderConfig.MECHANISM
                                        ];

  private static displayPropertyNameBindings: Map<string, string> =
                            new Map([
                              [LDAPProviderConfig.SESSION_TIMEOUT, 'sessionTimeout'],
                              [LDAPProviderConfig.DN_TEMPLATE, 'main.ldapRealm.userDnTemplate'],
                              [LDAPProviderConfig.URL, 'main.ldapRealm.contextFactory.url'],
                              [LDAPProviderConfig.MECHANISM, 'main.ldapRealm.contextFactory.authenticationMechanism']
                            ]);


  constructor() {
    super('ShiroProvider');
    this.setParam('main.ldapRealm', 'org.apache.hadoop.gateway.shirorealm.KnoxLdapRealm');
    this.setParam('main.ldapContextFactory', 'org.apache.hadoop.gateway.shirorealm.KnoxLdapContextFactory');
    this.setParam('main.ldapRealm.contextFactory', '$ldapContextFactory');
    this.setParam('urls./**', 'authcBasic');
  }

  getDisplayPropertyNames(): string[] {
    return LDAPProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string) {
    return LDAPProviderConfig.displayPropertyNameBindings.get(name);
  }

  // TODO: PJZ: Shiro-based providers have param ordering requirements; need to accommodate that
}