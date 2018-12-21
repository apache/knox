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
import {OrderedParamContainer} from './ordered-param-container';
import {ValidationUtils} from '../utils/validation-utils';

export class LDAPProviderConfig extends AuthenticationProviderConfig implements OrderedParamContainer {
    private static SESSION_TIMEOUT = 'Session Timeout';
    private static DN_TEMPLATE = 'User DN Template';
    private static URL = 'URL';
    private static MECHANISM = 'Mechanism';
    private static REALM = 'Realm';
    private static CONTEXT_FACTORY = 'LDAP Context Factory';
    private static REALM_CONTEXT_FACTORY = 'Realm Context Factory';
    private static AUTH_CHAIN = 'Authentication Chain';

    private static displayPropertyNames = [LDAPProviderConfig.SESSION_TIMEOUT,
        LDAPProviderConfig.URL,
        LDAPProviderConfig.DN_TEMPLATE,
        LDAPProviderConfig.MECHANISM,
        LDAPProviderConfig.AUTH_CHAIN
    ];

    private static displayPropertyNameBindings: Map<string, string> =
        new Map([
            [LDAPProviderConfig.SESSION_TIMEOUT, 'sessionTimeout'],
            [LDAPProviderConfig.DN_TEMPLATE, 'main.ldapRealm.userDnTemplate'],
            [LDAPProviderConfig.URL, 'main.ldapRealm.contextFactory.url'],
            [LDAPProviderConfig.MECHANISM, 'main.ldapRealm.contextFactory.authenticationMechanism'],
            [LDAPProviderConfig.REALM, 'main.ldapRealm'],
            [LDAPProviderConfig.CONTEXT_FACTORY, 'main.ldapContextFactory'],
            [LDAPProviderConfig.REALM_CONTEXT_FACTORY, 'main.ldapRealm.contextFactory'],
            [LDAPProviderConfig.AUTH_CHAIN, 'urls./**']
        ] as [string, string][]);

    private static paramsOrder: string[] =
        [LDAPProviderConfig.displayPropertyNameBindings.get(LDAPProviderConfig.SESSION_TIMEOUT),
            LDAPProviderConfig.displayPropertyNameBindings.get(LDAPProviderConfig.REALM),
            LDAPProviderConfig.displayPropertyNameBindings.get(LDAPProviderConfig.CONTEXT_FACTORY),
            LDAPProviderConfig.displayPropertyNameBindings.get(LDAPProviderConfig.REALM_CONTEXT_FACTORY),
            LDAPProviderConfig.displayPropertyNameBindings.get(LDAPProviderConfig.DN_TEMPLATE),
            LDAPProviderConfig.displayPropertyNameBindings.get(LDAPProviderConfig.URL),
            // user search attr name,              // TODO: PJZ: Define Me
            // authzn enabled,                     // TODO: PJZ: Define Me
            // realm context fact system user,     // TODO: PJZ: Define Me
            // realm context fact system user pwd, // TODO: PJZ: Define Me
            LDAPProviderConfig.displayPropertyNameBindings.get(LDAPProviderConfig.MECHANISM),
            // user object class,                  // TODO: PJZ: Define Me
            // realm search base,                  // TODO: PJZ: Define Me
            // realm user search base              // TODO: PJZ: Define Me
            LDAPProviderConfig.displayPropertyNameBindings.get(LDAPProviderConfig.AUTH_CHAIN)
        ];

    constructor() {
        super('ShiroProvider');
        this.setParam(this.getDisplayNamePropertyBinding(LDAPProviderConfig.REALM),
            'org.apache.hadoop.gateway.shirorealm.KnoxLdapRealm');
        this.setParam(this.getDisplayNamePropertyBinding(LDAPProviderConfig.CONTEXT_FACTORY),
            'org.apache.hadoop.gateway.shirorealm.KnoxLdapContextFactory');
        this.setParam(this.getDisplayNamePropertyBinding(LDAPProviderConfig.REALM_CONTEXT_FACTORY), '$ldapContextFactory');
        this.setParam(this.getDisplayNamePropertyBinding(LDAPProviderConfig.AUTH_CHAIN), 'authcBasic');
    }

    getDisplayPropertyNames(): string[] {
        return LDAPProviderConfig.displayPropertyNames;
    }

    getDisplayNamePropertyBinding(name: string) {
        return LDAPProviderConfig.displayPropertyNameBindings.get(name);
    }

    getOrderedParamNames(): string[] {
        return LDAPProviderConfig.paramsOrder;
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
            case LDAPProviderConfig.SESSION_TIMEOUT:
                isValid = this.isTimeoutValid();
                break;
            case LDAPProviderConfig.DN_TEMPLATE:
                isValid = this.isDnTemplateValid();
                break;
            case LDAPProviderConfig.URL:
                isValid = this.isLdapURLValid();
                break;
            default:
                isValid = true;
        }

        return isValid;
    }

    private isTimeoutValid(): boolean {
        let isValid = true;

        let timeout = this.getParam(this.getDisplayNamePropertyBinding(LDAPProviderConfig.SESSION_TIMEOUT));
        if (timeout) {
            isValid = ValidationUtils.isValidNumber(timeout);
            if (!isValid) {
                console.debug(LDAPProviderConfig.SESSION_TIMEOUT + ' value is not valid.');
            }
        }
        return isValid;
    }

    private isLdapURLValid(): boolean {
        let isValid = true;

        let url = this.getParam(this.getDisplayNamePropertyBinding(LDAPProviderConfig.URL));
        if (url) {
            isValid = ValidationUtils.isValidLdapURL(url);
            if (!isValid) {
                console.debug(LDAPProviderConfig.URL + ' value is not valid.');
            }
        } else {
            isValid = false; // URL must be specified
        }

        return isValid;
    }

    private isDnTemplateValid(): boolean {
        let isValid = true;

        let dnTemplate = this.getParam(this.getDisplayNamePropertyBinding(LDAPProviderConfig.DN_TEMPLATE));
        if (dnTemplate) {
            isValid = ValidationUtils.isValidDNTemplate(dnTemplate);
            if (!isValid) {
                console.debug(LDAPProviderConfig.DN_TEMPLATE + ' value is not valid.');
            }
        }
        return isValid;
    }
}
