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
import {ResourceTypesService} from '../resourcetypes/resourcetypes.service';
import {ResourceService} from '../resource/resource.service';
import {BsModalComponent} from 'ng2-bs3-modal';
import {ProviderConfig} from '../resource-detail/provider-config';
import {AuthenticationWizard} from './authentication-wizard';
import {CategoryWizard} from './category-wizard';
import {AuthorizationWizard} from './authorization-wizard';
import {IdentityAssertionWizard} from './identity-assertion-wizard';
import {HaWizard} from './ha-wizard';
import {Resource} from '../resource/resource';
import {DisplayBindingProviderConfig} from './display-binding-provider-config';
import {OrderedParamContainer} from './ordered-param-container';
import {HostMapProviderWizard} from './hostmap-provider-wizard';
import {ProviderContributorWizard} from './provider-contributor-wizard';
import {WebAppSecurityWizard} from './webappsec-wizard';
import {ValidationUtils} from '../utils/validation-utils';
import {HttpErrorResponse} from '@angular/common/http';

@Component({
    selector: 'app-provider-config-wizard',
    templateUrl: './provider-config-wizard.component.html',
    styleUrls: ['./provider-config-wizard.component.css']
})
export class ProviderConfigWizardComponent implements OnInit {
    private static CATEGORY_STEP = 1;
    private static TYPE_STEP = 2;
    private static PARAMS_STEP = 3;

    // Provider Categories
    private static CATEGORY_AUTHENTICATION = 'Authentication';
    private static CATEGORY_AUTHORIZATION = 'Authorization';
    private static CATEGORY_ID_ASSERTION = 'Identity Assertion';
    private static CATEGORY_HA = 'HA';
    private static CATEGORY_WEBAPPSEC = 'Web Application Security';
    private static CATEGORY_HOSTMAP = 'Host Mapping';
    private static providerCategories: string[] = [ProviderConfigWizardComponent.CATEGORY_AUTHENTICATION,
        ProviderConfigWizardComponent.CATEGORY_AUTHORIZATION,
        ProviderConfigWizardComponent.CATEGORY_ID_ASSERTION,
        ProviderConfigWizardComponent.CATEGORY_WEBAPPSEC,
        ProviderConfigWizardComponent.CATEGORY_HA,
        ProviderConfigWizardComponent.CATEGORY_HOSTMAP
    ];

    private static CATEGORY_TYPES: Map<string, CategoryWizard> =
        new Map([
            [ProviderConfigWizardComponent.CATEGORY_AUTHENTICATION, new AuthenticationWizard() as CategoryWizard],
            [ProviderConfigWizardComponent.CATEGORY_AUTHORIZATION, new AuthorizationWizard() as CategoryWizard],
            [ProviderConfigWizardComponent.CATEGORY_ID_ASSERTION, new IdentityAssertionWizard() as CategoryWizard],
            [ProviderConfigWizardComponent.CATEGORY_HA, new HaWizard() as CategoryWizard],
            [ProviderConfigWizardComponent.CATEGORY_WEBAPPSEC, new WebAppSecurityWizard() as CategoryWizard],
            [ProviderConfigWizardComponent.CATEGORY_HOSTMAP, new HostMapProviderWizard() as CategoryWizard]
        ]);

    @ViewChild('newProviderConfigModal')
    childModal: BsModalComponent;

    private step = 0;

    name = '';

    providers: Array<ProviderConfig> = [];

    selectedCategory: string;

    existingReadOnlyProvider = false;

    static isProviderConfigValid(pc: ProviderConfig): boolean {
        let isValid = true;
        if (pc instanceof DisplayBindingProviderConfig) {
            isValid = (pc as DisplayBindingProviderConfig).isValid();
        }
        return isValid;
    }

    // Type guard function to determine if a CategoryWizard is a ProviderContributorWizard
    static isProviderContributorWizard(wizard: any): wizard is ProviderContributorWizard {
        return (<ProviderContributorWizard>wizard).getProviderRole !== undefined;
    }

    // Type Guard for identifying OrderedParamContainer implementations
    static isOrderedParamContainer = (x: any): x is OrderedParamContainer => x.orderParams;

    constructor(private resourceTypesService: ResourceTypesService, private resourceService: ResourceService) {
    }

    ngOnInit() {
        this.selectedCategory = ProviderConfigWizardComponent.CATEGORY_AUTHENTICATION; // Default to authentication
    }

    open(size?: string) {
        this.reset();
        this.childModal.open(size ? size : 'lg');
    }

