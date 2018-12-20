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

import {AuthenticationProviderConfig} from "./authentication-provider-config";

export class JWTProviderConfig extends AuthenticationProviderConfig {

  static KNOXTOKEN_AUDIENCES  = 'KnoxToken Audiences';

  private static displayPropertyNames = [ JWTProviderConfig.KNOXTOKEN_AUDIENCES ];

  private static displayPropertyNameBindings: Map<string, string> =
    new Map([ [JWTProviderConfig.KNOXTOKEN_AUDIENCES, 'knox.token.audiences'] ]);


  constructor() {
    console.debug('new JWTProviderConfig()');
    super('JWTProvider', AuthenticationProviderConfig.FEDERATION_ROLE);
  }

  getDisplayPropertyNames(): string[] {
    return JWTProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string) {
    return JWTProviderConfig.displayPropertyNameBindings.get(name);
  }

}