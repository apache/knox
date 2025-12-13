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
import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import Swal from 'sweetalert2/dist/sweetalert2.esm.all.js';
import { firstValueFrom } from 'rxjs';
import { GeneralProxyInformation } from '../model/general.proxy.information';
import { TopologyInformation } from '../model/topology.information';
import { SessionInformation } from '../model/session.information';

@Injectable()
export class HomepageService {
    pathParts = window.location.pathname.split('/');
    topologyContext = '/' + this.pathParts[1] + '/' + this.pathParts[2] + '/';
    apiUrl = this.topologyContext + 'api/v1/metadata/';
    sessionUrl = this.topologyContext + 'session/api/v1/sessioninfo';
    generalProxyInformationUrl = this.apiUrl + 'info';
    publicCertUrl = this.apiUrl + 'publicCert?type=';
    topologiesUrl = this.apiUrl + 'topologies';

    constructor(private http: HttpClient, private route: ActivatedRoute) { }

    getGeneralProxyInformation(): Promise<GeneralProxyInformation> {
        let headers = this.addJsonHeaders(new HttpHeaders());
        return firstValueFrom(this.http.get(this.generalProxyInformationUrl, { headers }))
            .then(resp => resp['generalProxyInfo'] as GeneralProxyInformation)
            .catch(err => this.handleError(err));
    }

    getTopologies(): Promise<TopologyInformation[]> {
        let headers = this.addJsonHeaders(new HttpHeaders());
        return firstValueFrom(this.http.get(this.topologiesUrl, { headers }))
            .then(resp => resp['topologyInformations'].topologyInformation as TopologyInformation[])
            .catch(err => this.handleError(err));
    }

    getSessionInformation(): Promise<SessionInformation> {
        let headers = this.addJsonHeaders(new HttpHeaders());
        return firstValueFrom(this.http.get(this.sessionUrl, { headers }))
            .then(resp => resp['sessioninfo'] as SessionInformation)
            .catch(err => this.handleError(err,));
    }

    logout(logoutUrl: string): Promise<JSON> {
        let headers = this.addJsonHeaders(new HttpHeaders());
        return firstValueFrom(this.http.get(logoutUrl, { headers }))
            .then(resp => resp['loggedOut'])
            .catch(err => this.handleError(err));
    }

    getProfile(profileName: string): Promise<JSON> {
        let headers = this.addJsonHeaders(new HttpHeaders());
        let url = `${this.apiUrl}profiles/${profileName}`;
        return firstValueFrom(this.http.get(url, { headers }))
            .catch(err => this.handleError(err));
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
        void Swal.fire({
            icon: 'error',
            title: 'Oops!',
            text: `Something went wrong!\n${error.error ? error.error : error.statusText}`,
            confirmButtonColor: '#7cd1f9'
        });

        return Promise.reject(error.message || error);
    }


}
