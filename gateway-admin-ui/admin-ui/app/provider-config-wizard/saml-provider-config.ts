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

import {AuthenticationProviderConfig} from './authentication-provider-config';
import {ValidationUtils} from '../utils/validation-utils';

export class SAMLProviderConfig extends AuthenticationProviderConfig {

    static CALLBACK_URL = 'Callback URL';
    static KEYSTORE_PATH = 'Keystore Path';
    static KEYSTORE_PASS = 'Keystore Password';
    static PK_PASS = 'Private Key Password';
    static ID_PROVIDER_META = 'Identity Provider Metadata Path';
    static MAX_AUTH_LIFETIME = 'Maximum Authentication Lifetime';
    static SERVICE_PROVIDER_ID = 'Service Provider Identity';
    static SERVICE_PROVIDER_META = 'Service Provider Metadata Path';
    static COOKIE_DOMAIN_SUFFIX = 'Cookie Domain Suffix';


    private static displayPropertyNames = [SAMLProviderConfig.CALLBACK_URL,
        SAMLProviderConfig.KEYSTORE_PATH,
        SAMLProviderConfig.KEYSTORE_PASS,
        SAMLProviderConfig.PK_PASS,
        SAMLProviderConfig.ID_PROVIDER_META,
        SAMLProviderConfig.MAX_AUTH_LIFETIME,
        SAMLProviderConfig.SERVICE_PROVIDER_ID,
        SAMLProviderConfig.SERVICE_PROVIDER_META,
        SAMLProviderConfig.COOKIE_DOMAIN_SUFFIX
    ];

    private static displayPropertyNameBindings: Map<string, string> =
        new Map([
            [SAMLProviderConfig.CALLBACK_URL, 'pac4j.callbackUrl'],
            [SAMLProviderConfig.COOKIE_DOMAIN_SUFFIX, 'pac4j.cookie.domain.suffix'],
            [SAMLProviderConfig.KEYSTORE_PATH, 'saml.keystorePath'],
            [SAMLProviderConfig.KEYSTORE_PASS, 'saml.keystorePassword'],
            [SAMLProviderConfig.PK_PASS, 'saml.privateKeyPassword'],
            [SAMLProviderConfig.ID_PROVIDER_META, 'saml.identityProviderMetadataPath'],
            [SAMLProviderConfig.MAX_AUTH_LIFETIME, 'saml.maximumAuthenticationLifetime'],
            [SAMLProviderConfig.SERVICE_PROVIDER_ID, 'saml.serviceProviderEntityId'],
            [SAMLProviderConfig.SERVICE_PROVIDER_META, 'saml.serviceProviderMetadataPath']
        ]);


    private static SECRET_PROPERTIES: string[] = [SAMLProviderConfig.KEYSTORE_PASS, SAMLProviderConfig.PK_PASS];

    constructor() {
        super('pac4j', AuthenticationProviderConfig.FEDERATION_ROLE);
    }

    getDisplayPropertyNames(): string[] {
        return SAMLProviderConfig.displayPropertyNames;
    }

    getDisplayNamePropertyBinding(name: string) {
        return SAMLProviderConfig.displayPropertyNameBindings.get(name);
    }

    isPasswordParam(name: string): boolean {
        return (name && SAMLProviderConfig.SECRET_PROPERTIES.indexOf(name) > -1);
    }

    isValidParamValue(paramName: string): boolean {
        let isValid: boolean;

        switch (paramName) {
            case SAMLProviderConfig.CALLBACK_URL:
                isValid = this.isValidCallbackURL();
                break;
            case SAMLProviderConfig.MAX_AUTH_LIFETIME:
                isValid = this.isValidMaxAuthLifetime();
                break;
            default:
                isValid = true;
        }

        return isValid;
    }

    private isValidCallbackURL(): boolean {
        let isValid = true;

        let url = this.getParam(this.getDisplayNamePropertyBinding(SAMLProviderConfig.CALLBACK_URL));
        if (url) {
            isValid = ValidationUtils.isValidHttpURL(url);
            if (!isValid) {
                console.debug(SAMLProviderConfig.CALLBACK_URL + ' value is not a valid URL.');
            }
        }

        return isValid;
    }

    private isValidMaxAuthLifetime(): boolean {
        let isValid = true;

        let malt = this.getParam(this.getDisplayNamePropertyBinding(SAMLProviderConfig.MAX_AUTH_LIFETIME));
        if (malt) {
            isValid = ValidationUtils.isValidNumber(malt);
            if (!isValid) {
                console.debug(SAMLProviderConfig.MAX_AUTH_LIFETIME + ' value is not a valid number.');
            }
        }

        return isValid;
    }

}
