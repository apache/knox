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
import {HomepageService} from '../homepage.service';
import {GeneralProxyInformation} from './general.proxy.information';

@Component({
    selector: 'app-general-proxy-information',
    templateUrl: './general.proxy.information.component.html',
    providers: [HomepageService]
})

export class GeneralProxyInformationComponent implements OnInit {

    generalProxyInformation: GeneralProxyInformation;

    constructor(private homepageService: HomepageService) {
        this['showGeneralProxyInformation'] = false;
    }

    getVersion() {
        if (this.generalProxyInformation) {
            return this.generalProxyInformation.version;
          }
          return '';
    }

    getAdminUiUrl() {
        if (this.generalProxyInformation) {
            return this.generalProxyInformation.adminUiUrl;
          }
        return '';
    }

    getMetadataAPIUrl(endpoint: string) {
        return this.getAdminUiUrl().replace('manager/admin-ui/', 'metadata/api/v1/metadata/' + endpoint);
    }

    getAdminApiDescription() {
       return 'Knox provides a REST API which allows end-users executing CRUD operations on topologies/shared-porviders/descriptors/'
              + 'service definitions as well as fetching the Knox version.';
    }

    getAdminApiBookUrl() {
        if (this.generalProxyInformation) {
            return this.generalProxyInformation.adminApiBookUrl;
          }
        return '';
    }

    getTokenGenerationUrl() {
        return this.getAdminUiUrl().replace(new RegExp('manager/admin-ui/*'), 'homepage/tokengen/index.html');
    }

    ngOnInit(): void {
        console.debug('GeneralProxyInformationComponent --> ngOnInit() --> ');
        this.homepageService.getGeneralProxyInformation()
                            .then(generalProxyInformation => this.generalProxyInformation = generalProxyInformation);
    }

    toggleBoolean(propertyName: string) {
        this[propertyName] = !this[propertyName];
    }
}
