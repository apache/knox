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

import {CategoryWizard} from "./category-wizard";
import {AuthenticationProviderConfig} from "./authentication-provider-config";
import {LDAPProviderConfig} from "./ldap-provider-config";
import {PAMProviderConfig} from "./pam-provider-config";
import {KerberosProviderConfig} from "./kerberos-provider-config";
import {PreAuthSSOProviderConfig} from "./preauth-sso-provider-config";
import {SSOCookieProviderConfig} from "./sso-cookie-provider-config";
import {JWTProviderConfig} from "./jwt-provider-config";
import {CASProviderConfig} from "./cas-provider-config";
import {SAMLProviderConfig} from "./saml-provider-config";
import {OIDCProviderConfig} from "./oidc-provider-config";
import {OAUTHProviderConfig} from "./oauth-provider-config";
import {AnonymousProviderConfig} from "./AnonymousProviderConfig";

export class AuthenticationWizard extends CategoryWizard {

  private stepCount: number = 4;

  // Authentication provider types
  private static AUTH_LDAP: string       = 'LDAP';
  private static AUTH_PAM: string        = 'PAM';
  private static AUTH_HADOOP: string     = 'Kerberos';
  private static AUTH_SSO: string        = 'SSO';
  private static AUTH_SSO_COOKIE: string = 'SSO Cookie';
  private static AUTH_JWT: string        = 'JSON Web Tokens';
  private static AUTH_CAS: string        = 'CAS';
  private static AUTH_OAUTH: string      = 'OAuth';
  private static AUTH_SAML: string       = 'SAML';
  private static AUTH_OIDC: string       = 'OpenID Connect';
  private static AUTH_ANONYMOUS: string  = 'Anonymous';
  private static authTypes: string[] = [ AuthenticationWizard.AUTH_LDAP,
                                         AuthenticationWizard.AUTH_PAM,
                                         AuthenticationWizard.AUTH_HADOOP,
                                         AuthenticationWizard.AUTH_SSO,
                                         AuthenticationWizard.AUTH_SSO_COOKIE,
                                         AuthenticationWizard.AUTH_JWT,
                                         AuthenticationWizard.AUTH_CAS,
                                         AuthenticationWizard.AUTH_OAUTH,
                                         AuthenticationWizard.AUTH_SAML,
                                         AuthenticationWizard.AUTH_OIDC,
                                         AuthenticationWizard.AUTH_ANONYMOUS
                                       ];

  private static typeConfigMap: Map<string, typeof AuthenticationProviderConfig> =
           new Map([ [AuthenticationWizard.AUTH_LDAP,       LDAPProviderConfig],
                     [AuthenticationWizard.AUTH_PAM,        PAMProviderConfig],
                     [AuthenticationWizard.AUTH_HADOOP,     KerberosProviderConfig],
                     [AuthenticationWizard.AUTH_SSO,        PreAuthSSOProviderConfig],
                     [AuthenticationWizard.AUTH_SSO_COOKIE, SSOCookieProviderConfig],
                     [AuthenticationWizard.AUTH_JWT,        JWTProviderConfig],
                     [AuthenticationWizard.AUTH_CAS,        CASProviderConfig],
                     [AuthenticationWizard.AUTH_OAUTH,      OAUTHProviderConfig],
                     [AuthenticationWizard.AUTH_SAML,       SAMLProviderConfig],
                     [AuthenticationWizard.AUTH_OIDC,       OIDCProviderConfig],
                     [AuthenticationWizard.AUTH_ANONYMOUS,  AnonymousProviderConfig]
                   ] as [string, typeof AuthenticationProviderConfig][]);


  getTypes(): string[] {
    return AuthenticationWizard.authTypes;
  }

  getSteps(): number {
    return this.stepCount;
  }

  onChange() {
    let configType = AuthenticationWizard.typeConfigMap.get(this.selectedType);
    if (configType) {
      this.providerConfig = Object.create(configType.prototype) as AuthenticationProviderConfig;
      this.providerConfig.constructor.apply(this.providerConfig);
      (this.providerConfig as AuthenticationProviderConfig).setType(this.selectedType);
    } else {
      console.debug('AuthenticationWizard --> No provider configuration type mapped for ' + this.selectedType);
      this.providerConfig = null;
    }
  }

  getProviderConfig(): AuthenticationProviderConfig {
    return (this.providerConfig as AuthenticationProviderConfig);
  }

}