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

import {DisplayBindingProviderConfig} from "./display-binding-provider-config";

export class ACLsAuthznProviderConfig extends DisplayBindingProviderConfig {

  private static DEFAULT_MODE: string = 'Default Mode';
  private static DEFAULT_ACL: string  = 'Default ACL';

  private static displayPropertyNames: string[] = [ ACLsAuthznProviderConfig.DEFAULT_MODE,
                                                    ACLsAuthznProviderConfig.DEFAULT_ACL
                                                  ];

  private static displayPropertyNameBindings: Map<string, string> =
                    new Map([ [ACLsAuthznProviderConfig.DEFAULT_ACL,  'acl'],
                              [ACLsAuthznProviderConfig.DEFAULT_MODE, 'acl.mode']
                            ]);

  constructor() {
    super();
    this.role    = 'authorization';
    this.name    = 'AclsAuthz';
    this.enabled = 'true';
    this.params  = new Map<string, string>();
  }

  getDisplayPropertyNames(): string[] {
    return ACLsAuthznProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string) {
    return ACLsAuthznProviderConfig.displayPropertyNameBindings.get(name);
  }

}