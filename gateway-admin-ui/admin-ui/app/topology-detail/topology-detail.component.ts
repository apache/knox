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
import {Topology} from '../model/topology';
import {TopologyService} from '../service/topology.service';
import {ResourceTypesService} from '../service/resourcetypes.service';
import {ValidationUtils} from '../utils/validation-utils';
import {ModalComponent} from '../utils/modal.component';

@Component({
    selector: 'app-topology-detail',
    template: `
        <div class="panel panel-default" style="margin-top: 10px;">
            <div class="panel-heading">
                <h4 class="panel-title">{{title}}
                    @if (showEditOptions === false) {
                        <span style="padding-left: 15%;" class="text-danger text-center"> Read Only (generated file) </span>
                    }
                    <span class="pull-right">{{titleSuffix}}</span>
                </h4>
            </div>
            <div [style.display]="getEditorVisibility()" class="panel-body" style="padding: 0;">
                <div class="code-editor-wrapper">
                    <div class="code-editor-gutter">
                        @for (line of getLineNumbers(); track line) {
                            <div class="line-number">{{line}}</div>
                        }
                    </div>
                    <textarea
                            class="code-editor"
                            [(ngModel)]="topologyContent"
                            [readOnly]="!showEditOptions"
                            spellcheck="false"
                            (input)="onChange($event.target.value)">
                    </textarea>
                </div>
                <div class="editor-toolbar">
                    <button id="duplicateTopology" (click)="duplicateModal.open('sm')" class="btn btn-default btn-sm"
                            title="Duplicate Topology" type="submit">
                        <span class="material-icons">content_copy</span>
                    </button>
                    @if (showEditOptions) {
                        <button id="deleteTopology" (click)="deleteConfirmModal.open('sm')"
                                class="btn btn-default btn-sm" title="Delete Topology" type="submit">
                            <span class="material-icons">delete</span>
                        </button>
                    }
                    @if (showEditOptions) {
                        <button id="saveTopology" (click)="saveTopology()" class="btn btn-default btn-sm pull-right"
                                title="Save Topology" [disabled]="!changedTopology" type="submit">
                            <span class="material-icons">save</span>
                        </button>
                    }
                </div>
            </div>
            <app-modal (onClose)="createTopology()" #duplicateModal>
                <div class="modal-header">
                    <h4 class="modal-title">Create a copy</h4>
                    <button type="button" class="btn-close" (click)="duplicateModal.dismiss()"></button>
                </div>
                <div class="modal-body">
                    <div class="form-group">
                        <label for="textbox">Name the new topology</label>
                        <input autofocus type="text" class="form-control" required [(ngModel)]="newTopologyName" id="textbox">
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-default btn-sm" (click)="duplicateModal.dismiss()">Cancel</button>
                    <button type="button" class="btn btn-primary btn-sm" [disabled]="!isValidNewTopologyName()"
                            (click)="duplicateModal.close()">Ok</button>
                </div>
            </app-modal>
            <app-modal (onClose)="deleteTopology()" #deleteConfirmModal>
                <div class="modal-header">
                    <h4 class="modal-title">Deleting Topology {{titleSuffix}}</h4>
                    <button type="button" class="btn-close" (click)="deleteConfirmModal.dismiss()"></button>
                </div>
                <div class="modal-body">
                    Are you sure you want to delete the topology?
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-default btn-sm" (click)="deleteConfirmModal.dismiss()">Cancel</button>
                    <button type="button" class="btn btn-primary btn-sm" (click)="deleteConfirmModal.close()">Ok</button>
                </div>
            </app-modal>
        </div>
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

        .code-editor:read-only {
            opacity: 0.85;
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
export class TopologyDetailComponent implements OnInit {
    title = 'Topology Detail';
    titleSuffix: string;
    topology: Topology;
    topologyContent: string;
    changedTopology: string;
    newTopologyName: string;
    showEditOptions = true;

    constructor(private topologyService: TopologyService, private resourceTypesService: ResourceTypesService) {
    }

    ngOnInit(): void {
        this.topologyService.selectedTopology$.subscribe(value => this.populateContent(value));
    }

    getEditorVisibility(): string {
        return this.topologyContent ? 'block' : 'none';
    }

    getLineNumbers(): number[] {
        if (!this.topologyContent) {
            return [];
        }
        const count = this.topologyContent.split('\n').length;
        return Array.from({length: count}, (_, i) => i + 1);
    }

    setTitle(value: string) {
        this.titleSuffix = value;
    }

    onChange(code: any) {
        this.changedTopology = code;
    }

    saveTopology() {
        this.topologyService.saveTopology(this.topology.href, this.changedTopology)
            .then(() => this.topologyService.changedTopology(this.topology.name));
    }

    createTopology() {
        this.topologyService.createTopology(this.newTopologyName,
            (this.changedTopology ? this.changedTopology : this.topologyContent))
            .then(() => {
                this.topologyService.changedTopology(this.newTopologyName);
                this.resourceTypesService.selectResourceType('Topologies');
            });
    }

    deleteTopology() {
        this.topologyService.deleteTopology(this.topology.href)
            .then(() => {
                this.topologyService.changedTopology(this.topology.name);
                this.resourceTypesService.selectResourceType('Topologies');
                this.topologyService.getTopologies()
                    .then(topologies => {
                        this.topologyService.selectedTopology(topologies[0]);
                    });
            });
    }

    populateContent(topology: Topology) {
        this.topology = topology;
        this.setTitle(topology.name);
        if (this.topology) {
            if (this.topology.href) {
                this.topologyService.getTopology(this.topology.href)
                    .then(content => this.topologyContent = content)
                    .then(() => this.makeReadOnly(this.topologyContent, 'generated'));
            }
        }
    }

    isValidNewTopologyName(): boolean {
        return ValidationUtils.isValidResourceName(this.newTopologyName);
    }

    makeReadOnly(text, tag) {
        let parser = new DOMParser();
        let parsed = parser.parseFromString(text, 'text/xml');

        let tagValue = parsed.getElementsByTagName(tag);
        let result = tagValue[0].childNodes[0].nodeValue;

        this.showEditOptions = result !== 'true';
    }
}
