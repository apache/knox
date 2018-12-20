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

import {WebAppSecurityContributor} from "./webappsec-contributor";

export class XContentTypeOptionsProviderConfig extends WebAppSecurityContributor {

  public static VALUE: string = 'X-Content-Type-Options Header';

  private static SUPPORTED_VALUES: string[] = ['nosniff'];

  private static displayPropertyNames = [ XContentTypeOptionsProviderConfig.VALUE ];

  private static displayPropertyNameBindings: Map<string, string> =
    new Map([ [XContentTypeOptionsProviderConfig.VALUE, 'xcontent-type.options'] ] as [string, string][]);

  constructor() {
    super();
    // Set the default values
    this.setParam('xcontent-type.options.enabled', 'true');
    this.setParam(XContentTypeOptionsProviderConfig.displayPropertyNameBindings.get(XContentTypeOptionsProviderConfig.VALUE),
                  'nosniff');
  }

  getDisplayPropertyNames(): string[] {
    return XContentTypeOptionsProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string): string {
    return XContentTypeOptionsProviderConfig.displayPropertyNameBindings.get(name);
  }

  isValidParamValue(paramName: string): boolean {
    let isValid: boolean = true;

    let value = this.getParam(this.getDisplayNamePropertyBinding(paramName));
    if (value) {
      switch (paramName) {
        case XContentTypeOptionsProviderConfig.VALUE:
          value = value.trim().toLowerCase();
          isValid = XContentTypeOptionsProviderConfig.SUPPORTED_VALUES.includes(value);
          break;
        default:
      }
    }

    return isValid;
  }

}
