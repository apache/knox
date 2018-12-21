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

import {ValidationUtils} from '../utils/validation-utils';
import {WebAppSecurityContributor} from './webappsec-contributor';

export class CSRFProviderConfig extends WebAppSecurityContributor {
    public static CUSTOM_HEADER = 'Custom Header';
    public static METHODS_TO_IGNORE = 'Methods to Ignore';

    private static displayPropertyNames = [CSRFProviderConfig.CUSTOM_HEADER,
        CSRFProviderConfig.METHODS_TO_IGNORE
    ];

    private static displayPropertyNameBindings: Map<string, string> =
        new Map([
            [CSRFProviderConfig.CUSTOM_HEADER, 'csrf.customHeader'],
            [CSRFProviderConfig.METHODS_TO_IGNORE, 'csrf.methodsToIgnore']
        ] as [string, string][]);


    constructor() {
        super();
        this.setParam('csrf.enabled', 'true');
        this.setParam(CSRFProviderConfig.displayPropertyNameBindings.get(CSRFProviderConfig.CUSTOM_HEADER), 'X-XSRF-Header');
        this.setParam(CSRFProviderConfig.displayPropertyNameBindings.get(CSRFProviderConfig.METHODS_TO_IGNORE), 'GET,OPTIONS,HEAD');
    }

    getDisplayPropertyNames(): string[] {
        return CSRFProviderConfig.displayPropertyNames;
    }

    getDisplayNamePropertyBinding(name: string): string {
        return CSRFProviderConfig.displayPropertyNameBindings.get(name);
    }

    isValidParamValue(paramName: string): boolean {
        let isValid = true;

        let value = this.getParam(this.getDisplayNamePropertyBinding(paramName));
        if (value) {
            switch (paramName) {
                case CSRFProviderConfig.CUSTOM_HEADER:
                    isValid = ValidationUtils.isValidString(value);
                    break;
                case CSRFProviderConfig.METHODS_TO_IGNORE:
                    let methodList: string[] = value.split(',');
                    for (let method of methodList) {
                        isValid = isValid && ValidationUtils.isValidHTTPMethod(method.trim().toUpperCase());
                    }
                    break;
                default:
            }
        }
        return isValid;
    }
}

