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
import {Component, OnInit, ViewChild, AfterViewInit} from '@angular/core';
import {DatePipe} from '@angular/common';
import {MatTableModule, MatTableDataSource} from '@angular/material/table';
import {MatPaginatorModule, MatPaginator} from '@angular/material/paginator';
import {ResourceTypesService} from '../service/resourcetypes.service';
import {ResourceService} from '../service/resource.service';
import {Resource} from '../model/resource';
import {TopologyService} from '../service/topology.service';
import {Topology} from '../model/topology';
import {ServiceDefinitionService} from '../service/servicedefinition.service';
import {ServiceDefinition} from '../model/servicedefinition';
import {NewDescWizardComponent} from '../new-desc-wizard/new-desc-wizard.component';
import {ProviderConfigWizardComponent} from '../provider-config-wizard/provider-config-wizard.component';
import {NewServiceDefinitionComponent} from '../service-definition/new-service-definition.component';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'app-resource',
    templateUrl: './resource.component.html',
    styleUrls: ['./resource.component.css'],
    imports: [DatePipe, MatTableModule, MatPaginatorModule,
        NewDescWizardComponent, ProviderConfigWizardComponent, NewServiceDefinitionComponent]
})
export class ResourceComponent implements OnInit, AfterViewInit {
    resourceType: string;
    selectedResource: Resource;
    dataSource = new MatTableDataSource<Resource>([]);

    @ViewChild(MatPaginator) paginator: MatPaginator;

    constructor(private resourceTypesService: ResourceTypesService,
                private resourceService: ResourceService,
                private topologyService: TopologyService,
                private serviceDefinitionService: ServiceDefinitionService) {
    }

    ngOnInit() {
        this.resourceTypesService.selectedResourceType$.subscribe(resourceType => this.setResourceType(resourceType));
    }

    ngAfterViewInit() {
        this.dataSource.paginator = this.paginator;
    }

    getDisplayedColumns(): string[] {
        if (this.resourceType === 'Topologies') {
            return ['name', 'timestamp'];
        }
        return ['name'];
    }

    setResourceType(resType: string) {
        this.selectedResource = null;

        this.resourceType = resType;
        this.resourceService.selectedResourceType(this.resourceType);
        this.resourceService.getResources(resType)
            .then(resources => {
                this.dataSource.data = resources;

                let debugMsg = 'ResourceComponent --> Found ' + resources.length + ' ' + resType + ' resources\n';
                for (let res of resources) {
                    if (res.service) {
                        debugMsg += '  ' + res.service['role'] + ' (' + res.service['version'] + ')' + '\n';
                    } else {
                        debugMsg += '  ' + res.name + '\n';
                    }
                }
                console.debug(debugMsg);
            })
            .catch((err: HttpErrorResponse) => {
                console.debug('Error accessing ' + resType + ' : ' + err.message);
            });
    }

    onSelect(resource: Resource) {
        this.selectedResource = resource;

        if (this.resourceType === 'Topologies') {
            let topology = new Topology();
            topology.name = resource.name;
            topology.href = resource.href;
            this.topologyService.selectedTopology(topology);
        } else if (this.resourceType === 'Service Definitions') {
           let serviceDefinition = new ServiceDefinition();
           serviceDefinition.service = resource.service['name'];
           serviceDefinition.role = resource.service['role'];
           serviceDefinition.version = resource.service['version'];
           this.serviceDefinitionService.selectedServiceDefinition(serviceDefinition);
        } else {
            this.resourceService.selectedResource(resource);
        }
    }

    isSelectedResource(res: Resource): boolean {
        return (res && this.selectedResource) ? (res.service && this.selectedResource.service ? this.isSelectedServiceDefinition(res)
            : (res.name === this.selectedResource.name)) : false;
    }

    isSelectedServiceDefinition(res: Resource): boolean {
        return res.service['name'] === this.selectedResource.service['name']
            && res.service['role'] === this.selectedResource.service['role']
            && res.service['version'] === this.selectedResource.service['version'];
    }

    getResourceTypeSingularDisplayName(resType: string): string {
        switch (resType) {
            case 'Topologies': {
                return 'Topology';
            }
            case 'Provider Configurations':
            case 'Service Definitions':
            case 'Descriptors': {
                return resType.substring(0, resType.length - 1);
            }
            default: {
                return 'Resource';
            }
        }
    }
}
