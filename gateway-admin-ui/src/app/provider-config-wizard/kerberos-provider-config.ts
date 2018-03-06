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
                                          KerberosProviderConfig.TYPE,
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
                                          [KerberosProviderConfig.SIG_SECRET,     '.signature.secret'],
                                          [KerberosProviderConfig.TYPE,           '.type'],
                                          [KerberosProviderConfig.ANON_ALLOWED,   '.simple.anonymous.allowed'],
                                          [KerberosProviderConfig.TOKEN_VALIDITY, '.token.validity'],
                                          [KerberosProviderConfig.COOKIE_DOMAIN,  '.cookie.domain'],
                                          [KerberosProviderConfig.COOKIE_PATH,    '.cookie.path'],
                                          [KerberosProviderConfig.KRB_PRINCIPAL,  '.kerberos.principal'],
                                          [KerberosProviderConfig.KRB_KEYTAB,     '.kerberos.keytab'],
                                          [KerberosProviderConfig.KRB_RULES,      '.kerberos.name.rules']
                                        ]);


  constructor() {
    super('HadoopAuth');
  }

  getDisplayPropertyNames(): string[] {
    return KerberosProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string) {
    if (name === KerberosProviderConfig.CONFIG_PREFIX) {
      return KerberosProviderConfig.displayPropertyNameBindings.get(name);
    } else {
      let prefix = this.getParam(KerberosProviderConfig.displayPropertyNameBindings.get(KerberosProviderConfig.CONFIG_PREFIX));
      if (prefix) {
        return prefix + KerberosProviderConfig.displayPropertyNameBindings.get(name);
      }
    }
    return null;
  }

}