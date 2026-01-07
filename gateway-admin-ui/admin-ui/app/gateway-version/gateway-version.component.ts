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
import {Component, OnInit} from '@angular/core';
import {GatewayVersion} from '../model/gateway-version';
import {GatewayVersionService} from '../service/gateway-version.service';


@Component({
    selector: 'app-gateway-version',
    template: `
        @if (gatewayVersion) {
            <span class="version-item"><strong>Version</strong> {{gatewayVersion.version}}</span>
            <span class="version-divider">|</span>
            <span class="version-item"><strong>Hash</strong> {{gatewayVersion.hash}}</span>
        }
    `,
    styles: [`
        :host {
            display: inline;
        }

        .version-item {
            font-size: 12px;
            color: #888;
        }

        .version-item strong {
            color: #555;
            margin-right: 3px;
        }

        .version-divider {
            margin: 0 8px;
            color: #ccc;
        }
    `]
})

export class GatewayVersionComponent implements OnInit {
    gatewayVersion: GatewayVersion;

    constructor(private gatewayVersionService: GatewayVersionService) {
    }

    getVersion(): void {
        this.gatewayVersionService.getVersion().then(gatewayVersion => this.gatewayVersion = gatewayVersion);
    }

    ngOnInit(): void {
        this.getVersion();
    }
}
