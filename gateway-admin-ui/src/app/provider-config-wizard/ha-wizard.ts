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

import {CategoryWizard} from "./category-wizard";
import {ProviderConfig} from "../resource-detail/provider-config";
import {ProviderContributorWizard} from "./provider-contributor-wizard";
import {HaProviderConfig} from "./ha-provider-config";
import {DisplayBindingProviderConfig} from "./display-binding-provider-config";

export class HaWizard extends CategoryWizard implements ProviderContributorWizard {

  private static DEFAULT_TYPE: string = 'Add service';

  private stepCount: number = 4;

  getTypes(): string[] {
    return [HaWizard.DEFAULT_TYPE];
  }

  getSteps(): number {
    return this.stepCount;
  }

  onChange() {
    this.providerConfig = this.createNewProviderConfig();
  }

  getProviderConfig(): ProviderConfig {
    return (this.providerConfig as HaProviderConfig);
  }

  getProviderRole(): string {
    return this.providerConfig.role;
  }

  createNewProviderConfig(): ProviderConfig {
    return new HaProviderConfig();
  }

  contribute(target: ProviderConfig) {
    let svcNameProperty =
      (this.providerConfig as DisplayBindingProviderConfig).getDisplayNamePropertyBinding(HaProviderConfig.SERVICE_NAME);
    let serviceName = (this.providerConfig as DisplayBindingProviderConfig).getParam(svcNameProperty);

    let paramValue: string = "enabled=true";

    for (let propertyName in this.providerConfig.params) {
      if (propertyName !== svcNameProperty) {
        let value = (this.providerConfig as DisplayBindingProviderConfig).getParam(propertyName);
        if (value && value.trim().length > 0) {
          paramValue += ';' + propertyName + '=' + value;
        }
      }
    }

    (target as DisplayBindingProviderConfig).setParam(serviceName, paramValue);
  }

}