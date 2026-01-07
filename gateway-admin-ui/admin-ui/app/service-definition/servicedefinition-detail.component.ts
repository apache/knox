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
    template: `
        <div class="panel panel-default" style="margin-top: 10px;">
            <div class="panel-heading">
                <h4 class="panel-title">{{title}} <span class="pull-right">{{titleSuffix}}</span></h4>
            </div>
            @if (serviceDefinitionContent) {
                <div class="panel-body" style="padding: 0;">
                    <div class="code-editor-wrapper">
                        <div class="code-editor-gutter">
                            @for (line of getLineNumbers(); track line) {
                                <div class="line-number">{{line}}</div>
                            }
                        </div>
                        <textarea
                                class="code-editor"
                                [(ngModel)]="serviceDefinitionContent"
                                spellcheck="false"
                                (input)="onChange($event.target.value)">
                        </textarea>
                    </div>
                    <div class="editor-toolbar">
                        <button id="deleteServiceDefinition" (click)="deleteServiceDefConfirmModal.open('sm')"
                                class="btn btn-default btn-sm" title="Delete Service Definition" type="submit">
                            <span class="material-icons">delete</span>
                        </button>
                        <button id="updateServiceDefinition" (click)="updateServiceDefConfirmModal.open('sm')"
                                class="btn btn-default btn-sm pull-right" title="Save Service Definition" type="submit">
                            <span class="material-icons">save</span>
                        </button>
                    </div>
                </div>
            }
        </div>
        <app-modal (onClose)="updateServiceDefinition()" #updateServiceDefConfirmModal>
            <div class="modal-header">
                <h4 class="modal-title">Updating Service Definition {{titleSuffix}}</h4>
                <button type="button" class="btn-close" (click)="updateServiceDefConfirmModal.dismiss()"></button>
            </div>
            <div class="modal-body">
                Are you sure you want to update this service definition?
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-default btn-sm"
                        (click)="updateServiceDefConfirmModal.dismiss()">Cancel</button>
                <button type="button" class="btn btn-primary btn-sm"
                        (click)="updateServiceDefConfirmModal.close()">Ok</button>
            </div>
        </app-modal>
        <app-modal (onClose)="deleteServiceDefinition()" #deleteServiceDefConfirmModal>
            <div class="modal-header">
                <h4 class="modal-title">Deleting Service Definition {{titleSuffix}}</h4>
                <button type="button" class="btn-close" (click)="deleteServiceDefConfirmModal.dismiss()"></button>
            </div>
            <div class="modal-body">
                Are you sure you want to delete this service definition?
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-default btn-sm"
                        (click)="deleteServiceDefConfirmModal.dismiss()">Cancel</button>
                <button type="button" class="btn btn-primary btn-sm"
                        (click)="deleteServiceDefConfirmModal.close()">Ok</button>
            </div>
        </app-modal>
    `,
    styles: [`
        .code-editor-wrapper {
            display: flex;
            min-height: 450px;
            background-color: #272822;
            border-radius: 4px 4px 0 0;
            overflow: hidden;
        }

        .code-editor-gutter {
            min-width: 40px;
            padding: 10px 8px 10px 0;
            background-color: #1e1f1c;
            text-align: right;
            user-select: none;
            border-right: 1px solid #3e3d32;
        }

        .line-number {
            font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
            font-size: 12px;
            line-height: 18px;
            color: #75715e;
        }

        .code-editor {
            flex: 1;
            min-height: 450px;
            padding: 10px 12px;
            background-color: #272822;
            color: #f8f8f2;
            border: none;
            outline: none;
            resize: none;
            font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
            font-size: 12px;
            line-height: 18px;
            tab-size: 4;
            white-space: pre;
            overflow: auto;
        }

        .code-editor::selection {
            background-color: #49483e;
        }

        .editor-toolbar {
            padding: 8px 12px;
            background-color: #1e1f1c;
            border-top: 1px solid #3e3d32;
            border-radius: 0 0 4px 4px;
        }

        .editor-toolbar .btn {
            background-color: #3e3d32;
            border-color: #555;
            color: #f8f8f2;
        }

        .editor-toolbar .btn:hover:not(:disabled) {
            background-color: #555;
            border-color: #75715e;
        }

        .editor-toolbar .btn:disabled {
            opacity: 0.4;
        }

        .editor-toolbar .material-icons {
            color: #f8f8f2;
            font-size: 16px;
        }
    `],
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
