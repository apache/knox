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

export class PAMProviderConfig extends AuthenticationProviderConfig {

  static SESSION_TIMEOUT  = 'Session Timeout';

  private static displayPropertyNames = [ PAMProviderConfig.SESSION_TIMEOUT ];

  private static displayPropertyNameBindings: Map<string, string> =
                            new Map([
                              [PAMProviderConfig.SESSION_TIMEOUT, 'sessionTimeout']
                            ]);


  constructor() {
    super('ShiroProvider');
    this.setParam('main.pamRealm', 'org.apache.knox.gateway.shirorealm.KnoxPamRealm');
    this.setParam('main.pamRealm.service', 'login');
    this.setParam('urls./**', 'authcBasic');
  }

  getDisplayPropertyNames(): string[] {
    return PAMProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string) {
    return PAMProviderConfig.displayPropertyNameBindings.get(name);
  }

  // TODO: PJZ: Shiro-based providers have param ordering requirements; need to accommodate that
}