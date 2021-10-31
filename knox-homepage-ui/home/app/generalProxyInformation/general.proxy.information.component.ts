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
import {ActivatedRoute} from '@angular/router';
import {HomepageService} from '../homepage.service';
import {GeneralProxyInformation} from './general.proxy.information';

@Component({
    selector: 'app-general-proxy-information',
    templateUrl: './general.proxy.information.component.html',
    providers: [HomepageService]
})

export class GeneralProxyInformationComponent implements OnInit {

    generalProxyInformation: GeneralProxyInformation;
    profile: JSON;

    constructor(private homepageService: HomepageService, private route: ActivatedRoute) {
        this['showGeneralProxyInformation'] = false;
        this['showKnoxVersion'] = true;
        this['showPublicCerts'] = true;
        this['showAdminUI'] = true;
        this['showAdminAPI'] = true;
        this['showMetadataAPI'] = true;
        this['showTokens'] = true;
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

    getTokenManagementUrl() {
        return this.getAdminUiUrl().replace(new RegExp('manager/admin-ui/*'), 'homepage/token-management/index.html');
    }

    isTokenManagementEnabled() {
        if (this.generalProxyInformation) {
	        return this.generalProxyInformation.enableTokenManagement === 'true';
	    }
        return false;
    }

    ngOnInit(): void {
        console.debug('GeneralProxyInformationComponent --> ngOnInit() --> ');
        this.homepageService.getGeneralProxyInformation()
                            .then(generalProxyInformation => this.generalProxyInformation = generalProxyInformation);
        let profileName;
        this.route.queryParams.subscribe(params => {
        	    profileName = params['profile'];
            console.debug('Profile name = ' + profileName)
            if (profileName) {
            	    console.debug('Fetching profile information...');
            	    this.homepageService.getProfile(profileName).then(profile => this.setProfileFlags(profile));
            }
        });
    }

    setProfileFlags(profile: JSON) {
    	    console.debug('Setting GPI profile flags...');
        this['showKnoxVersion'] = (profile['gpi_version'] === 'true');
        this['showPublicCerts'] = (profile['gpi_cert'] === 'true');
        this['showAdminUI'] = (profile['gpi_admin_ui'] === 'true');
        this['showAdminAPI'] = (profile['gpi_admin_api'] === 'true');
        this['showMetadataAPI'] = (profile['gpi_md_api'] === 'true');
        this['showTokens'] = (profile['gpi_tokens'] === 'true');
    }

    toggleBoolean(propertyName: string) {
        this[propertyName] = !this[propertyName];
    }
}