    reset() {
        this.getCategoryWizard(this.selectedCategory).reset();
        this.step = 0;
        this.name = '';
        this.providers = [];
        this.selectedCategory = ProviderConfigWizardComponent.CATEGORY_AUTHENTICATION;
        this.existingReadOnlyProvider = false;
    }

    onFinishAdd() {
        console.debug('ProviderConfigWizard --> Selected provider category: ' + this.selectedCategory);

        let catWizard = this.getCategoryWizard(this.selectedCategory);
        let type = catWizard ? catWizard.getSelectedType() : 'undefined';
        console.debug('ProviderConfigWizard --> Selected provider type: ' + type);

        if (catWizard) {
            let pc: ProviderConfig;

            let isContributed = false;

            let isContributingWizard = false;

            if (ProviderConfigWizardComponent.isProviderContributorWizard(catWizard)) {
                isContributingWizard = true;
                let contribWiz = catWizard as ProviderContributorWizard;
                let role = contribWiz.getProviderRole();
                console.debug('Wizard is ProviderContributorWizard for role ' + role);
                for (let provider of this.providers) {
                    if (role === provider.role) {
                        console.debug('Found existing provider config for ' + role);
                        pc = provider;
                        break;
                    }
                }

                if (!pc) {
                    console.debug('No existing provider config found for ' + role + ', so creating one...');
                    pc = contribWiz.createNewProviderConfig();
                    this.providers.push(pc);
                }

                // If there is an existing provider config of the current type
                if (pc) {
                    if (ProviderConfigWizardComponent.isProviderConfigValid(catWizard.getProviderConfig())) {
                        contribWiz.contribute(pc);
                        isContributed = true;
                    } else {
                        console.debug('CategoryWizard ProviderConfig is not valid.');
                    }
                }
            }

            if (!pc && !isContributingWizard) { // If not a contributing wizard, just use the category wizard's provider config
                pc = catWizard.getProviderConfig();
            }

            if (pc && (isContributed || (!isContributingWizard && ProviderConfigWizardComponent.isProviderConfigValid(pc)))) {
                if (!isContributed) {
                    this.providers.push(pc);
                }
                console.debug('ProviderConfigWizard --> Provider: name=' + pc.name + ', role=' + pc.role + ', enabled=' + pc.enabled);
                if (pc.params) {
                    // If the provider is managing its own param order, allow it to re-order the params now
                    if (ProviderConfigWizardComponent.isOrderedParamContainer(pc)) {
                        pc.params = (pc as OrderedParamContainer).orderParams(pc.params);
                    }

                    for (let name of Object.getOwnPropertyNames(pc.params)) {
                        console.debug('\tParam: ' + name + ' = ' + pc.params[name]);
                    }
                }

                this.step = 0; // Return to the beginning

                // Clear the wizard state
                this.getCategoryWizard(this.selectedCategory).reset();
            } else {
                console.debug('ProviderConfig is missing or invalid.');
            }
        }
    }

    save() {
        // Identify the new resource
        let newResource = new Resource();
        newResource.name = this.name + '.json';

        // Make constrained copies of ProviderConfig objects to avoid persisting unwanted object properties
        let tmp = new Array<ProviderConfig>();
        for (let p of this.providers) {
            let pp = new ProviderConfig();
            pp.role = p.role;
            pp.name = p.name;
            pp.enabled = p.enabled;
            pp.params = p.params;
            tmp.push(pp);
        }

        // Persist the new provider configuration
        this.resourceService.createResource('Provider Configurations',
            newResource,
            this.resourceService.serializeProviderConfiguration(tmp, 'json'))
            .then(() => {
                // Reload the resource list presentation
                this.resourceTypesService.selectResourceType('Provider Configurations');

                // Set the new descriptor as the selected resource
                this.resourceService.getProviderConfigResources().then(resources => {
                    for (let res of resources) {
                        if (res.name === newResource.name) {
                            this.resourceService.selectedResource(res);
                            break;
                        }
                    }
                });
                this.childModal.close(); // close the dialog if there was no error
            }).catch((err: HttpErrorResponse) => {
                this.existingReadOnlyProvider = (err.status === 409);
                console.error('Error creating ' + newResource + ' : ' + err.message);
          });
    }

    onNextStep() {
        ++this.step;
    }

    onPreviousStep() {
        --this.step;
    }

    hasMoreSteps(): boolean {
        let result = false;
        let catWizard = this.getCategoryWizard(this.selectedCategory);
        if (catWizard) {
            result = (this.step < (catWizard.getSteps() - 1));
            if (result) {
                if (this.isProviderTypeStep()) { // Next step would be params
                    // Check for the need to display the params step
                    let pc = catWizard.getProviderConfig();
                    if (pc && pc instanceof DisplayBindingProviderConfig) {
                        let propNames: string[] = (pc as DisplayBindingProviderConfig).getDisplayPropertyNames();
                        result = propNames.length > 0;
                    }
                }
            }
        }
        return result;
    }

