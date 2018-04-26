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

import {ProviderConfig} from "../resource-detail/provider-config";

export abstract class DisplayBindingProviderConfig extends ProviderConfig {

  protected providerType: string;

  setType(type: string) {
    this.providerType = type;
  }

  getType(): string {
    return this.providerType;
  }

  getName(): string {
    return this.name;
  }

  getRole(): string {
    return this.role;
  }

  isEnabled() {
    return this.enabled;
  }

  setParam(name: string, value: string) {
    this.params[name] = value;
  }

  removeParam(name: string): string {
    let value = this.getParam(name);
    delete this.params[name];
    return value;
  }

  getParamNames(): string[] {
    return Object.getOwnPropertyNames(this.params);
  }

  getParam(name: string): string {
    return this.params[name];
  }

  isPasswordParam(name: string): boolean {
    return false;
  }

  isValidParamValue(paramName: string) {
    return true;
  }

  isValid(): boolean {
    let isValid: boolean = true;

    for (let param of this.getDisplayPropertyNames()) {
      if (isValid) { // quit if invalid param is discovered
        isValid = isValid && this.isValidParamValue(param);
      }
    }

    return isValid;
  }

  abstract getDisplayPropertyNames(): string[];

  abstract getDisplayNamePropertyBinding(name: string): string;

}