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
import {FormsModule} from '@angular/forms';
import Swal from 'sweetalert2/dist/sweetalert2.esm.all.js';

import {ServiceDefinition} from '../model/servicedefinition';
import {ServiceDefinitionService} from '../service/servicedefinition.service';
import {ResourceTypesService} from '../service/resourcetypes.service';
import {ModalComponent} from '../utils/modal.component';

@Component({
    selector: 'app-servicedefinition-detail',
    templateUrl: './servicedefinition-detail.component.html',
    styleUrls: ['./servicedefinition-detail.component.css'],
    imports: [FormsModule, ModalComponent]
})

export class ServiceDefinitionDetailComponent implements OnInit {
    serviceDefinition: ServiceDefinition;
    title = 'Service Definition Detail';
    titleSuffix: string;
    serviceDefinitionContent: string;
    changedServiceDefinitionContent: string;

    constructor(private serviceDefinitionService: ServiceDefinitionService, private resourceTypesService: ResourceTypesService) {
    }

    ngOnInit(): void {
        this.serviceDefinitionService.selectedServiceDefinition$.subscribe(value => this.populateContent(value));
    }

    getLineNumbers(): number[] {
        if (!this.serviceDefinitionContent) {
            return [];
        }
        const count = this.serviceDefinitionContent.split('\n').length;
        return Array.from({length: count}, (_, i) => i + 1);
    }

    setTitleSuffix(value: string) {
        this.titleSuffix = value;
    }

    onChange(code: any) {
        this.changedServiceDefinitionContent = code;
    }

    populateContent(serviceDefinition: ServiceDefinition) {
        if (serviceDefinition) {
            this.serviceDefinition = serviceDefinition;
            this.setTitleSuffix(serviceDefinition.role + ' (' + serviceDefinition.version + ')');
            this.serviceDefinitionService.getServiceDefinitionXml(serviceDefinition)
                .then(content => this.serviceDefinitionContent = content);
        }
    }

    updateServiceDefinition() {
        this.serviceDefinitionService.updateServiceDefinition(this.changedServiceDefinitionContent ? this.changedServiceDefinitionContent
                                                                : this.serviceDefinitionContent)
            .then(() => {
                Swal.fire({
                    text: 'Updated successfully!',
                    confirmButtonColor: '#7cd1f9'
                });
                this.resourceTypesService.selectResourceType('Service Definitions');
                this.serviceDefinitionService.selectedServiceDefinition(null);
            });
    }

    deleteServiceDefinition() {
        this.serviceDefinitionService.deleteServiceDefinition(this.serviceDefinition)
            .then(() => {
                Swal.fire({
                    text: 'Deleted successfully!',
                    confirmButtonColor: '#7cd1f9'
                });
                this.resourceTypesService.selectResourceType('Service Definitions');
                this.serviceDefinitionService.selectedServiceDefinition(null);
            });
    }
}
