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


export class HostMapProviderWizard extends CategoryWizard {

  private stepCount: number = 2;

  getTypes(): string[] {
    return [];
  }

  getSteps(): number {
    return this.stepCount;
  }

  onChange() {
    // Nothing to do
  }

  getProviderConfig(): ProviderConfig {
    this.providerConfig = new ProviderConfig();
    this.providerConfig.role = 'hostmap';
    this.providerConfig.name = 'static';
    this.providerConfig.enabled = 'true';
    this.providerConfig.params = new Map<string, string>();
    return this.providerConfig;
  }

}

