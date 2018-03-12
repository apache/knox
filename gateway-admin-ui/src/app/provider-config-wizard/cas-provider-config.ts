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
import {ValidationUtils} from "../utils/validation-utils";

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


  private static SUPPORTED_PROTOCOLS: string[] = [ 'CAS10', 'CAS20', 'CAS20_PROXY', 'CAS30', 'CAS30_PROXY', 'SAML' ];

  constructor() {
    super('pac4j', AuthenticationProviderConfig.FEDERATION_ROLE);
  }

  getDisplayPropertyNames(): string[] {
    return CASProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string) {
    return CASProviderConfig.displayPropertyNameBindings.get(name);
  }

  isValid(): boolean {
    let isValid: boolean = true;

    let cbURL = this.getParam(this.getDisplayNamePropertyBinding(CASProviderConfig.CALLBACK_URL));
    if (cbURL) {
      let isCBURLValid = ValidationUtils.isValidURL(cbURL);
      if (!isCBURLValid) {
        console.debug(CASProviderConfig.CALLBACK_URL + ' value is not a valid URL.');
      }
      isValid = isValid && isCBURLValid;
    }

    let loginURL = this.getParam(this.getDisplayNamePropertyBinding(CASProviderConfig.LOGIN_URL));
    if (loginURL) {
      let isLoginURLValid = ValidationUtils.isValidURL(loginURL);
      if (!isLoginURLValid) {
        console.debug(CASProviderConfig.LOGIN_URL + ' value is not a valid URL.');
      }
      isValid = isValid && isLoginURLValid;
    }

    let protocol = this.getParam(this.getDisplayNamePropertyBinding(CASProviderConfig.PROTOCOL));
    if (protocol) {
      let isProtocolValid = (CASProviderConfig.SUPPORTED_PROTOCOLS.indexOf(protocol) > -1);
      if (!isProtocolValid) {
        console.debug(CASProviderConfig.PROTOCOL + ' value is not a supported protocol');
      }
      isValid = isValid && isProtocolValid;
    }

    return isValid;
  }

}