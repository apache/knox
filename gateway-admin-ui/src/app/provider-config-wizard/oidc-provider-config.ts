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
import {SAMLProviderConfig} from "./saml-provider-config";

export class OIDCProviderConfig extends AuthenticationProviderConfig {

  static CALLBACK_URL           = 'Callback URL';
  static PROVIDER_ID            = 'Provider Identifier';
  static PROVIDER_SECRET        = 'Provider Secret';
  static PROVIDER_DISCOVERY_URL = 'Provider Discovery URL';
  static USE_NONCE              = 'Use Nonce';
  static PREF_JWS_ALGO          = 'Preferred JWS Algorithm';
  static MAX_CLOCK_SKEW         = 'Maximum Clock Skew';
  static COOKIE_DOMAIN_SUFFIX   = 'Cookie Domain Suffix';


  private static displayPropertyNames = [ OIDCProviderConfig.CALLBACK_URL,
                                          OIDCProviderConfig.PROVIDER_ID,
                                          OIDCProviderConfig.PROVIDER_SECRET,
                                          OIDCProviderConfig.PROVIDER_DISCOVERY_URL,
                                          OIDCProviderConfig.USE_NONCE,
                                          OIDCProviderConfig.PREF_JWS_ALGO,
                                          OIDCProviderConfig.MAX_CLOCK_SKEW,
                                          OIDCProviderConfig.COOKIE_DOMAIN_SUFFIX
                                        ];

  private static displayPropertyNameBindings: Map<string, string> =
                                      new Map([
                                        [OIDCProviderConfig.CALLBACK_URL,           'pac4j.callbackUrl'],
                                        [OIDCProviderConfig.COOKIE_DOMAIN_SUFFIX,   'pac4j.cookie.domain.suffix'],
                                        [OIDCProviderConfig.PROVIDER_ID,            'oidc.id'],
                                        [OIDCProviderConfig.PROVIDER_SECRET,        'oidc.secret'],
                                        [OIDCProviderConfig.PROVIDER_DISCOVERY_URL, 'oidc.discoveryUri'],
                                        [OIDCProviderConfig.USE_NONCE,              'oidc.useNonce'],
                                        [OIDCProviderConfig.PREF_JWS_ALGO,          'oidc.preferredJwsAlgorithm'],
                                        [OIDCProviderConfig.MAX_CLOCK_SKEW,         'oidc.maxClockSkew'],
                                      ]);


  constructor() {
    super('pac4j', AuthenticationProviderConfig.FEDERATION_ROLE);
  }

  getDisplayPropertyNames(): string[] {
    return OIDCProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string) {
    return OIDCProviderConfig.displayPropertyNameBindings.get(name);
  }

  isPasswordParam(name: string): boolean {
    return (name === OIDCProviderConfig.PROVIDER_SECRET);
  }

  isValid(): boolean {
    let isValid: boolean = true;

    let cbURL = this.getParam(this.getDisplayNamePropertyBinding(OIDCProviderConfig.CALLBACK_URL));
    if (cbURL) {
      let isCBURLValid = ValidationUtils.isValidURL(cbURL);
      if (!isCBURLValid) {
        console.debug(OIDCProviderConfig.CALLBACK_URL + ' value is not a valid URL.');
      }
      isValid = isValid && isCBURLValid;
    }

    let pdURL = this.getParam(this.getDisplayNamePropertyBinding(OIDCProviderConfig.PROVIDER_DISCOVERY_URL));
    if (pdURL) {
      let isPDURLValid = ValidationUtils.isValidURL(pdURL);
      if (!isPDURLValid) {
        console.debug(OIDCProviderConfig.PROVIDER_DISCOVERY_URL + ' value is not a valid URL.');
      }
      isValid = isValid && isPDURLValid;
    }

    let useNonce = this.getParam(this.getDisplayNamePropertyBinding(OIDCProviderConfig.USE_NONCE));
    if (useNonce) {
      let isNonceValid = ValidationUtils.isValidBoolean(useNonce);
      if (!isNonceValid) {
        console.debug(OIDCProviderConfig.USE_NONCE + ' value is not a valid boolean.');
      }
      isValid = isValid && isNonceValid;
    }

    let clockSkew = this.getParam(this.getDisplayNamePropertyBinding(OIDCProviderConfig.MAX_CLOCK_SKEW));
    if (clockSkew) {
      let isSkewValid = ValidationUtils.isValidNumber(clockSkew);
      if (!isSkewValid) {
        console.debug(OIDCProviderConfig.MAX_CLOCK_SKEW + ' value is not a valid number');
      }
      isValid = isValid && isSkewValid;
    }

    return isValid;
  }

}