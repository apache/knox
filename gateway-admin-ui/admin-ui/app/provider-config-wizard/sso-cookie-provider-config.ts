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

export class SSOCookieProviderConfig extends AuthenticationProviderConfig {

  static PROVIDER_URL  = 'Provider URL';

  private static displayPropertyNames = [ SSOCookieProviderConfig.PROVIDER_URL ];

  private static displayPropertyNameBindings: Map<string, string> =
                    new Map([ [SSOCookieProviderConfig.PROVIDER_URL, 'sso.authentication.provider.url'] ]);


  constructor() {
    console.debug('new SSOCookieProviderConfig()');
    super('SSOCookieProvider', AuthenticationProviderConfig.FEDERATION_ROLE);
  }

  getDisplayPropertyNames(): string[] {
    return SSOCookieProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string) {
    return SSOCookieProviderConfig.displayPropertyNameBindings.get(name);
  }

  isValidParamValue(paramName: string): boolean {
    let isValid: boolean;

    switch (paramName) {
      case SSOCookieProviderConfig.PROVIDER_URL:
        isValid = this.isValidProviderURL();
        break;
      default:
        isValid = true;
    }

    return isValid;
  }

  private isValidProviderURL(): boolean {
    let isValid: boolean = true;

    let url = this.getParam(this.getDisplayNamePropertyBinding(SSOCookieProviderConfig.PROVIDER_URL));
    if (url) {
      isValid = ValidationUtils.isValidHttpURL(url);
      if (!isValid) {
        console.debug(SSOCookieProviderConfig.PROVIDER_URL + ' value is not a valid URL.');
      }
    }
    return isValid;
  }

}