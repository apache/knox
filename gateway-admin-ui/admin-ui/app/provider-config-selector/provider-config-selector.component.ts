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
import {BsModalComponent} from 'ng2-bs3-modal';
import {Descriptor} from '../resource-detail/descriptor';
import {HttpErrorResponse} from '@angular/common/http';


@Component({
    selector: 'app-provider-config-selector',
    templateUrl: './provider-config-selector.component.html',
    styleUrls: ['./provider-config-selector.component.css']
})
export class ProviderConfigSelectorComponent {

    @ViewChild('chooseProviderConfigModal')
    private childModal: BsModalComponent;

    private providerConfigs: Resource[];

    // The descriptor whose provider configuration reference should be updated as a result of the selection in this component
    private descriptor: Descriptor;

    selectedName: string;

    constructor(private resourceService: ResourceService) {
    }

    open(desc: Descriptor, size?: string) {
        this.descriptor = desc;
        this.selectedName = desc.providerConfig; // Set the default selection based on the current ref in the descriptor

        // Load the available provider configs every time this modal is open
        this.resourceService.getResources('Provider Configurations')
            .then(result => this.providerConfigs = result)
            .catch((err: HttpErrorResponse) => console.debug('Error access provider configurations: ' + err));

        this.childModal.open(size);
    }

    onClose() {
        // Assign the descriptor's provider configuration to the selection
        this.descriptor.setProviderConfig(this.selectedName);
    }

    getProviderConfigs(): Resource[] {
        return this.providerConfigs;
    }

    getReferenceName(providerConfigName: string): string {
        let refName = providerConfigName;
        let extIndex = providerConfigName.lastIndexOf('.');
        if (extIndex > 0) {
            refName = providerConfigName.substring(0, extIndex);
        }
        return refName;
    }

}
