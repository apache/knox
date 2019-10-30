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
import {HttpClient} from '@angular/common/http';
import {BsModalComponent} from 'ng2-bs3-modal';
import swal from 'sweetalert';

import {ServiceDefinitionService} from './servicedefinition.service';
import {ResourceTypesService} from '../resourcetypes/resourcetypes.service';

import 'brace/theme/monokai';
import 'brace/mode/xml';

@Component({
    selector: 'app-service-definition-wizard',
    templateUrl: './new-service-definition.component.html',
    styleUrls: ['./new-service-definition.component.css']
})
export class NewServiceDefinitionComponent implements OnInit {

    serviceDefinitionXmlTemplate = 'assets/new-service-definition-template.xml';
    defaultServiceDefinitionContent: string;
    serviceDefinitionContent: string;
    theme: String = 'monokai';
    options: any = {useWorker: false, printMargin: false};

    @ViewChild('newServiceDefinitionModal')
    childModal: BsModalComponent;

    constructor(private http: HttpClient,
                private serviceDefinitionService: ServiceDefinitionService,
                private resourceTypesService: ResourceTypesService) {
    }

    ngOnInit() {
        this.http.get(this.serviceDefinitionXmlTemplate, {responseType: 'text'})
                     .subscribe(data => this.defaultServiceDefinitionContent = data);
    }

    open(size?: string) {
        this.reset();
        this.childModal.open(size ? size : 'lg');
    }

    reset() {
        this.serviceDefinitionContent = this.defaultServiceDefinitionContent;
    }

    onClose() {
        this.serviceDefinitionService.saveNewServiceDefinition(this.serviceDefinitionContent)
                                     .then(response => {
                                           swal('Saved successfully!');
                                           this.resourceTypesService.selectResourceType('Service Definitions');
                                           this.serviceDefinitionService.selectedServiceDefinition(null);
                                     });
    }

    validate(): boolean {
        if (this.serviceDefinitionContent) {
            if (this.serviceDefinitionContent.indexOf('your_service_name') >= 0
                || this.serviceDefinitionContent.indexOf('YOUR_ROLE') >= 0) {
                return false;
            }
        }
        return true;
    }

}
