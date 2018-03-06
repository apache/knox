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

export class CASProviderConfig extends AuthenticationProviderConfig {

  static CALLBACK_URL         = 'Callback URL';
  static LOGIN_URL            = 'Login URL';
  static PROTOCOL             = 'Protocol';
  static COOKIE_DOMAIN_SUFFIX = 'Cookie Domain Suffix';

  private static displayPropertyNames = [ CASProviderConfig.CALLBACK_URL,
                                          CASProviderConfig.LOGIN_URL,
                                          CASProviderConfig.PROTOCOL,
                                          CASProviderConfig.COOKIE_DOMAIN_SUFFIX
                                        ];

  private static displayPropertyNameBindings: Map<string, string> =
                                        new Map([
                                          [CASProviderConfig.CALLBACK_URL,         'pac4j.callbackUrl'],
                                          [CASProviderConfig.COOKIE_DOMAIN_SUFFIX, 'pac4j.cookie.domain.suffix'],
                                          [CASProviderConfig.LOGIN_URL,            'cas.loginUrl'],
                                          [CASProviderConfig.PROTOCOL,             'cas.protocol']
                                        ]);


  constructor() {
    super('pac4j', AuthenticationProviderConfig.FEDERATION_ROLE);
  }

  getDisplayPropertyNames(): string[] {
    return CASProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string) {
    return CASProviderConfig.displayPropertyNameBindings.get(name);
  }

}