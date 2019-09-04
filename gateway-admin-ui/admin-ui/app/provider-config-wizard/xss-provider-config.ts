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

export class XSSProviderConfig extends WebAppSecurityContributor {
    public static X_XSS_PROTECTION = 'X-XSS-Protection';

    private static SUPPORTED_VALUES: string[] = ['0', '1', '1; mode=block'];

    private static displayPropertyNames = [XSSProviderConfig.X_XSS_PROTECTION];

    private static displayPropertyNameBindings: Map<string, string> = new Map(
       [ [XSSProviderConfig.X_XSS_PROTECTION, 'xss.protection'] ] as [string, string][]
    );

    constructor() {
        super();
        // Set default values
        this.setParam('xss.protection.enabled', 'true');
        this.setParam(XSSProviderConfig.displayPropertyNameBindings.get(XSSProviderConfig.X_XSS_PROTECTION), '1; mode=block');
    }

    getDisplayPropertyNames(): string[] {
        return XSSProviderConfig.displayPropertyNames;
    }

    getDisplayNamePropertyBinding(name: string): string {
        return XSSProviderConfig.displayPropertyNameBindings.get(name);
    }

    isValidParamValue(paramName: string): boolean {
        let isValid = false;
        let value = this.getParam(this.getDisplayNamePropertyBinding(paramName));
        if (value) {
            switch (paramName) {
                case XSSProviderConfig.X_XSS_PROTECTION:
                    if (XSSProviderConfig.SUPPORTED_VALUES.includes(value)) {
                        isValid = true;
                    } else {
                        // only supported in Chromium
                        isValid = value.startsWith('1; report=');
                    }
                    break;
                default:
                    break;
            }
        }

        return isValid;
    }
}
