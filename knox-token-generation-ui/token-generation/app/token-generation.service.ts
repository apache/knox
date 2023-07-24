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
import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import Swal from 'sweetalert2';
import { TokenData, TokenRequestParams, TokenResultData, TssStatusData } from './token-generation.models';

@Injectable()
export class TokenGenService {
    readonly baseURL: string;
    readonly tokenURL: string;
    readonly tssStatusRequestURL: string;

    constructor(private http: HttpClient) {
        const loginPageSuffix = 'token-generation/index.html';
        const knoxtokenURL = 'knoxtoken/api/v1/token';
        const tssStatusURL = 'knoxtoken/api/v1/token/getTssStatus';

        let topologyContext = window.location.pathname.replace(loginPageSuffix, '');
        let temporaryURL = topologyContext.substring(0, topologyContext.lastIndexOf('/'));
        this.baseURL = temporaryURL.substring(0, temporaryURL.lastIndexOf('/') + 1);
        this.tokenURL = topologyContext + knoxtokenURL;
        this.tssStatusRequestURL = topologyContext + tssStatusURL;
    }

    getTokenStateServiceStatus(): Promise<TssStatusData> {
        let headers = new HttpHeaders();
        headers = this.addHeaders(headers);

        return this.http.get<any>(this.tssStatusRequestURL, { headers: headers })
        .toPromise()
        .then(responseData => {
                /**
                 * The data needs to be returned this way, because if the return type would be set to <TssStatusData> and the responseData
                 * would be just returned without modification and the boolean values would act like string.
                 */
                return {
                    tokenManagementEnabled: responseData.tokenManagementEnabled === 'true',
                    maximumLifetimeText: responseData.maximumLifetimeText,
                    maximumLifetimeSeconds: responseData.maximumLifetimeSeconds,
                    lifespanInputEnabled: responseData.lifespanInputEnabled === 'true',
                    impersonationEnabled: responseData.impersonationEnabled === 'true',
                    configuredTssBackend: responseData.configuredTssBackend,
                    allowedTssForTokengen: responseData.allowedTssForTokengen === 'true',
                    actualTssBackend: responseData.actualTssBackend
                };
        })
        .catch((error: HttpErrorResponse) => {
            console.debug('TokenGenService --> getTokenStateServiceStatus() --> '
                + this.tssStatusRequestURL + '\n  error: ' + error.message);
            if (error.status === 401) {
                window.location.reload();
            } else {
                return this.handleError(error);
            }
        });
    }

    getGeneratedTokenData(params: TokenRequestParams): Promise<TokenResultData> {
        let headers = new HttpHeaders();
        headers = this.addHeaders(headers);

        let httpParams = new HttpParams();
        if (params.lifespanInputEnabled) {
            httpParams = httpParams.append('lifespan', 'P' + params.lifespanDays + 'DT'
                + params.lifespanHours + 'H' + params.lifespanMins + 'M');
        }
        if (params.comment) {
            httpParams = httpParams.append('comment', params.comment);
        }
        if (params.impersonation) {
            httpParams = httpParams.append('doAs', params.impersonation);
        }

        return this.http.get<TokenData>(this.tokenURL, { params: httpParams, headers: headers })
        .toPromise()
        .then(tokenData => {
            let decodedToken = this.b64DecodeUnicode(tokenData.access_token.split('.')[1]);
            let jwtJson = JSON.parse(decodedToken);
            return {
                accessToken: tokenData.access_token,
                user: jwtJson.sub,
                accessPasscode: tokenData.passcode,
                expiry: new Date(tokenData.expires_in).toLocaleString(),
                homepageURL: this.baseURL + tokenData.homepage_url,
                targetURL: window.location.protocol + '//' + window.location.host + this.baseURL + tokenData.target_url
            };
        })
        .catch((error: HttpErrorResponse) => {
            console.debug('TokenGenService --> getGeneratedTokenData() --> ' + this.tokenURL + '\n  error: ' + error.message);
            if (error.status === 401) {
                window.location.reload();
            } else {
                return this.handleError(error);
            }
        });
    }

    private addHeaders(headers: HttpHeaders): HttpHeaders {
        return headers.append('Accept', 'application/json')
            .append('Content-Type', 'application/json')
            .append('X-XSRF-Header', 'homepage')
            .append('X-Requested-With', 'XMLHttpRequest');
    }

    private handleError(error: HttpErrorResponse): Promise<any> {
	    let errorMsg = '';
        if (error.error) {
            errorMsg = error.error.RemoteException ? error.error.RemoteException.message : error.error;
        }
        Swal.fire({
            icon: 'error',
            title: 'Oops!',
            text: 'Something went wrong!\n' + (errorMsg ? errorMsg : error.statusText),
            confirmButtonColor: '#7cd1f9'
          });
        let requestErrorMessage = 'Response from ' + error.url + ' - ' + error.status + ': ' + error.statusText;
        if (errorMsg) {
            requestErrorMessage += ' (' + errorMsg + ')';
        }
        return Promise.reject(requestErrorMessage);
    }

    private b64DecodeUnicode(string: string) {
        // Going backwards: from bytestream, to percent-encoding, to original string.
        return decodeURIComponent(atob(string).split('').map(function(c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(''));
    }
}
