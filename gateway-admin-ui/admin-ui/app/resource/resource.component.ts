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
import {ResourceTypesService} from '../resourcetypes/resourcetypes.service';
import {ResourceService} from './resource.service';
import {Resource} from './resource';
import {TopologyService} from '../topology.service';
import {Topology} from '../topology';
import {ServiceDefinitionService} from '../service-definition/servicedefinition.service';
import {ServiceDefinition} from '../service-definition/servicedefinition';
import {HttpErrorResponse} from '@angular/common/http';

@Component({
    selector: 'app-resource',
    templateUrl: './resource.component.html',
    styleUrls: ['./resource.component.css']
})
export class ResourceComponent implements OnInit {
    resourceType: string;
    value: any;
    resources: Resource[];
    selectedResource: Resource;

    constructor(private resourceTypesService: ResourceTypesService,
                private resourceService: ResourceService,
                private topologyService: TopologyService,
                private serviceDefinitionService: ServiceDefinitionService) {
    }

    ngOnInit() {
        this.resourceTypesService.selectedResourceType$.subscribe(resourceType => this.setResourceType(resourceType));
    }

    setResourceType(resType: string) {
        // Clear the selected resource, so it can be removed from the list on refresh if necessary
        this.selectedResource = null;

        this.resourceType = resType;
        this.resourceService.selectedResourceType(this.resourceType);
        this.resourceService.getResources(resType)
            .then(resources => {
                this.resources = resources;

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

        // If it's a topology resource, notify the topology service
        if (this.resourceType === 'Topologies') {
            let topology = new Topology();
            topology.name = resource.name;
            topology.href = resource.href;
            this.topologyService.selectedTopology(topology);
        } else if (this.resourceType === 'Service Definitions') {
           let serviceDefinition = new ServiceDefinition();
           serviceDefinition.name = resource.name;
           serviceDefinition.service = resource.service['name'];
           serviceDefinition.role = resource.service['role'];
           serviceDefinition.version = resource.service['version'];
           this.serviceDefinitionService.selectedServiceDefinition(serviceDefinition);
        } else {
            // Otherwise, notify the resource service
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
