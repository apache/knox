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

export class KerberosProviderConfig extends AuthenticationProviderConfig {

  static CONFIG_PREFIX    = 'Config Prefix';
  static SIG_SECRET       = 'Signature Secret';
  static TYPE             = 'Type';
  static ANON_ALLOWED     = 'Allow Anonymous';
  static TOKEN_VALIDITY   = 'Token Expiration';
  static COOKIE_DOMAIN    = 'Domain';
  static COOKIE_PATH      = 'Path';
  static KRB_PRINCIPAL    = 'Principal';
  static KRB_KEYTAB       = 'KeyTab';
  static KRB_RULES        = 'Name Rules';

  private static displayPropertyNames = [ KerberosProviderConfig.CONFIG_PREFIX,
                                          KerberosProviderConfig.SIG_SECRET,
                                          KerberosProviderConfig.ANON_ALLOWED,
                                          KerberosProviderConfig.TOKEN_VALIDITY,
                                          KerberosProviderConfig.COOKIE_DOMAIN,
                                          KerberosProviderConfig.COOKIE_PATH,
                                          KerberosProviderConfig.KRB_PRINCIPAL,
                                          KerberosProviderConfig.KRB_KEYTAB,
                                          KerberosProviderConfig.KRB_RULES
                                        ];

  private static displayPropertyNameBindings: Map<string, string> =
                                        new Map([
                                          [KerberosProviderConfig.CONFIG_PREFIX,  'config.prefix'],
                                          [KerberosProviderConfig.SIG_SECRET,     'signature.secret'],
                                          [KerberosProviderConfig.TYPE,           'type'],
                                          [KerberosProviderConfig.ANON_ALLOWED,   'simple.anonymous.allowed'],
                                          [KerberosProviderConfig.TOKEN_VALIDITY, 'token.validity'],
                                          [KerberosProviderConfig.COOKIE_DOMAIN,  'cookie.domain'],
                                          [KerberosProviderConfig.COOKIE_PATH,    'cookie.path'],
                                          [KerberosProviderConfig.KRB_PRINCIPAL,  'kerberos.principal'],
                                          [KerberosProviderConfig.KRB_KEYTAB,     'kerberos.keytab'],
                                          [KerberosProviderConfig.KRB_RULES,      'kerberos.name.rules']
                                        ]);


  constructor() {
    super('HadoopAuth');
    this.setParam(this.getDisplayNamePropertyBinding(KerberosProviderConfig.CONFIG_PREFIX), 'hadoop.auth.config');
    this.setParam(this.getDisplayNamePropertyBinding(KerberosProviderConfig.TYPE), 'kerberos');
  }

  getDisplayPropertyNames(): string[] {
    return KerberosProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string) {
    let propName: string;
    if (name === KerberosProviderConfig.CONFIG_PREFIX) {
      propName = KerberosProviderConfig.displayPropertyNameBindings.get(name);
    } else {
      let prefix = this.getParam(KerberosProviderConfig.displayPropertyNameBindings.get(KerberosProviderConfig.CONFIG_PREFIX));
      if (prefix) {
        propName = prefix + '.' + KerberosProviderConfig.displayPropertyNameBindings.get(name);
      } else {
        propName = KerberosProviderConfig.displayPropertyNameBindings.get(name);
      }
    }
    return propName;
  }

  isPasswordParam(name: string): boolean {
    return (name === KerberosProviderConfig.SIG_SECRET);
  }

  setParam(name: string, value: string) {
    console.debug('KerberosProviderConfig --> setParam(' + name + ', ' + value + ')'); // TODO: PJZ: DELETE ME
    if (name === this.getDisplayNamePropertyBinding(KerberosProviderConfig.CONFIG_PREFIX)) {
      // If the config prefix property has changed, then the properties need to be modified accordingly
      let prevPrefix = this.getParam(this.getDisplayNamePropertyBinding(KerberosProviderConfig.CONFIG_PREFIX));
      if (value !== prevPrefix) {
        // Iterate over those properties which have already been set
        for (let propName of this.getParamNames()) {
          console.debug('\tPrevious property ' + propName);
          if (propName.startsWith(prevPrefix)) { // If the property name includes the previous config prefix
            let suffix = propName.substring(prevPrefix.length + 1); // prefix + '.'
            let newPropName = value + '.' + suffix;
            this.setParam(newPropName, this.getParam(propName));
            console.debug('\tRenamed ' + propName + ' to ' + newPropName + ' because config prefix changed.');
            this.removeParam(propName);
            console.debug('\tDeleted ' + propName + ' because config prefix changed.');
          }
        }
        super.setParam(name, value); // Update the config prefix property itself
      }
    } else {
      super.setParam(name, value);
    }
  }

  isValidParamValue(paramName: string): boolean {
    let isValid: boolean = true;

    switch (paramName) {
      case KerberosProviderConfig.ANON_ALLOWED:
        isValid = this.isValidAllowAnon();
        break;
      case KerberosProviderConfig.TOKEN_VALIDITY:
        isValid = this.isValidTokenExpiration();
        break;
      default:
        isValid = true;
    }

    return isValid;
  }

  private isValidTokenExpiration(): boolean {
    let isValid: boolean = true;

    let tokenExpiration = this.getParam(this.getDisplayNamePropertyBinding(KerberosProviderConfig.TOKEN_VALIDITY));
    if (tokenExpiration) {
      isValid = ValidationUtils.isValidNumber(tokenExpiration);
      if (!isValid) {
        console.debug(KerberosProviderConfig.TOKEN_VALIDITY + ' value is not valid.');
      }
    }
    return isValid;
  }

  private isValidAllowAnon(): boolean {
    let isValid: boolean = true;

    let allowAnon = this.getParam(this.getDisplayNamePropertyBinding(KerberosProviderConfig.ANON_ALLOWED));
    if (allowAnon) {
      isValid = ValidationUtils.isValidBoolean(allowAnon);
      if (!isValid) {
        console.debug(KerberosProviderConfig.ANON_ALLOWED + ' value is not valid.');
      }
    }
    return isValid;
  }

}