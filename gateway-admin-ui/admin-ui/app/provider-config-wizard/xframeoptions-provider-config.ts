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

import {WebAppSecurityContributor} from './webappsec-contributor';

export class XFrameOptionsProviderConfig extends WebAppSecurityContributor {
    public static VALUE = 'X-Frame-Options Header'; // DENY, SAMEORIGIN, ALLOW-FROM

    private static SUPPORTED_VALUES: string[] = ['DENY', 'SAMEORIGIN', 'ALLOW-FROM'];

    private static displayPropertyNames = [XFrameOptionsProviderConfig.VALUE];

    private static displayPropertyNameBindings: Map<string, string> =
        new Map([[XFrameOptionsProviderConfig.VALUE, 'xframe.options']] as [string, string][]);

    constructor() {
        super();
        // Set the default values
        this.setParam('xframe.options.enabled', 'true');
        this.setParam(XFrameOptionsProviderConfig.displayPropertyNameBindings.get(XFrameOptionsProviderConfig.VALUE), 'DENY');
    }

    getDisplayPropertyNames(): string[] {
        return XFrameOptionsProviderConfig.displayPropertyNames;
    }

    getDisplayNamePropertyBinding(name: string): string {
        return XFrameOptionsProviderConfig.displayPropertyNameBindings.get(name);
    }

    isValidParamValue(paramName: string): boolean {
        let isValid = true;

        let value = this.getParam(this.getDisplayNamePropertyBinding(paramName));
        if (value) {
            switch (paramName) {
                case XFrameOptionsProviderConfig.VALUE:
                    value = value.trim().toUpperCase();
                    isValid = XFrameOptionsProviderConfig.SUPPORTED_VALUES.includes(value);
                    break;
                default:
            }
        }

        return isValid;
    }
}

