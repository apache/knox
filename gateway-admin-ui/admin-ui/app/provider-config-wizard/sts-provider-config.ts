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

import {ValidationUtils} from "../utils/validation-utils";
import {WebAppSecurityContributor} from "./webappsec-contributor";

export class STSProviderConfig extends WebAppSecurityContributor {

  public static TYPE: string = 'cors';

  public static STS: string = 'Strict-Transport-Security Header';

  private static displayPropertyNames = [ STSProviderConfig.STS ];

  private static displayPropertyNameBindings: Map<string, string> =
    new Map([ [STSProviderConfig.STS, 'strict.transport'] ] as [string, string][]);

  constructor() {
    super();
    // Set the default values
    this.setParam('strict.transport.enabled', 'true');
    this.setParam(STSProviderConfig.displayPropertyNameBindings.get(STSProviderConfig.STS), 'max-age=31536000');
  }

  getDisplayPropertyNames(): string[] {
    return STSProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string): string {
    return STSProviderConfig.displayPropertyNameBindings.get(name);
  }

  isValidParamValue(paramName: string): boolean {
    let isValid: boolean = true;

    let value = this.getParam(this.getDisplayNamePropertyBinding(paramName));
    if (value) {
      switch (paramName) {
        case STSProviderConfig.STS:
          isValid = ValidationUtils.isValidString(value);
          break;
        default:
      }
    }

    return isValid;
  }


}
