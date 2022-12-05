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
import {ACLsAuthznProviderConfig} from './acls-authzn-provider-config';
import {DisplayBindingProviderConfig} from './display-binding-provider-config';

export class AuthorizationWizard extends CategoryWizard {
    // Authorization provider types
    private static AUTHZN_ACLS = 'Access Control Lists';

    private static authznTypes: string[] = [AuthorizationWizard.AUTHZN_ACLS];

    private static typeConfigMap: Map<string, typeof DisplayBindingProviderConfig> =
        new Map([
            [AuthorizationWizard.AUTHZN_ACLS, ACLsAuthznProviderConfig]
        ] as [string, typeof DisplayBindingProviderConfig][]);

    private stepCount = 4;

    getTypes(): string[] {
        return AuthorizationWizard.authznTypes;
    }

    getSteps(): number {
        return this.stepCount;
    }

    onChange() {
        let configType = AuthorizationWizard.typeConfigMap.get(this.selectedType);
        if (configType) {
            this.providerConfig = Object.create(configType.prototype) as DisplayBindingProviderConfig;
            this.providerConfig = this.providerConfig.constructor.apply(this.providerConfig);
            (this.providerConfig as DisplayBindingProviderConfig).setType(this.selectedType);
        } else {
            console.debug('AuthorizationWizard --> No provider configuration type mapped for ' + this.selectedType);
            this.providerConfig = null;
        }
    }

    getProviderConfig(): DisplayBindingProviderConfig {
        return (this.providerConfig as DisplayBindingProviderConfig);
    }
}
