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

import {CategoryWizard} from './category-wizard';
import {ProviderConfig} from '../resource-detail/provider-config';
import {IdentityAssertionProviderConfig} from './identity-assertion-provider-config';
import {DefaultIdAssertionProviderConfig} from './default-idassertion-provider-config';
import {ConcatAssertionProviderConfig} from './concat-idassertion-provider-config';
import {SwitchCaseAssertionProviderConfig} from './switchcase-idassertion-provider-config';
import {RegexAssertionProviderConfig} from './regex-idassertion-provider-config';
import {GroupLookupAssertionProviderConfig} from './grouplookup-id-assertion-provider-config';

export class IdentityAssertionWizard extends CategoryWizard {
    private static DEFAULT = 'Default';
    private static CONCAT = 'Concatenation';
    private static SWITCHCASE = 'SwitchCase';
    private static REGEXP = 'Regular Expression';
    private static GROUP_LOOKUP = 'Hadoop Group Lookup (LDAP)';

    private static assertionTypes: string[] = [IdentityAssertionWizard.DEFAULT,
        IdentityAssertionWizard.CONCAT,
        IdentityAssertionWizard.SWITCHCASE,
        IdentityAssertionWizard.REGEXP,
        IdentityAssertionWizard.GROUP_LOOKUP
    ];

    private static typeConfigMap: Map<string, typeof IdentityAssertionProviderConfig> =
        new Map([[IdentityAssertionWizard.DEFAULT, DefaultIdAssertionProviderConfig],
            [IdentityAssertionWizard.CONCAT, ConcatAssertionProviderConfig],
            [IdentityAssertionWizard.SWITCHCASE, SwitchCaseAssertionProviderConfig],
            [IdentityAssertionWizard.REGEXP, RegexAssertionProviderConfig],
            [IdentityAssertionWizard.GROUP_LOOKUP, GroupLookupAssertionProviderConfig],
        ] as [string, typeof IdentityAssertionProviderConfig][]);

    private stepCount = 4;

    getTypes(): string[] {
        return IdentityAssertionWizard.assertionTypes;
    }

    getSteps(): number {
        return this.stepCount;
    }

    onChange() {
        let configType = IdentityAssertionWizard.typeConfigMap.get(this.selectedType);
        if (configType) {
            this.providerConfig = Object.create(configType.prototype) as IdentityAssertionProviderConfig;
            this.providerConfig = this.providerConfig.constructor.apply(this.providerConfig);
            (this.providerConfig as IdentityAssertionProviderConfig).setType(this.selectedType);
        } else {
            console.debug('IdentityAssertionWizard --> No provider configuration type mapped for ' + this.selectedType);
            this.providerConfig = null;
        }
    }

    getProviderConfig(): ProviderConfig {
        return (this.providerConfig as IdentityAssertionProviderConfig);
    }
}
