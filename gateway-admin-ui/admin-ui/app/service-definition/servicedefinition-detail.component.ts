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
import {Component, OnInit, ViewChild} from '@angular/core';
import {BsModalComponent} from 'ng2-bs3-modal/ng2-bs3-modal';
import {ServiceDefinition} from './servicedefinition';
import {ServiceDefinitionService} from './servicedefinition.service';
import {ResourceTypesService} from '../resourcetypes/resourcetypes.service';

import 'brace/theme/monokai';
import 'brace/mode/xml';

@Component({
    selector: 'app-servicedefinition-detail',
    template: `
        <div class="panel panel-default">
            <div class="panel-heading">
                <h4 class="panel-title">{{title}} <span class="pull-right">{{titleSuffix}}</span></h4>
            </div>
            <div *ngIf="serviceDefinitionContent" class="panel-body">
                <ace-editor
                        [(text)]="serviceDefinitionContent"
                        [mode]="'xml'"
                        [options]="options"
                        [theme]="theme"
                        style="min-height: 430px; width:100%; overflow: auto;"
                        (textChanged)="onChange($event)">
                </ace-editor>
		        <div class="panel-footer">
		            <button id="deleteServiceDefinition" (click)="deleteServiceDefConfirmModal.open('sm')"
		                    class="btn btn-default btn-sm" type="submit">
		                <span class="glyphicon glyphicon-trash" aria-hidden="true"></span>
		            </button>
		            <button id="updateServiceDefinition" (click)="updateServiceDefConfirmModal.open('sm')"
		                class="btn btn-default btn-sm pull-right" type="submit">
		                <span class="glyphicon glyphicon-floppy-disk" aria-hidden="true"></span>
		            </button>
		        </div>
            </div>
        </div>
        <bs-modal (onClose)="updateServiceDefinition()" #updateServiceDefConfirmModal>
            <bs-modal-header [showDismiss]="true">
                <h4 class="modal-title">Updating Service Definition {{titleSuffix}}</h4>
            </bs-modal-header>
            <bs-modal-body>
                Are you sure you want to update this service definition?
            </bs-modal-body>
            <bs-modal-footer>
                <button type="button" class="btn btn-default btn-sm" data-dismiss="updateServiceDefConfirmModal"
                        (click)="updateServiceDefConfirmModal.dismiss()">Cancel
                </button>
                <button type="button" class="btn btn-primary btn-sm" (click)="updateServiceDefConfirmModal.close()">Ok</button>
            </bs-modal-footer>
        </bs-modal>
        <bs-modal (onClose)="deleteServiceDefinition()" #deleteServiceDefConfirmModal>
            <bs-modal-header [showDismiss]="true">
                <h4 class="modal-title">Deleting Service Definition {{titleSuffix}}</h4>
            </bs-modal-header>
            <bs-modal-body>
                Are you sure you want to delete this service definition?
            </bs-modal-body>
            <bs-modal-footer>
                <button type="button" class="btn btn-default btn-sm" data-dismiss="deleteServiceDefConfirmModal"
                        (click)="deleteServiceDefConfirmModal.dismiss()">Cancel
                </button>
                <button type="button" class="btn btn-primary btn-sm" (click)="deleteServiceDefConfirmModal.close()">Ok</button>
            </bs-modal-footer>
        </bs-modal>
    `
})

export class ServiceDefinitionDetailComponent implements OnInit {
    serviceDefinition: ServiceDefinition;
    title = 'Service Definition Detail';
    titleSuffix: string;
    serviceDefinitionContent: string;
    changedServiceDefinitionContent: string;
    theme: String = 'monokai';
    options: any = {useWorker: false, printMargin: false};

    @ViewChild('editor') editor;

    @ViewChild('deleteServiceDefConfirmModal')
    deleteServiceDefConfirmModal: BsModalComponent;

    constructor(private serviceDefinitionService: ServiceDefinitionService, private resourceTypesService: ResourceTypesService) {
    }

    ngOnInit(): void {
        this.serviceDefinitionService.selectedServiceDefinition$.subscribe(value => this.populateContent(value));
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
                // This refreshes the list of service definitions
                this.resourceTypesService.selectResourceType('Service Definitions');
            });
    }

    deleteServiceDefinition() {
        this.serviceDefinitionService.deleteServiceDefinition(this.serviceDefinition)
            .then(() => {
                // This refreshes the list of service definitions
                this.resourceTypesService.selectResourceType('Service Definitions');
                // This refreshes the service definition content panel to the first one in the list
                this.serviceDefinitionService.getServiceDefinitions()
                    .then(serviceDefinitions => {
                        this.serviceDefinitionService.selectedServiceDefinition(serviceDefinitions[0]);
                    });
            });
    }
}
