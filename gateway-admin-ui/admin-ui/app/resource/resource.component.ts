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
import { Component, OnInit } from '@angular/core';
import { ResourceTypesService } from '../resourcetypes/resourcetypes.service';
import { ResourceService } from './resource.service';
import { Resource } from './resource';
import {TopologyService} from "../topology.service";
import {Topology} from "../topology";
import {HttpErrorResponse} from "@angular/common/http";


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
              private topologyService: TopologyService) { }


  ngOnInit() {
    this.resourceTypesService.selectedResourceType$.subscribe(resourceType => this.setResourceType(resourceType));
  }


  setResourceType(resType: string) {
    //console.debug('ResourceComponent--> setResourceType --> ' + resType);

    // Clear the selected resource, so it can be removed from the list on refresh if necessary
    this.selectedResource = null;

    this.resourceType = resType;
    this.resourceService.selectedResourceType(this.resourceType);
    this.resources = []; // Clear the table before loading the new resources
    this.resourceService.getResources(resType)
      .then(resources => {
        this.resources = resources;

        let debugMsg = 'ResourceComponent --> Found ' + resources.length + ' ' + resType + ' resources\n';
        for (let res of resources) {
            debugMsg += '  ' + res.name + '\n';
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
    } else {
        // Otherwise, notify the resource service
        this.resourceService.selectedResource(resource);
    }
  }

  isSelectedResource(res: Resource): boolean {
      return (res && this.selectedResource) ? (res.name === this.selectedResource.name) : false;
  }

  getResourceTypeSingularDisplayName(resType: string): string {
      switch(resType) {
          case 'Topologies': {
              return 'Topology';
          }
          case 'Provider Configurations':
          case 'Descriptors': {
              return resType.substring(0, resType.length - 1);
          }
          default: {
              return 'Resource';
          }
      }
  }

}
