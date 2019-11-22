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
import {ResourceService} from '../resource/resource.service';
import {Resource} from '../resource/resource';
import {ProviderConfig} from './provider-config';
import {Descriptor} from './descriptor';
import {Service} from '../resource/service';
import {parseString} from 'xml2js';

import 'brace/theme/monokai';
import 'brace/mode/xml';

import {ProviderConfigSelectorComponent} from '../provider-config-selector/provider-config-selector.component';
import {ResourceTypesService} from '../resourcetypes/resourcetypes.service';
import {HttpErrorResponse} from '@angular/common/http';

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
    changedProviders: Array<ProviderConfig>;

    descriptor: Descriptor;

    @ViewChild('choosePC')
    chooseProviderConfigModal: ProviderConfigSelectorComponent;

    referencedProviderConfigError = false;

    constructor(private resourceService: ResourceService, private resourceTypesService: ResourceTypesService) {
    }

    ngOnInit() {
        this.resourceService.selectedResourceType$.subscribe(type => this.setResourceType(type));
        this.resourceService.selectedResource$.subscribe(value => this.setResource(value));
    }

    setResourceType(resType: string) {
        // Clear the current resource details
        if (this.resource) {
            this.resource.name = '';
        } // This clears the details title when the type context changes
        this.resource = ResourceDetailComponent.emptyResource;
        this.providers = null;
        this.descriptor = ResourceDetailComponent.emptyDescriptor;
        this.resourceContent = ''; // Clear the content area
        this.resourceType = resType;
    }

    setResource(res: Resource) {
        this.referencedProviderConfigError = false;
        this.resource = res;
        this.providers = [];
        this.changedProviders = null;
        this.descriptor = ResourceDetailComponent.emptyDescriptor;
        if (res) {
            this.resourceService.getResource(this.resourceType, res)
                .then(content => this.setResourceContent(res, content))
                .catch((error: HttpErrorResponse) => {
                    console.debug('Error accessing content for ' + res.name + ' : ' + error);
                });
        }
    }

    setResourceContent(res: Resource, content: string) {
        switch (this.resourceType) {
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
                    contentObj = yaml.safeLoad(this.resourceContent);
                    this.providers = contentObj['providers'];
                } else if (res.name.endsWith('xml')) {
                    // Parse the XML representation
                    parseString(this.resourceContent,
                        (error, result) => {
                            if (error) {
                                console.log('Error parsing ' + res.name + ' error: ' + error);
                            } else {
                                // Parsing the XML is a bit less straight-forward
                                let tempProviders = new Array<ProviderConfig>();
                                result['gateway'].provider.forEach(entry => {
                                    let providerConfig: ProviderConfig = new ProviderConfig();
                                    providerConfig.role = entry.role[0];
                                    providerConfig.name = entry.name[0];
                                    providerConfig.enabled = entry.enabled[0];

                                    // There may not be params
                                    if (entry.param) {
                                        let params = new Map<string, string>();
                                        for (let i = 0; i < entry.param.length; i++) {
                                            let param = entry.param[i];
                                            params[param.name[0]] = param.value[0];
                                        }
                                        providerConfig.params = params;
                                    }
                                    tempProviders.push(providerConfig);
                                });
                                this.providers = tempProviders;
                            }
                        });
                }
            } catch (e) {
                console.error('ResourceDetailComponent --> setProviderConfigContent() --> Error parsing ' + res.name + ' content: ' + e);
                this.providers = null; // Clear detail display
            }
        }
    }

    setDescriptorContent(res: Resource, content: string) {
        this.resourceContent = content;
        if (this.resourceContent) {
            try {
                let contentObj;
                if (res.name.endsWith('json')) {
                    contentObj = JSON.parse(this.resourceContent);
                } else if (res.name.endsWith('yaml') || res.name.endsWith('yml')) {
                    let yaml = require('js-yaml');
                    contentObj = yaml.load(this.resourceContent);
                }
                let tempDesc = new Descriptor();
                if (contentObj) {
                    tempDesc.discoveryType = contentObj['discovery-type'];
                    tempDesc.discoveryAddress = contentObj['discovery-address'];
                    tempDesc.discoveryUser = contentObj['discovery-user'];
                    tempDesc.discoveryPassAlias = contentObj['discovery-pwd-alias'];
                    tempDesc.discoveryCluster = contentObj['cluster'];
                    tempDesc.providerConfig = contentObj['provider-config-ref'];
                    tempDesc.services = contentObj['services'];
                }
                this.descriptor = tempDesc;
            } catch (e) {
                console.error('ResourceDetailComponent.setDescriptorContent: Error parsing ' + res.name + ' content: ' + e);
            }
        }
    }

    persistChanges() {
        switch (this.resourceType) {
            case 'Provider Configurations' : {
                this.persistProviderConfiguration();
                break;
            }
            case 'Descriptors': {
                this.persistDescriptor();
            }
        }
    }

    persistProviderConfiguration() {
        let content;
        let ext = this.resource.name.split('.').pop();
        switch (ext) {
            case 'json': {
                content = this.resourceService.serializeProviderConfiguration(this.providers, 'json');
                break;
            }
            case 'yaml':
            case 'yml': {
                content = this.resourceService.serializeProviderConfiguration(this.providers, 'yaml');
                break;
            }
            case 'xml': {
                // We're not going to bother serializing XML. Rather, delete the original XML resource, and replace it
                // with JSON
                console.debug('Replacing XML provider configuration ' + this.resource.name + ' with JSON...');

                // Generate the JSON representation of the updated provider configuration
                content = this.resourceService.serializeProviderConfiguration(this.providers, 'json');

                let replacementResource = new Resource();
                replacementResource.name = this.resource.name.slice(0, -4) + '.json';
                replacementResource.href = this.resource.href;

                // Delete the XML resource
                this.resourceService.deleteResource(this.resource.href + '?force=true')
                    .then(() => {
                        // Save the updated content
                        this.resourceService.saveResource(replacementResource, content).then(() => {
                            // Update the list of provider configuration to ensure that the XML one is replaced with the JSON one
                            this.resourceTypesService.selectResourceType(this.resourceType);
                            // Update the detail view
                            this.resourceService.selectedResource(replacementResource);
                        })
                            .catch(err => {
                                console.error('Error persisting ' + replacementResource.name + ' : ' + err);
                            });
                    });
                break;
            }
        }

        // For the non-XML provider configuration cases, simply save the changes
        if (ext !== 'xml') {
            // Save the updated content
            this.resourceService.saveResource(this.resource, content)
                .then(() => {
                    // Refresh the presentation
                    this.resourceService.selectedResource(this.resource);
                })
                .catch(err => {
                    console.error('Error persisting ' + this.resource.name + ' : ' + err);
                });
        }
    }

    persistDescriptor() {
        let content;
        let ext = this.resource.name.split('.').pop();
        switch (ext) {
            case 'json': {
                content = this.resourceService.serializeDescriptor(this.descriptor, 'json');
                break;
            }
            case 'yaml':
            case 'yml': {
                content = this.resourceService.serializeDescriptor(this.descriptor, 'yaml');
                break;
            }
        }

        // Save the updated content
        this.resourceService.saveResource(this.resource, content)
            .then(() => {
                // Refresh the presentation
                this.resourceService.selectedResource(this.resource);
            })
            .catch(err => {
                console.error('Error persisting ' + this.resource.name + ' : ' + err);
            });
    }

    discardChanges() {
        this.resourceService.selectedResource(this.resource);
    }


    deleteResource() {
        let resourceName = this.resource.name;
        this.resourceService.deleteResource(this.resource.href)
            .then(() => {
                console.debug('Deleted ' + resourceName);
                // This refreshes the list of resources
                this.resourceTypesService.selectResourceType(this.resourceType);
            })
            .catch((err: HttpErrorResponse) => {
                if (err.status === 304) { // Not Modified
                    console.log(resourceName + ' cannot be deleted while there are descriptors actively referencing it.');
                    this.referencedProviderConfigError = true;
                } else {
                    console.error('Error deleting ' + resourceName + ' : ' + err.message);
                }
            });
    }

    onRemoveProvider(name: string) {
        for (let i = 0; i < this.providers.length; i++) {
            if (this.providers[i].name === name) {
                this.providers.splice(i, 1);
                break;
            }
        }
        this.changedProviders = this.providers;
    }

    onProviderEnabled(provider: ProviderConfig) {
        provider.enabled = this.isProviderEnabled(provider) ? 'false' : 'true';
        this.changedProviders = this.providers;
    }

    onRemoveProviderParam(pc: ProviderConfig, paramName: string) {
        if (pc.params.hasOwnProperty(paramName)) {
            delete pc.params[paramName];
        }
        this.changedProviders = this.providers;
    }

    onRemoveDescriptorService(serviceName: string) {
        for (let i = 0; i < this.descriptor.services.length; i++) {
            if (this.descriptor.services[i].name === serviceName) {
                this.descriptor.services.splice(i, 1);
                this.descriptor.setDirty();
                break;
            }
        }
    }

    onRemoveDescriptorServiceParam(serviceName: string, paramName: string) {
        let done = false;
        for (let i = 0; i < this.descriptor.services.length; i++) {
            if (this.descriptor.services[i].name === serviceName) {
                let service = this.descriptor.services[i];
                if (service.params.hasOwnProperty(paramName)) {
                    delete service.params[paramName];
                    this.descriptor.setDirty();
                    done = true;
                    break;
                }
            }
            if (done) { // Stop checking services if it has already been handled
                break;
            }
        }
    }

    onRemoveDescriptorServiceURL(serviceName: string, serviceUrl: string) {
        let done = false;
        for (let i = 0; i < this.descriptor.services.length; i++) {
            if (this.descriptor.services[i].name === serviceName) {
                let service = this.descriptor.services[i];
                for (let j = 0; j < service.urls.length; j++) {
                    if (service.urls[j] === serviceUrl) {
                        service.urls.splice(j, 1);
                        this.descriptor.setDirty();
                        done = true;
                        break;
                    }
                }
            }
            if (done) { // Stop checking services if it has already been handled
                break;
            }
        }
    }

    toggleShowProvider(provider: ProviderConfig) {
        this[this.resource.name + provider.name + 'Show'] = !this.isShowProvider(provider);
    }

    isShowProvider(provider: ProviderConfig): boolean {
        return this[this.resource.name + provider.name + 'Show'];
    }

    toggleShowProviderParams(provider: ProviderConfig) {
        this[this.resource.name + provider.name + 'ShowParams'] = !this.isShowProviderParams(provider);
    }

    showProviderParams(provider: ProviderConfig) {
        this[this.resource.name + provider.name + 'ShowParams'] = true;
    }

    isShowProviderParams(provider: ProviderConfig): boolean {
        return this[this.resource.name + provider.name + 'ShowParams'];
    }

    toggleShowServices() {
        this[this.resource.name + 'ShowServices'] = !this.isShowServices();
    }

    showServices() {
        this[this.resource.name + 'ShowServices'] = true;
    }

    isShowServices(): boolean {
        return this[this.resource.name + 'ShowServices'];
    }

    toggleShowServiceDiscovery() {
        this[this.resource.name + 'ShowDiscovery'] = !this.isShowServiceDiscovery();
    }

    isShowServiceDiscovery(): boolean {
        return this[this.resource.name + 'ShowDiscovery'];
    }

    toggleShowServiceParams(service: Service) {
        this[this.resource.name + service.name + 'ShowParams'] = !this.isShowServiceParams(service);
    }

    showServiceParams(service: Service) {
        this[this.resource.name + service.name + 'ShowParams'] = true;
    }

    isShowServiceParams(service: Service): boolean {
        return this[this.resource.name + service.name + 'ShowParams'];
    }

    toggleShowServiceURLs(service: Service) {
        this[this.resource.name + service.name + 'ShowURLs'] = !this.isShowServiceURLs(service);
    }

    showServiceURLs(service: Service) {
        this[this.resource.name + service.name + 'ShowURLs'] = true;
    }

    isShowServiceURLs(service: Service): boolean {
        return this[this.resource.name + service.name + 'ShowURLs'];
    }

    setProviderParamEditFlag(provider: ProviderConfig, paramName: string, value: boolean) {
        this[provider.name + paramName + 'EditMode'] = value;
        this.changedProviders = this.providers;
    }

    getProviderParamEditFlag(provider: ProviderConfig, paramName: string): boolean {
        return this[provider.name + paramName + 'EditMode'];
    }

    setServiceVersionEditFlag(service: Service, value: boolean) {
        this[service.name + 'EditMode'] = value;
        this.descriptor.setDirty();
    }

    getServiceVersionEditFlag(service: Service): boolean {
        return this[service.name + 'EditMode'];
    }

    setServiceParamEditFlag(service: Service, paramName: string, value: boolean) {
        this[service.name + paramName + 'EditMode'] = value;
        this.descriptor.setDirty();
    }

    getServiceParamEditFlag(service: Service, paramName: string): boolean {
        return this[service.name + paramName + 'EditMode'];
    }

    setServiceURLEditFlag(service: Service, index: number, value: boolean) {
        this[service.name + index + 'EditMode'] = value;
        this.descriptor.setDirty();
    }

    getServiceURLEditFlag(service: Service, index: number): boolean {
        return this[service.name + index + 'EditMode'];
    }

    isAddingServiceParam(service: Service): boolean {
        return this['addParam' + service.name];
    }

    setAddingServiceParam(service: Service, value: boolean) {
        this['addParam' + service.name] = value;
    }

    isAddingServiceURL(service: Service): boolean {
        return this['addURL' + service.name];
    }

    setAddingServiceURL(service: Service, value: boolean) {
        this['addURL' + service.name] = value;
    }

    isAddingProviderParam(provider: ProviderConfig): boolean {
        return this['addParam' + provider.name];
    }

    setAddingProviderParam(provider: ProviderConfig, value: boolean) {
        this['addParam' + provider.name] = value;
    }

    addProvider(name: string, role: string) {
        let p = new ProviderConfig();
        p.name = name;
        p.role = role;
        this.providers.push(p);
        this.changedProviders = this.providers;
    }

    addProviderParam(provider: ProviderConfig, name: string, value: string) {
        if (!provider.params) {
            provider.params = new Map<string, string>();
        }
        provider.params[name] = value;
        this.changedProviders = this.providers;
    }

    getProviderParamNames(provider: ProviderConfig): string[] {
        if (!provider.params) {
            provider.params = new Map<string, string>();
        }
        return Object.keys(provider.params);
    }

    isProviderEnabled(pc: ProviderConfig): boolean {
        let result = false;

        if (pc) {
            if (typeof (pc.enabled) === 'string') {
                let lowered = pc.enabled.toLowerCase().trim();
                result = (lowered === 'true');
            } else if (typeof (pc.enabled) === 'boolean') {
                result = pc.enabled;
            }
        }

        return result;
    }

    // This method is required to maintain focus on descriptor service URLs when they're being edited.
    trackByServiceURLIndex(index: any, item: any) {
        return index;
    }

    hasSelectedResource(): boolean {
        return Boolean(this.resource) && Boolean(this.resource.name);
    }

    getTitleSubject(): string {
        switch (this.resourceType) {
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
