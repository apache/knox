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
import {Injectable} from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpHeaders} from '@angular/common/http';
import swal from 'sweetalert';

import 'rxjs/add/operator/toPromise';

import {GeneralProxyInformation} from './generalProxyInformation/general.proxy.information';
import {TopologyInformation} from './topologies/topology.information';

@Injectable()
export class HomepageService {
    apiUrl = window.location.pathname.replace(new RegExp('home/.*'), 'api/v1/metadata/');
    generalProxyInformationUrl = this.apiUrl + 'info';
    publicCertUrl = this.apiUrl + 'publicCert?type=';
    topologiesUrl = this.apiUrl + 'topologies';

    constructor(private http: HttpClient) {}

    getGeneralProxyInformation(): Promise<GeneralProxyInformation> {
        let headers = new HttpHeaders();
        headers = this.addJsonHeaders(headers);
        return this.http.get(this.generalProxyInformationUrl, { headers: headers})
            .toPromise()
            .then(response => response['generalProxyInfo'] as GeneralProxyInformation)
            .catch((err: HttpErrorResponse) => {
                console.debug('HomepageService --> getGeneralProxyInformation() --> '
                               + this.generalProxyInformationUrl + '\n  error: ' + err.message);
                if (err.status === 401) {
                    window.location.assign(document.location.pathname);
                } else {
                    return this.handleError(err);
                }
            });
    }

    getTopologies(): Promise<TopologyInformation[]> {
        let headers = new HttpHeaders();
        headers = this.addJsonHeaders(headers);
        return this.http.get(this.topologiesUrl, { headers: headers})
            .toPromise()
            .then(response => response['topologyInformations'].topologyInformation as TopologyInformation[])
            .catch((err: HttpErrorResponse) => {
                console.debug('HomepageService --> getTopologies() --> ' + this.topologiesUrl + '\n  error: ' + err.message);
                if (err.status === 401) {
                    window.location.assign(document.location.pathname);
                } else {
                    return this.handleError(err);
                }
            });
    }

    addJsonHeaders(headers: HttpHeaders): HttpHeaders {
        return this.addCsrfHeaders(headers.append('Accept', 'application/json').append('Content-Type', 'application/json'));
    }

    addCsrfHeaders(headers: HttpHeaders): HttpHeaders {
        return this.addXHRHeaders(headers.append('X-XSRF-Header', 'homepage'));
    }

    addXHRHeaders(headers: HttpHeaders): HttpHeaders {
        return headers.append('X-Requested-With', 'XMLHttpRequest');
    }

    private handleError(error: HttpErrorResponse): Promise<any> {
        swal('Oops!', 'Something went wrong!\n' + (error.error ? error.error : error.statusText), 'error');
        return Promise.reject(error.message || error);
    }

}
