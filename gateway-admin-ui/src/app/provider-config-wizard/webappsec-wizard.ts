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
import {ProviderConfig} from "../resource-detail/provider-config";
import {ProviderContributorWizard} from "./provider-contributor-wizard";
import {HaProviderConfig} from "./ha-provider-config";
import {DisplayBindingProviderConfig} from "./display-binding-provider-config";
import {HaWizard} from "./ha-wizard";
import {WebAppSecurityProviderConfig} from "./webappsec-provider-config";
import {OIDCProviderConfig} from "./oidc-provider-config";
import {JWTProviderConfig} from "./jwt-provider-config";
import {SAMLProviderConfig} from "./saml-provider-config";
import {OAUTHProviderConfig} from "./oauth-provider-config";
import {PreAuthSSOProviderConfig} from "./preauth-sso-provider-config";
import {AuthenticationProviderConfig} from "./authentication-provider-config";
import {AuthenticationWizard} from "./authentication-wizard";
import {CASProviderConfig} from "./cas-provider-config";
import {SSOCookieProviderConfig} from "./sso-cookie-provider-config";
import {LDAPProviderConfig} from "./ldap-provider-config";
import {PAMProviderConfig} from "./pam-provider-config";
import {AnonymousProviderConfig} from "./AnonymousProviderConfig";
import {KerberosProviderConfig} from "./kerberos-provider-config";
import {CSRFProviderConfig} from "./csrf-provider-config";
import {CORSProviderConfig} from "./cors-provider-config";
import {WebAppSecurityContributor} from "./webappsec-contributor";
import {STSProviderConfig} from "./sts-provider-config";
import {XFrameOptionsProviderConfig} from "./xframeoptions-provider-config";
import {XContentTypeOptionsProviderConfig} from "./xcontent-type-options-provider-config";

export class WebAppSecurityWizard extends CategoryWizard implements ProviderContributorWizard {

  private stepCount: number = 4;

  // WebAppSec provider types
  private static CSRF: string          = 'Cross-Site Request Forgery';
  private static CORS: string          = 'Cross-Origin Resource Sharing';
  private static XFRAME: string        = 'X-Frame-Options';
  private static XCONTENT_TYPE: string = 'X-Content-Type-Options';
  private static STS: string           = 'Strict Transport Security';

  private static webAppSecTypes: string[] = [ WebAppSecurityWizard.CSRF,
                                              WebAppSecurityWizard.CORS,
                                              WebAppSecurityWizard.XFRAME,
                                              WebAppSecurityWizard.XCONTENT_TYPE,
                                              WebAppSecurityWizard.STS
                                            ]

  private static typeConfigMap: Map<string, typeof WebAppSecurityContributor> =
                                            new Map([
                                              [WebAppSecurityWizard.CSRF,          CSRFProviderConfig],
                                              [WebAppSecurityWizard.CORS,          CORSProviderConfig],
                                              [WebAppSecurityWizard.XFRAME,        XFrameOptionsProviderConfig],
                                              [WebAppSecurityWizard.XCONTENT_TYPE, XContentTypeOptionsProviderConfig],
                                              [WebAppSecurityWizard.STS,           STSProviderConfig]
                                            ] as [string, typeof WebAppSecurityContributor][]);


  getTypes(): string[] {
    return WebAppSecurityWizard.webAppSecTypes;
  }

  getSteps(): number {
    return this.stepCount;
  }

  onChange() {
    let configType = WebAppSecurityWizard.typeConfigMap.get(this.selectedType);
    if (configType) {
      this.providerConfig = Object.create(configType.prototype) as WebAppSecurityContributor;
      this.providerConfig.constructor.apply(this.providerConfig);
      (this.providerConfig as WebAppSecurityContributor).setType(this.selectedType);
    } else {
      console.debug('WebAppSecurityWizard --> No provider configuration type mapped for ' + this.selectedType);
      this.providerConfig = null;
    }
  }

  getProviderConfig(): ProviderConfig {
    return (this.providerConfig as WebAppSecurityProviderConfig);
  }

  getProviderRole(): string {
    return 'webappsec';
  }

  createNewProviderConfig(): ProviderConfig {
    return new WebAppSecurityProviderConfig();
  }

  contribute(target: ProviderConfig) {
    for (let paramName in this.providerConfig.params) {
      (target as DisplayBindingProviderConfig).setParam(paramName,
                                                        (this.providerConfig as DisplayBindingProviderConfig).getParam(paramName));
    }
  }

}
