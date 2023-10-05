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
import Swal from 'sweetalert2';

import 'rxjs/add/operator/toPromise';

import {KnoxToken} from './knox.token';
import {SessionInformation} from './session.information';

@Injectable()
export class TokenManagementService {
    sessionUrl = window.location.pathname.replace(new RegExp('token-management/.*'), 'session/api/v1/sessioninfo');
    apiUrl = window.location.pathname.replace(new RegExp('token-management/.*'), 'knoxtoken/api/v1/token/');
    getAllKnoxTokensUrl = this.apiUrl + 'getUserTokens?allTokens=true';
    getKnoxTokensUrl = this.apiUrl + 'getUserTokens?userNameOrCreatedBy=';
    enableKnoxTokenUrl = this.apiUrl + 'enable';
    enableKnoxTokensBatchUrl = this.apiUrl + 'enableTokens';
    disableKnoxTokenUrl = this.apiUrl + 'disable';
    disableKnoxTokensBatchUrl = this.apiUrl + 'disableTokens';
    revokeKnoxTokenUrl = this.apiUrl + 'revoke';
    revokeKnoxTokensBatchUrl = this.apiUrl + 'revokeTokens';
    getTssStatusUrl = this.apiUrl + 'getTssStatus';

    constructor(private http: HttpClient) {}

    getKnoxTokens(userName: string, canSeeAllTokens: boolean): Promise<KnoxToken[]> {
        let headers = new HttpHeaders();
        headers = this.addJsonHeaders(headers);
        let url = canSeeAllTokens ? this.getAllKnoxTokensUrl : (this.getKnoxTokensUrl + userName);
        return this.http.get(url, { headers: headers})
            .toPromise()
            .then(response => response['tokens'] as KnoxToken[])
            .catch((err: HttpErrorResponse) => {
                console.debug('TokenManagementService --> getKnoxTokens() --> ' + this.getKnoxTokensUrl + '\n  error: ' + err.message);
                if (err.status === 401) {
                    window.location.assign(document.location.pathname);
                } else {
                    return this.handleError(err);
                }
            });
    }

    setEnabledDisabledFlag(enable: boolean, tokenId: string): Promise<string> {
        let xheaders = new HttpHeaders();
        xheaders = this.addJsonHeaders(xheaders);
        let urlToUse = enable ? this.enableKnoxTokenUrl : this.disableKnoxTokenUrl;
        return this.http.put(urlToUse, tokenId, {headers: xheaders, responseType: 'text'})
            .toPromise()
            .then(response => response)
            .catch((err: HttpErrorResponse) => {
                console.debug('TokenManagementService --> setEnabledDisabledFlag() --> ' + urlToUse
                              + '\n  error: ' + err.status + ' ' + err.message);
                if (err.status === 401) {
                    window.location.assign(document.location.pathname);
                } else {
                    return this.handleError(err);
                }
            });
    }

    setEnabledDisabledFlagsInBatch(enable: boolean, tokenIds: string[]): Promise<string> {
        let xheaders = new HttpHeaders();
        xheaders = this.addJsonHeaders(xheaders);
        let urlToUse = enable ? this.enableKnoxTokensBatchUrl : this.disableKnoxTokensBatchUrl;
        return this.http.put(urlToUse, JSON.stringify(tokenIds), {headers: xheaders, responseType: 'text'})
            .toPromise()
            .then(response => response)
            .catch((err: HttpErrorResponse) => {
                console.debug('TokenManagementService --> setEnabledDisabledFlagsInBatch() --> ' + urlToUse
                              + '\n  error: ' + err.status + ' ' + err.message);
                if (err.status === 401) {
                    window.location.assign(document.location.pathname);
                } else {
                    return this.handleError(err);
                }
            });
    }

    revokeToken(tokenId: string) {
        let xheaders = new HttpHeaders();
        xheaders = this.addJsonHeaders(xheaders);
        return this.http.request('DELETE', this.revokeKnoxTokenUrl, {headers: xheaders, body: tokenId, responseType: 'text'})
            .toPromise()
            .then(response => response)
            .catch((err: HttpErrorResponse) => {
                console.debug('TokenManagementService --> revokeToken() --> ' + this.revokeKnoxTokenUrl
                              + '\n  error: ' + err.status + ' ' + err.message);
                if (err.status === 401) {
                    window.location.assign(document.location.pathname);
                } else {
                    return this.handleError(err);
                }
            });
    }

    revokeTokensInBatch(tokenIds: string[]) {
        let xheaders = new HttpHeaders();
        xheaders = this.addJsonHeaders(xheaders);
        return this.http.request('DELETE', this.revokeKnoxTokensBatchUrl,
                                 {headers: xheaders, body: JSON.stringify(tokenIds), responseType: 'text'})
            .toPromise()
            .then(response => response)
            .catch((err: HttpErrorResponse) => {
                console.debug('TokenManagementService --> revokeTokensInBatch() --> ' + this.revokeKnoxTokensBatchUrl
                              + '\n  error: ' + err.status + ' ' + err.message);
                if (err.status === 401) {
                    window.location.assign(document.location.pathname);
                } else {
                    return this.handleError(err);
                }
            });
    }

    getSessionInformation(): Promise<SessionInformation> {
        let headers = new HttpHeaders();
        headers = this.addJsonHeaders(headers);
        return this.http.get(this.sessionUrl, { headers: headers})
            .toPromise()
            .then(response => response['sessioninfo'] as SessionInformation)
            .catch((err: HttpErrorResponse) => {
                console.debug('TokenManagementService --> getSessionInformation() --> ' + this.sessionUrl + '\n  error: ' + err.message);
                if (err.status === 401) {
                    window.location.assign(document.location.pathname);
                } else {
                    return this.handleError(err);
                }
            });
    }

    getImpersonationEnabled(): Promise<string> {
        let headers = new HttpHeaders();
        headers = this.addJsonHeaders(headers);
        return this.http.get(this.getTssStatusUrl, { headers: headers})
            .toPromise()
            .then(response => response['impersonationEnabled'] as string)
            .catch((err: HttpErrorResponse) => {
                console.debug('TokenManagementService --> getImpersonationEnabled() --> ' + this.getTssStatusUrl
                              + '\n  error: ' + err.message);
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
        Swal.fire({
            icon: 'error',
            title: 'Oops!',
            text: 'Something went wrong!\n' + (error.error ? error.error : error.statusText),
            confirmButtonColor: '#7cd1f9'
          });
        return Promise.reject(error.message || error);
    }

}
