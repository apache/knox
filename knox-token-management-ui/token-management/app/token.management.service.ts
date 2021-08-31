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

import {KnoxToken} from './knox.token';

@Injectable()
export class TokenManagementService {
    sessionUrl = window.location.pathname.replace(new RegExp('token-management/.*'), 'session/api/v1/sessioninfo');
    apiUrl = window.location.pathname.replace(new RegExp('token-management/.*'), 'knoxtoken/api/v1/token/');
    getKnoxTokensUrl = this.apiUrl + 'getUserTokens?userName=';
    enableKnoxTokenUrl = this.apiUrl + 'enable';
    disableKnoxTokenUrl = this.apiUrl + 'disable';
    revokeKnoxTokenUrl = this.apiUrl + 'revoke';

    constructor(private http: HttpClient) {}

    getKnoxTokens(userName: string): Promise<KnoxToken[]> {
        let headers = new HttpHeaders();
        headers = this.addJsonHeaders(headers);
        return this.http.get(this.getKnoxTokensUrl + userName, { headers: headers})
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

    revokeToken(tokenId: string) {
        let xheaders = new HttpHeaders();
        xheaders = this.addJsonHeaders(xheaders);
        return this.http.post(this.revokeKnoxTokenUrl, tokenId, {headers: xheaders, responseType: 'text'})
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

    getUserName(): Promise<string> {
        let headers = new HttpHeaders();
        headers = this.addJsonHeaders(headers);
        return this.http.get(this.sessionUrl, { headers: headers})
            .toPromise()
            .then(response => response['sessioninfo'].user as string)
            .catch((err: HttpErrorResponse) => {
                console.debug('TokenManagementService --> getUserName() --> ' + this.sessionUrl + '\n  error: ' + err.message);
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
