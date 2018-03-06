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

export class SwitchCaseAssertionProviderConfig extends IdentityAssertionProviderConfig {

  static PRINCIPAL_CASE       = 'Principal Case';
  static GROUP_PRINCIPAL_CASE = 'Group Principal Case';

  private static displayPropertyNames = [ SwitchCaseAssertionProviderConfig.PRINCIPAL_CASE,
                                          SwitchCaseAssertionProviderConfig.GROUP_PRINCIPAL_CASE
                                        ];

  private static displayPropertyNameBindings: Map<string, string> =
                                    new Map([
                                      [SwitchCaseAssertionProviderConfig.PRINCIPAL_CASE,       'principal.case'],
                                      [SwitchCaseAssertionProviderConfig.GROUP_PRINCIPAL_CASE, 'group.principal.case']
                                    ]);

  constructor() {
    super('SwitchCase');
  }

  getDisplayPropertyNames(): string[] {
    return SwitchCaseAssertionProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string) {
    return SwitchCaseAssertionProviderConfig.displayPropertyNameBindings.get(name);
  }

}