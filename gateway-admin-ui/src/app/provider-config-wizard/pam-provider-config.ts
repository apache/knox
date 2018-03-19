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
import {OrderedParamContainer} from "./ordered-param-container";
import {ValidationUtils} from "../utils/validation-utils";

export class PAMProviderConfig extends AuthenticationProviderConfig implements OrderedParamContainer {

  static SESSION_TIMEOUT  = 'Session Timeout';
  static REALM            = 'Realm';
  static SERVICE          = 'Service';
  static AUTH_CHAIN       = 'Authentication Chain';

  private static displayPropertyNames = [ PAMProviderConfig.SESSION_TIMEOUT,
                                          PAMProviderConfig.SERVICE ];

  private static displayPropertyNameBindings: Map<string, string> =
                            new Map([
                              [PAMProviderConfig.SESSION_TIMEOUT, 'sessionTimeout'],
                              [PAMProviderConfig.REALM,           'main.pamRealm'],
                              [PAMProviderConfig.SERVICE,         'main.pamRealm.service'],
                              [PAMProviderConfig.AUTH_CHAIN,      'urls./**']
                            ]);

  private static paramsOrder: string[] =
                            [ PAMProviderConfig.displayPropertyNameBindings.get(PAMProviderConfig.SESSION_TIMEOUT),
                              PAMProviderConfig.displayPropertyNameBindings.get(PAMProviderConfig.REALM),
                              PAMProviderConfig.displayPropertyNameBindings.get(PAMProviderConfig.SERVICE),
                              PAMProviderConfig.displayPropertyNameBindings.get(PAMProviderConfig.AUTH_CHAIN)
                            ];

  constructor() {
    super('ShiroProvider');
    this.setParam(this.getDisplayNamePropertyBinding(PAMProviderConfig.REALM),
                  'org.apache.knox.gateway.shirorealm.KnoxPamRealm');
    this.setParam(this.getDisplayNamePropertyBinding(PAMProviderConfig.SERVICE), 'login');
    this.setParam(this.getDisplayNamePropertyBinding(PAMProviderConfig.AUTH_CHAIN), 'authcBasic');
  }

  getDisplayPropertyNames(): string[] {
    return PAMProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string) {
    return PAMProviderConfig.displayPropertyNameBindings.get(name);
  }

  getOrderedParamNames(): string[] {
    return PAMProviderConfig.paramsOrder;
  }

  orderParams(params: Map<string, string>): Map<string, string> {
    let result = new Map<string, string>();

    for (let name of this.getOrderedParamNames()) {
      let value = params[name];
      if (value) {
        result[name] = value;
      }
    }

    return result;
  }

  isValidParamValue(paramName: string): boolean {
    let isValid: boolean;

    switch (paramName) {
      case PAMProviderConfig.SESSION_TIMEOUT:
        isValid = this.isValidTimeout();
        break;
      default:
        isValid = true;
    }

    return isValid;
  }

  private isValidTimeout(): boolean {
    let isValid: boolean = true;

    let timeout = this.getParam(this.getDisplayNamePropertyBinding(PAMProviderConfig.SESSION_TIMEOUT));
    if (timeout) {
      isValid = ValidationUtils.isValidNumber(timeout);
    }

    return isValid;
  }

}