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

import {IdentityAssertionProviderConfig} from './identity-assertion-provider-config';
import {ValidationUtils} from '../utils/validation-utils';

export class RegexAssertionProviderConfig extends IdentityAssertionProviderConfig {
    private static INPUT = 'Input';
    private static OUTPUT = 'Output';
    private static LOOKUP = 'Lookup';
    private static ORIG_ON_FAIL = 'Use Original Lookup on Failure';

    private static displayPropertyNames = [RegexAssertionProviderConfig.INPUT,
        RegexAssertionProviderConfig.OUTPUT,
        RegexAssertionProviderConfig.LOOKUP,
        RegexAssertionProviderConfig.ORIG_ON_FAIL
    ];

    private static displayPropertyNameBindings: Map<string, string> =
        new Map([
            [RegexAssertionProviderConfig.INPUT, 'input'],
            [RegexAssertionProviderConfig.OUTPUT, 'output'],
            [RegexAssertionProviderConfig.LOOKUP, 'lookup'],
            [RegexAssertionProviderConfig.ORIG_ON_FAIL, 'use.original.on.lookup.failure']
        ]);

    constructor() {
        super('Regex');
    }

    getDisplayPropertyNames(): string[] {
        return RegexAssertionProviderConfig.displayPropertyNames;
    }

    getDisplayNamePropertyBinding(name: string) {
        return RegexAssertionProviderConfig.displayPropertyNameBindings.get(name);
    }

    isValidParamValue(paramName: string): boolean {
        let isValid: boolean;

        switch (paramName) {
            case RegexAssertionProviderConfig.ORIG_ON_FAIL:
                isValid = this.isValidUseOriginal();
                break;
            default:
                isValid = true;
        }

        return isValid;
    }

    private isValidUseOriginal(): boolean {
        let isValid = true;

        let useOrig = this.getParam(this.getDisplayNamePropertyBinding(RegexAssertionProviderConfig.ORIG_ON_FAIL));
        if (useOrig) {
            isValid = ValidationUtils.isValidBoolean(useOrig);
            if (!isValid) {
                console.debug(RegexAssertionProviderConfig.ORIG_ON_FAIL + ' value is not a valid boolean.');
            }
        }

        return isValid;
    }
}