    isRootStep(): boolean {
        return (this.step === 0);
    }

    isProviderCategoryStep(): boolean {
        return (this.step === ProviderConfigWizardComponent.CATEGORY_STEP);
    }

    isProviderTypeStep(): boolean {
        return (this.step === ProviderConfigWizardComponent.TYPE_STEP);
    }

    isProviderParamsStep(): boolean {
        return (this.step === ProviderConfigWizardComponent.PARAMS_STEP);
    }

    getProviderCategories(): string[] {
        return ProviderConfigWizardComponent.providerCategories;
    }

    getCategoryWizard(category?: string): CategoryWizard {
        return ProviderConfigWizardComponent.CATEGORY_TYPES.get(category ? category : this.selectedCategory);
    }

    getProviderTypes(category?: string): string[] {
        let catWizard = this.getCategoryWizard(category);
        if (catWizard) {
            return catWizard.getTypes();
        } else {
            console.debug('ProviderConfigWizard --> Unresolved category wizard for ' + (category ? category : this.selectedCategory));
        }
        return [];
    }

    getProviderParams(): string[] {
        let catWizard = this.getCategoryWizard();
        if (catWizard) {
            let pc = catWizard.getProviderConfig();
            if (pc) {
                if (pc instanceof DisplayBindingProviderConfig) {
                    let dispPC = pc as DisplayBindingProviderConfig;
                    return dispPC.getDisplayPropertyNames();
                } else {
                    return [];
                }
            } else {
                console.log('ProviderConfigWizard --> No provider config from category wizard ' + typeof (catWizard));
            }
        } else {
            console.debug('ProviderConfigWizard --> Unresolved category wizard for ' + this.selectedCategory);
        }
        return [];
    }

    setProviderParamBinding(name: string, value: string) {
        let catWizard = this.getCategoryWizard();
        if (catWizard) {
            let pc = catWizard.getProviderConfig();
            if (pc) {
                if (pc instanceof DisplayBindingProviderConfig) {
                    let property = (pc as DisplayBindingProviderConfig).getDisplayNamePropertyBinding(name);
                    if (property) {
                        pc.setParam(property, value);
                        console.debug('ProviderConfigWizard --> Set ProviderConfig param value: ' + property + '=' + value);
                    } else {
                        console.debug('ProviderConfigWizard --> No provider property configured for ' + name);
                    }
                }
            }
        }
    }

    getProviderParamBinding(name: string): string {
        let catWizard = this.getCategoryWizard();
        if (catWizard) {
            let pc = catWizard.getProviderConfig();
            if (pc) {
                if (pc instanceof DisplayBindingProviderConfig) {
                    let dispPC = pc as DisplayBindingProviderConfig;
                    let value = pc.getParam(dispPC.getDisplayNamePropertyBinding(name));
                    return (value ? value : '');
                }
            }
        }
        return '';
    }

    getConfiguredProviderDisplayNames(): string[] {
        let result: string[] = [];

        for (let p of this.providers) {
            let pName: string;
            let pRole: string;
            if (p instanceof DisplayBindingProviderConfig) {
                pName = (p as DisplayBindingProviderConfig).getType();
                pRole = p.getRole();
            } else {
                pName = p.name;
                pRole = p.role;
            }
            result.push(pName + ' (' + pRole + ')');
        }

        return result;
    }

    isValidProviderConfigName(): boolean {
        return ValidationUtils.isValidResourceName(this.name);
    }

    isPasswordParam(name: string) {
        let result = false;

        let p = this.getCategoryWizard().getProviderConfig();
        if (p && p instanceof DisplayBindingProviderConfig) {
            result = (p as DisplayBindingProviderConfig).isPasswordParam(name);
        }

        return result;
    }

    isValidParamValue(paramName: string) {
        let isValid = true;
        let pc: ProviderConfig = this.getCategoryWizard().getProviderConfig();
        if (pc) {
            if (pc instanceof DisplayBindingProviderConfig) {
                isValid = (pc as DisplayBindingProviderConfig).isValidParamValue(paramName);
            }
        }
        return isValid;
    }

    togglePasswordDisplay(propertyName: string) {
        this['show' + propertyName] = !this['show' + propertyName];
    }

    getPasswordDisplay(propertName: string): boolean {
        return this['show' + propertName];
    }

    isExistingReadOnlyProvider(): boolean {
        return this.existingReadOnlyProvider;
    }
}
