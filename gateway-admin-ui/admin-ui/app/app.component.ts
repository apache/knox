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
import {Component} from '@angular/core';
import {TopologyService} from './topology.service';
import {ServiceDefinitionService} from './service-definition/servicedefinition.service';
import {ResourceTypesService} from './resourcetypes/resourcetypes.service';

@Component({
    selector: 'app-resource-management',
    template: `
        <div class="container-fluid">
            <div class="row">
                <div class="col-md-2 col-lg-2">
                    <app-resourcetypes></app-resourcetypes>
                </div>
                <div class="col-md-3 col-lg-3">
                    <app-resource></app-resource>
                </div>
                <div class="col-md-7 col-lg-7">
                    <app-resource-detail></app-resource-detail>
                </div>
            </div>
        </div>
    `,
    providers: [TopologyService, ServiceDefinitionService, ResourceTypesService]
})

export class AppComponent {
    constructor(private topologyService: TopologyService,
                private serviceDefinitionService: ServiceDefinitionService,
                private resourcetypesService: ResourceTypesService) {
    }
}
