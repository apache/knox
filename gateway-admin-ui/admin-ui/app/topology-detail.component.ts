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
import {AfterViewInit, Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {Topology} from './topology';
import {TopologyService} from './topology.service';
import {ResourceTypesService} from './resourcetypes/resourcetypes.service';
import {BsModalComponent} from 'ng2-bs3-modal';
import {ValidationUtils} from './utils/validation-utils';
import * as ace from 'ace-builds';

@Component({
    selector: 'app-topology-detail',
    template: `
        <div class="panel panel-default">
            <div class="panel-heading">
                <h4 class="panel-title">{{title}} <span *ngIf="showEditOptions === false" style="padding-left: 15%;"
                                                        class="text-danger text-center"> Read Only (generated file) </span> <span
                        class="pull-right">{{titleSuffix}}</span></h4>
            </div>
            <div [style.display]="getEditorVisibility()" class="panel-body">
                <ace-editor
                        #editor
                        [(text)]="topologyContent"
                        [options]="options"
                        [theme]="theme"
                        [readOnly]="!showEditOptions"
                        style="min-height: 430px; width:100%; overflow: auto;"
                        (textChanged)="onChange($event)">
                </ace-editor>
                <div class="panel-footer">
                    <button id="duplicateTopology" (click)="duplicateModal.open('sm')" class="btn btn-default btn-sm" type="submit">
                        <span class="glyphicon glyphicon-duplicate" aria-hidden="true"></span>
                    </button>
                    <button id="deleteTopology" *ngIf="showEditOptions" (click)="deleteConfirmModal.open('sm')"
                            class="btn btn-default btn-sm" type="submit">
                        <span class="glyphicon glyphicon-trash" aria-hidden="true"></span>
                    </button>
                    <button id="saveTopology" *ngIf="showEditOptions" (click)="saveTopology()" class="btn btn-default btn-sm pull-right"
                            [disabled]="!changedTopology" type="submit">
                        <span class="glyphicon glyphicon-floppy-disk" aria-hidden="true"></span>
                    </button>
                </div>
            </div>
            <bs-modal (onClose)="createTopology()" #duplicateModal>
                <bs-modal-header [showDismiss]="true">
                    <h4 class="modal-title">Create a copy</h4>
                </bs-modal-header>
                <bs-modal-body>
                    <div class="form-group">
                        <label for="textbox">Name the new topology</label>
                        <input autofocus type="text" class="form-control" required [(ngModel)]="newTopologyName" id="textbox">
                    </div>
                </bs-modal-body>
                <bs-modal-footer>
                    <button type="button" class="btn btn-default btn-sm" data-dismiss="duplicateModal" (click)="duplicateModal.dismiss()">
                        Cancel
                    </button>
                    <button type="button" class="btn btn-primary btn-sm" [disabled]="!isValidNewTopologyName()"
                            (click)="duplicateModal.close()">Ok
                    </button>
                </bs-modal-footer>
            </bs-modal>
            <bs-modal (onClose)="deleteTopology()" #deleteConfirmModal>
                <bs-modal-header [showDismiss]="true">
                    <h4 class="modal-title">Deleting Topology {{titleSuffix}}</h4>
                </bs-modal-header>
                <bs-modal-body>
                    Are you sure you want to delete the topology?
                </bs-modal-body>
                <bs-modal-footer>
                    <button type="button" class="btn btn-default btn-sm" data-dismiss="deleteConfirmModal"
                            (click)="deleteConfirmModal.dismiss()">Cancel
                    </button>
                    <button type="button" class="btn btn-primary btn-sm" (click)="deleteConfirmModal.close()">Ok</button>
                </bs-modal-footer>
            </bs-modal>
        </div>
    `
})
export class TopologyDetailComponent implements OnInit, AfterViewInit {
    title = 'Topology Detail';
    titleSuffix: string;
    topology: Topology;
    topologyContent: string;
    changedTopology: string;
    newTopologyName: string;
    showEditOptions = true;
    theme: String = 'monokai';
    options: any = {useWorker: false, printMargin: false};

    @ViewChild('duplicateModal')
    duplicateModal: BsModalComponent;

    @ViewChild('deleteConfirmModal')
    deleteConfirmModal: BsModalComponent;

    @ViewChild('editor')
    editor: ElementRef<HTMLElement>;

    constructor(private topologyService: TopologyService, private resourceTypesService: ResourceTypesService) {
    }

    ngOnInit(): void {
        this.topologyService.selectedTopology$.subscribe(value => this.populateContent(value));
    }

    ngAfterViewInit(): void {
        ace.config.set(
            'basePath',
            'https://unpkg.com/ace-builds@1.4.12/src-noconflict'
        );
        const aceEditor = ace.edit(this.editor.nativeElement);
        aceEditor.session.setMode('xml');
    }

    getEditorVisibility(): string {
        return this.topologyContent ? 'block' : 'none';
    }

    setTitle(value: string) {
        this.titleSuffix = value;
    }

    onChange(code: any) {
        this.changedTopology = code;
    }

    saveTopology() {
        this.topologyService.saveTopology(this.topology.href, this.changedTopology)
            .then(value => this.topologyService.changedTopology(this.topology.name));
    }

    createTopology() {
        this.topologyService.createTopology(this.newTopologyName,
            (this.changedTopology ? this.changedTopology : this.topologyContent))
            .then(() => {
                this.topologyService.changedTopology(this.newTopologyName);
                // This refreshes the list of topologies
                this.resourceTypesService.selectResourceType('Topologies');
            });
    }

    deleteTopology() {
        this.topologyService.deleteTopology(this.topology.href)
            .then(() => {
                this.topologyService.changedTopology(this.topology.name);
                // This refreshes the list of topologies
                this.resourceTypesService.selectResourceType('Topologies');
                // This refreshes the topology content panel to the first one in the list
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

    /*
    * Parse the XML and depending on the
    * provided tag value make the editor read only
    */
    makeReadOnly(text, tag) {
        let parser = new DOMParser();
        let parsed = parser.parseFromString(text, 'text/xml');

        let tagValue = parsed.getElementsByTagName(tag);
        let result = tagValue[0].childNodes[0].nodeValue;

        if (result === 'true') {
            this.showEditOptions = false;
            this.options = {readOnly: true, useWorker: false, printMargin: false, highlightActiveLine: false, highlightGutterLine: false};
        } else {
            this.showEditOptions = true;
            this.options = {readOnly: false, useWorker: false, printMargin: false};
        }

    }


}
