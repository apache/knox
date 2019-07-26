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
import {HttpClient, HttpHeaders} from '@angular/common/http';

import 'rxjs/add/operator/toPromise';

import {GatewayVersion} from './gateway-version';

@Injectable()
export class GatewayVersionService {
    private apiUrl = window.location.pathname.replace(new RegExp('admin-ui/.*'), 'api/v1/');
    private versionUrl = this.apiUrl + 'version';

    constructor(private http: HttpClient) {
    }

    getVersion(): Promise<GatewayVersion> {
        let headers = new HttpHeaders();
        headers = this.addHeaders(headers);
        return this.http.get(this.versionUrl, {headers: headers})
            .toPromise()
            .then(response => {
                return response['ServerVersion'] as GatewayVersion;
            })
            .catch(this.handleError);
    }

    addHeaders(headers: HttpHeaders) {
        return headers.append('Accept', 'application/json')
            .append('Content-Type', 'application/json')
            .append('X-XSRF-Header', 'admin-ui');
    }

    private handleError(error: any): Promise<any> {
        console.error('An error occurred', error); // for demo purposes only
        return Promise.reject(error.message || error);
    }
}
