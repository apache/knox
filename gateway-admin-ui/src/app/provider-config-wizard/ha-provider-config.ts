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
import {DisplayBindingProviderConfig} from "./display-binding-provider-config";

export class HaProviderConfig extends DisplayBindingProviderConfig {

  public static TYPE: string = 'HaProvider';

  public static SERVICE_NAME          = 'Service Name';
  public static MAX_FAILOVER_ATTEMPTS = 'Failover Atttempts Limit';
  public static FAILOVER_SLEEP        = 'Failover Interval';
  public static MAX_RETRY_ATTEMPTS    = 'Retry Attempts Limit';
  public static RETRY_SLEEP           = 'Retry Interval';

  private static displayPropertyNames = [ HaProviderConfig.SERVICE_NAME,
                                          HaProviderConfig.MAX_FAILOVER_ATTEMPTS,
                                          HaProviderConfig.FAILOVER_SLEEP,
                                          HaProviderConfig.MAX_RETRY_ATTEMPTS,
                                          HaProviderConfig.RETRY_SLEEP
                                        ];

  private static displayPropertyNameBindings: Map<string, string> =
    new Map([
      [HaProviderConfig.SERVICE_NAME,          'serviceName'],
      [HaProviderConfig.MAX_FAILOVER_ATTEMPTS, 'maxFailoverAttempts'],
      [HaProviderConfig.FAILOVER_SLEEP,        'failoverSleep'],
      [HaProviderConfig.MAX_RETRY_ATTEMPTS,    'maxRetryAttempts'],
      [HaProviderConfig.RETRY_SLEEP,           'retrySleep']
    ] as [string, string][]);

  constructor() {
    super();
    this.name = HaProviderConfig.TYPE;
  }

  getDisplayPropertyNames(): string[] {
    return HaProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string): string {
    return HaProviderConfig.displayPropertyNameBindings.get(name);
  }

  getType(): string {
    return HaProviderConfig.TYPE;
  }

  isValidParamValue(paramName: string): boolean {
    let isValid: boolean = true;

    let value = this.getParam(this.getDisplayNamePropertyBinding(paramName));
    if (value) {
      switch (paramName) {
        case HaProviderConfig.SERVICE_NAME:
          isValid = ValidationUtils.isValidString(value) && !ValidationUtils.isValidNumber(value);
          break;
        default:
          isValid = ValidationUtils.isValidNumber(value);
          if (!isValid) {
            console.debug(paramName + ' value is not valid.');
          }
      }
    }

    return isValid;
  }

}

