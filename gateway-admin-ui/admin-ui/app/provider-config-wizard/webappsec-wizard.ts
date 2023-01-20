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
import {ProviderContributorWizard} from './provider-contributor-wizard';
import {DisplayBindingProviderConfig} from './display-binding-provider-config';
import {WebAppSecurityProviderConfig} from './webappsec-provider-config';
import {CSRFProviderConfig} from './csrf-provider-config';
import {CORSProviderConfig} from './cors-provider-config';
import {WebAppSecurityContributor} from './webappsec-contributor';
import {STSProviderConfig} from './sts-provider-config';
import {XFrameOptionsProviderConfig} from './xframeoptions-provider-config';
import {XContentTypeOptionsProviderConfig} from './xcontent-type-options-provider-config';
import {XSSProviderConfig} from './xss-provider-config';

export class WebAppSecurityWizard extends CategoryWizard implements ProviderContributorWizard {
    // WebAppSec provider types
    private static CSRF = 'Cross-Site Request Forgery';
    private static CORS = 'Cross-Origin Resource Sharing';
    private static XFRAME = 'X-Frame-Options';
    private static XCONTENT_TYPE = 'X-Content-Type-Options';
    private static STS = 'Strict Transport Security';
    private static XSS = 'X-XSS-Protection';

    private static webAppSecTypes: string[] = [WebAppSecurityWizard.CSRF,
        WebAppSecurityWizard.CORS,
        WebAppSecurityWizard.XFRAME,
        WebAppSecurityWizard.XCONTENT_TYPE,
        WebAppSecurityWizard.STS,
        WebAppSecurityWizard.XSS
    ];

    private static typeConfigMap: Map<string, typeof WebAppSecurityContributor> =
        new Map([
            [WebAppSecurityWizard.CSRF, CSRFProviderConfig],
            [WebAppSecurityWizard.CORS, CORSProviderConfig],
            [WebAppSecurityWizard.XFRAME, XFrameOptionsProviderConfig],
            [WebAppSecurityWizard.XCONTENT_TYPE, XContentTypeOptionsProviderConfig],
            [WebAppSecurityWizard.STS, STSProviderConfig],
            [WebAppSecurityWizard.XSS, XSSProviderConfig]
        ] as [string, typeof WebAppSecurityContributor][]);

    private stepCount = 4;

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
            this.providerConfig = this.providerConfig.constructor.apply(this.providerConfig);
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
        for (const paramName of Object.keys(this.providerConfig.params)) {
            (target as DisplayBindingProviderConfig).setParam(paramName,
                (this.providerConfig as DisplayBindingProviderConfig).getParam(paramName));
        }
    }

}
