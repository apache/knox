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

import {ValidationUtils} from '../utils/validation-utils';
import {DisplayBindingProviderConfig} from './display-binding-provider-config';

export class HaProviderConfig extends DisplayBindingProviderConfig {
    public static TYPE = 'HaProvider';

    private static ENSEMBLE_REGEXP = new RegExp('^([a-zA-Z\\d-.]+:\\d+)(,[a-zA-Z\\d-.]+:\\d+)*$');

    public static SERVICE_NAME = 'Service Name';
    public static MAX_FAILOVER_ATTEMPTS = 'Failover Atttempts Limit';
    public static FAILOVER_SLEEP = 'Failover Interval';
    public static MAX_RETRY_ATTEMPTS = 'Retry Attempts Limit';
    public static RETRY_SLEEP = 'Retry Interval';
    public static ZK_ENSEMBLE = 'ZooKeeper Ensemble';
    public static ZK_NAMESPACE = 'ZooKeeper Namespace';

    private static displayPropertyNames = [HaProviderConfig.SERVICE_NAME,
        HaProviderConfig.MAX_FAILOVER_ATTEMPTS,
        HaProviderConfig.FAILOVER_SLEEP,
        HaProviderConfig.MAX_RETRY_ATTEMPTS,
        HaProviderConfig.RETRY_SLEEP,
        HaProviderConfig.ZK_ENSEMBLE,
        HaProviderConfig.ZK_NAMESPACE
    ];

    private static displayPropertyNameBindings: Map<string, string> =
        new Map([
            [HaProviderConfig.SERVICE_NAME, 'serviceName'],
            [HaProviderConfig.MAX_FAILOVER_ATTEMPTS, 'maxFailoverAttempts'],
            [HaProviderConfig.FAILOVER_SLEEP, 'failoverSleep'],
            [HaProviderConfig.ZK_ENSEMBLE, 'zookeeperEnsemble'],
            [HaProviderConfig.ZK_NAMESPACE, 'zookeeperNamespace']
        ] as [string, string][]);

    constructor() {
        super();
        this.setType(HaProviderConfig.TYPE);
        this.enabled = 'true';
        this.name = HaProviderConfig.TYPE;
        this.role = 'ha';
        this.params = new Map<string, string>();
    }

    getDisplayPropertyNames(): string[] {
        return HaProviderConfig.displayPropertyNames;
    }

    getDisplayNamePropertyBinding(name: string): string {
        return HaProviderConfig.displayPropertyNameBindings.get(name);
    }

    isValidParamValue(paramName: string): boolean {
        let isValid = true;

        let value = this.getParam(this.getDisplayNamePropertyBinding(paramName));

        switch (paramName) {
            case HaProviderConfig.SERVICE_NAME:
                isValid = ValidationUtils.isValidString(value) && !ValidationUtils.isValidNumber(value);
                break;
            case HaProviderConfig.ZK_ENSEMBLE:
                if (value) {
                    isValid = this.isValidZooKeeperEnsemble(value);
                }
                break;
            case HaProviderConfig.ZK_NAMESPACE:
                if (value) {
                    isValid = ValidationUtils.isValidString(value);
                }
                break;
            default:
                if (value) {
                    isValid = ValidationUtils.isValidNumber(value);
                }
        }

        if (!isValid) {
            console.debug(paramName + ' (' + this.getDisplayNamePropertyBinding(paramName) + ') value is NOT valid: ' + value);
        }

        return isValid;
    }

    private isValidZooKeeperEnsemble(value: string): boolean {
        let isValid: boolean = HaProviderConfig.ENSEMBLE_REGEXP.test(value);
        if (isValid) {
            // Check each hostname for validity
            let addresses: string[] = value.split(';');
            for (let address of addresses) {
                if (isValid) {
                    let hostport: string[] = address.split(':');
                    isValid = isValid && ValidationUtils.isValidHostName(hostport[0]);
                } else {
                    break;
                }
            }
        }
        return isValid;
    }

}

