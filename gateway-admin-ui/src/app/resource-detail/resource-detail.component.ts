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
import { Component, OnInit, ViewChild } from '@angular/core';
import { ResourceService } from '../resource/resource.service';
import { Resource } from '../resource/resource';
import { ProviderConfig } from './provider-config';
import { Descriptor } from "./descriptor";
import { Service } from "../resource/service";
import { parseString } from 'xml2js';

import 'brace/theme/monokai';
import 'brace/mode/xml';

import { ProviderConfigSelectorComponent } from "../provider-config-selector/provider-config-selector.component";


@Component({
  selector: 'app-resource-detail',
  templateUrl: './resource-detail.component.html',
  styleUrls: ['./resource-detail.component.css']
})
export class ResourceDetailComponent implements OnInit {

  // Static "empty" Resource used for clearing the display between resource selections
  private static emptyResource: Resource = new Resource();

  private static emptyDescriptor: Descriptor = new Descriptor();

  title: string;

  resourceType: string;
  resource: Resource;
  resourceContent: string;

  providers: Array<ProviderConfig>;

  descriptor: Descriptor;

  availableProviderConfigs: Resource[];

  @ViewChild('choosePC')
  chooseProviderConfigModal: ProviderConfigSelectorComponent;

  constructor(private resourceService: ResourceService) {
  }

  ngOnInit() {
      this.resourceService.getResources('Provider Configurations').then(pcs => {
          this.availableProviderConfigs = pcs;
      });

      this.resourceService.selectedResourceType$.subscribe(type => this.setResourceType(type));
      this.resourceService.selectedResource$.subscribe(value => this.setResource(value));
  }

  get self() {
      return this;
  }

  setResourceType(resType: string) {
      if (resType !== this.resourceType) {

        if (resType === 'Descriptors') {
          // Update the available provider configurations if we're dealing with descriptors
          this.resourceService.getResources("Provider Configurations").then(result => this.availableProviderConfigs = result);
        }

        // Clear the current resource details
        if (this.resource) {this.resource.name = '';} // This clears the details title when the type context changes
        this.resource = ResourceDetailComponent.emptyResource;
        this.providers = null;
        this.descriptor = ResourceDetailComponent.emptyDescriptor;
        this.resourceContent = ''; // Clear the content area
        this.resourceType = resType;
      }
  }

  setResource(res: Resource) {
      this.resource = res;
      this.providers = [];
      this.resourceService.getResource(this.resourceType, res).then(content => this.setResourceContent(res, content));
  }

  setResourceContent(res: Resource, content: string) {
      switch(this.resourceType) {
          case 'Provider Configurations': {
              this.setProviderConfigContent(res, content);
              break;
          }
          case 'Descriptors': {
              this.setDescriptorContent(res, content);
              break;
          }
      }
  }

  setProviderConfigContent(res: Resource, content: string) {
      this.resourceContent = content;
      if (this.resourceContent) {
          try {
            let contentObj;
            if (res.name.endsWith('json')) {
                // Parse the JSON representation
                contentObj = JSON.parse(this.resourceContent);
                this.providers = contentObj['providers'];
            } else if (res.name.endsWith('yaml') || res.name.endsWith('yml')) {
                // Parse the YAML representation
                let yaml = require('js-yaml');
                contentObj = yaml.load(this.resourceContent);
                this.providers = contentObj['providers'];
            } else if (res.name.endsWith('xml')) {
                // Parse the XML representation
                parseString(this.resourceContent, (err, result) => {
                    let tempProviders = new Array<ProviderConfig>();
                    result['gateway'].provider.forEach(entry => {
                       let providerConfig: ProviderConfig = entry;
                       let params = {};
                       entry.param.forEach(param => {
                           params[param.name] = param.value;
                       });
                       providerConfig.params = params;
                       tempProviders.push(providerConfig);
                    });
                    this.providers = tempProviders;
                });
            }
          } catch (e) {
            console.error('Error parsing ' + res.name + ' content: ' + e);
          }
      }
  }

  setDescriptorContent(res: Resource, content: string) {
    this.resourceContent = content;
    if (this.resourceContent) {
      try {
        console.debug('ResourceDetailComponent --> setDescriptorContent() --> Parsing descriptor ' + res.name);
        let contentObj;
        if (res.name.endsWith('json')) {
          contentObj = JSON.parse(this.resourceContent);
        } else if (res.name.endsWith('yaml') || res.name.endsWith('yml')) {
          let yaml = require('js-yaml');
          contentObj = yaml.load(this.resourceContent);
        }
        let tempDesc = new Descriptor();
        if (contentObj) {
          tempDesc.discoveryAddress = contentObj['discovery-address'];
          tempDesc.discoveryUser = contentObj['discovery-user'];
          tempDesc.discoveryPassAlias = contentObj['discovery-pwd-alias'];
          tempDesc.discoveryCluster = contentObj['cluster'];
          tempDesc.providerConfig = contentObj['provider-config-ref'];
          tempDesc.services = contentObj['services'];
        }
        this.descriptor = tempDesc;
      } catch (e) {
        console.error('Error parsing '+ res.name + ' content: ' + e);
      }
    }
  }

  getParamKeys(provider: ProviderConfig): string[] {
    let result = [];
    for(let key in provider.params){
      if (provider.params.hasOwnProperty(key)){
          result.push(key);
      }
    }
    return result;
  }


  getServiceParamKeys(service: Service): string[] {
    let result = [];
    for(let key in service.params){
      if (service.params.hasOwnProperty(key)){
        result.push(key);
      }
    }
    return result;
  }

  hasSelectedResource(): boolean {
    return Boolean(this.resource) && Boolean(this.resource.name);
  }

  getTitleSubject(): string {
      switch(this.resourceType) {
          case 'Topologies': {
              return 'Topology';
          }
          case 'Provider Configurations':
          case 'Descriptors': {
              return this.resourceType.substring(0, this.resourceType.length - 1);
          }
          default: {
              return 'Resource';
          }
      }
  }

}
