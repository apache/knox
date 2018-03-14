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

import {IdentityAssertionProviderConfig} from "./identity-assertion-provider-config";
import {ValidationUtils} from "../utils/validation-utils";

export class DefaultIdAssertionProviderConfig extends IdentityAssertionProviderConfig {

  private static PRINCIPAL_MAPPING       = 'Principal Mapping';
  private static GROUP_PRINCIPAL_MAPPING = 'Group Principal Mapping';

  private static displayPropertyNames = [ DefaultIdAssertionProviderConfig.PRINCIPAL_MAPPING,
                                          DefaultIdAssertionProviderConfig.GROUP_PRINCIPAL_MAPPING
                                        ];

  private static displayPropertyNameBindings: Map<string, string> =
    new Map([
      [DefaultIdAssertionProviderConfig.PRINCIPAL_MAPPING,       'principal.mapping'],
      [DefaultIdAssertionProviderConfig.GROUP_PRINCIPAL_MAPPING, 'group.principal.mapping']
    ]);

  constructor() {
    console.debug('new DefaultIdAssertionProviderConfig()');
    super('Default');
  }

  getDisplayPropertyNames(): string[] {
    return DefaultIdAssertionProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string) {
    return DefaultIdAssertionProviderConfig.displayPropertyNameBindings.get(name);
  }

  isValidParamValue(paramName: string): boolean {
    let isValid: boolean;

    switch (paramName) {
      case DefaultIdAssertionProviderConfig.PRINCIPAL_MAPPING:
      case DefaultIdAssertionProviderConfig.GROUP_PRINCIPAL_MAPPING:
        isValid = this.isValidPrincipalMapping(paramName);
        break;
      default:
        isValid = true;
    }

    return isValid;
  }

  private isValidPrincipalMapping(param: string) {
    let isValid: boolean = true;

    let mapping = this.getParam(this.getDisplayNamePropertyBinding(param));
    if (mapping) {
      isValid = ValidationUtils.isValidPrincipalMapping(mapping);
      if (!isValid) {
        console.debug(param + ' value is not a valid principal mapping.')
      }
    }

    return isValid;
  }

}