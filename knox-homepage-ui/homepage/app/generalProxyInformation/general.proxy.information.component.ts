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
    template: `
            <div class="table-responsive">
                <table class="table table-striped table-hover">
                    <thead>
                        <tr><th colspan="2" align="center">General Proxy Information</th></tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>Knox Version</td>
                            <td>{{ getVersion() }}</td>
                        </tr>
                        <tr>
                            <td>TLS Public Certificate PEM file</td>
                            <td><a href="{{ getPublicCertPemPath() }}" target="_blank">Click here to download</a></td>
                        </tr>
                        <tr>
                            <td>TLS Public Certificate JKS file</td>
                            <td><a href="{{ getPublicCertJksPath() }}" target="_blank">Click here to download</a></td>
                        </tr>
                        <tr>
                            <td>Admin UI URL</td>
                            <td><a href="{{ getAdminUiUrl() }}" target="_blank">{{ getAdminUiUrl() }}</a></td>
                        </tr>
                        <tr>
                            <td>
                                Admin API Details
                                <span class="inline-glyph glyphicon glyphicon-info-sign btn btn-xs"
                                title="{{ getAdminApiDescription() }}" data-toggle="tooltip"></span>
                            </td>
                            <td>
                                <a href="{{ getAdminApiBookUrl() }}" target="_blank">{{ getAdminApiBookUrl() }}</a>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
    `,
    providers: [HomepageService]
})

export class GeneralProxyInformationComponent implements OnInit {

    generalProxyInformation: GeneralProxyInformation;

    constructor(private homepageService: HomepageService) {}

    getVersion() {
        if (this.generalProxyInformation) {
            return this.generalProxyInformation.version;
          }
          return '';
    }
    getPublicCertPemPath() {
      if (this.generalProxyInformation) {
        return this.generalProxyInformation.publicCertPemPath;
      }
      return '';
    }

    getPublicCertJksPath() {
        if (this.generalProxyInformation) {
          return this.generalProxyInformation.publicCertJksPath;
        }
        return '';
    }

    getAdminUiUrl() {
        if (this.generalProxyInformation) {
            return this.generalProxyInformation.adminUiUrl;
          }
        return '';
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

    ngOnInit(): void {
        console.debug('GeneralProxyInformationComponent --> ngOnInit() --> ');
        this.homepageService.getGeneralProxyInformation()
                            .then(generalProxyInformation => this.generalProxyInformation = generalProxyInformation);
    }

}
