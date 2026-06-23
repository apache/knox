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
    templateUrl: './topology-detail.component.html',
    styleUrls: ['./topology-detail.component.css'],
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
