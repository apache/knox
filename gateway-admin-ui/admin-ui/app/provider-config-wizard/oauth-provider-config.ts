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

export class OAUTHProviderConfig extends AuthenticationProviderConfig {

    static CALLBACK_URL = 'Callback URL';
    static COOKIE_DOMAIN_SUFFIX = 'Cookie Domain Suffix';

    private static displayPropertyNames: string[] = [OAUTHProviderConfig.CALLBACK_URL,
        OAUTHProviderConfig.COOKIE_DOMAIN_SUFFIX
    ];

    private static displayPropertyNameBindings: Map<string, string> =
        new Map([
            [OAUTHProviderConfig.CALLBACK_URL, 'pac4j.callbackUrl'],
            [OAUTHProviderConfig.COOKIE_DOMAIN_SUFFIX, 'pac4j.cookie.domain.suffix']
        ]);


    constructor() {
        super('pac4j', AuthenticationProviderConfig.FEDERATION_ROLE);
    }

    getDisplayPropertyNames(): string[] {
        return OAUTHProviderConfig.displayPropertyNames;
    }

    getDisplayNamePropertyBinding(name: string) {
        return OAUTHProviderConfig.displayPropertyNameBindings.get(name);
    }

    isValidParamValue(paramName: string): boolean {
        let isValid: boolean;

        switch (paramName) {
            case OAUTHProviderConfig.CALLBACK_URL:
                isValid = this.isValidCallbackURL();
                break;
            default:
                isValid = true;
        }

        return isValid;
    }

    private isValidCallbackURL(): boolean {
        let isValid = true;

        let url = this.getParam(this.getDisplayNamePropertyBinding(OAUTHProviderConfig.CALLBACK_URL));
        if (url) {
            isValid = ValidationUtils.isValidHttpURL(url);
            if (!isValid) {
                console.debug(OAUTHProviderConfig.CALLBACK_URL + ' value is not a valid URL.');
            }
        }

        return isValid;
    }

}
