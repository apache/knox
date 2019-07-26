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

import 'rxjs/add/operator/toPromise';
import {Subject} from 'rxjs/Subject';
import {Topology} from './topology';

@Injectable()
export class TopologyService {
    apiUrl = window.location.pathname.replace(new RegExp('admin-ui/.*'), 'api/v1/');
    topologiesUrl = this.apiUrl + 'topologies';
    selectedTopologySource = new Subject<Topology>();
    selectedTopology$ = this.selectedTopologySource.asObservable();
    changedTopologySource = new Subject<string>();
    changedTopology$ = this.changedTopologySource.asObservable();

    constructor(private http: HttpClient) {
    }

    getTopologies(): Promise<Topology[]> {
        let headers = new HttpHeaders();
        headers = this.addJsonHeaders(headers);
        return this.http.get(this.topologiesUrl, {
            headers: headers
        })
            .toPromise()
            .then(response => response['topologies'].topology as Topology[])
            .catch((err: HttpErrorResponse) => {
                console.debug('TopologyService --> getTopologies() --> ' + '\n  error: ' + err.message);
                if (err.status === 401) {
                    window.location.assign(document.location.pathname);
                } else {
                    return this.handleError(err);
                }
            });
    }

    getTopology(href: string): Promise<string> {
        let headers = new HttpHeaders();
        headers = this.addXmlHeaders(headers);
        return this.http.get(href, {
            headers: headers, responseType: 'text'
        })
            .toPromise()
            .then(response => response)
            .catch((err: HttpErrorResponse) => {
                console.debug('TopologyService --> getTopology() --> ' + href + '\n  error: ' + err.message);
                if (err.status === 401) {
                    window.location.assign(document.location.pathname);
                } else {
                    return this.handleError(err);
                }
            });
    }

    saveTopology(url: string, xml: string): Promise<string> {
        let xheaders = new HttpHeaders();
        xheaders = this.addXmlHeaders(xheaders);
        return this.http.put(url, xml, {headers: xheaders, responseType: 'text'})
            .toPromise()
            .then(() => xml)
            .catch((err: HttpErrorResponse) => {
                console.debug('TopologyService --> getTopology() --> \n  error: ' + err.status + ' ' + err.message);
                if (err.status === 401) {
                    window.location.assign(document.location.pathname);
                } else {
                    return this.handleError(err);
                }
            });
    }

    createTopology(name: string, xml: string): Promise<string> {
        let xheaders = new HttpHeaders();
        xheaders = this.addXmlHeaders(xheaders);
        let url = this.topologiesUrl + '/' + name;
        return this.http.put(url, xml, {headers: xheaders, responseType: 'text'})
            .toPromise()
            .then(() => xml)
            .catch((err: HttpErrorResponse) => {
                console.debug('TopologyService --> createTopology() --> \n  error: ' + err.status + ' ' + err.message);
                if (err.status === 401) {
                    window.location.assign(document.location.pathname);
                } else {
                    return this.handleError(err);
                }
            });
    }

    deleteTopology(href: string): Promise<string> {
        let headers = new HttpHeaders();
        headers = this.addJsonHeaders(headers);
        return this.http.delete(href, {
            headers: headers, responseType: 'text'
        })
            .toPromise()
            .then(response => response)
            .catch((err: HttpErrorResponse) => {
                console.debug('TopologyService --> getTopology() --> ' + href + '\n  error: ' + err.message);
                if (err.status === 401) {
                    window.location.assign(document.location.pathname);
                } else {
                    return this.handleError(err);
                }
            });
    }

    addJsonHeaders(headers: HttpHeaders): HttpHeaders {
        return this.addCsrfHeaders(headers.append('Accept', 'application/json')
            .append('Content-Type', 'application/json'));
    }

    addXmlHeaders(headers: HttpHeaders): HttpHeaders {
        return this.addCsrfHeaders(headers.append('Accept', 'application/xml')
            .append('Content-Type', 'application/xml'));
    }

    addCsrfHeaders(headers: HttpHeaders): HttpHeaders {
        return this.addXHRHeaders(headers.append('X-XSRF-Header', 'admin-ui'));
    }

    addXHRHeaders(headers: HttpHeaders): HttpHeaders {
        return headers.append('X-Requested-With', 'XMLHttpRequest');
    }

    selectedTopology(value: Topology) {
        this.selectedTopologySource.next(value);
    }

    changedTopology(value: string) {
        this.changedTopologySource.next(value);
    }

    private handleError(error: any): Promise<any> {
        console.error('An error occurred', error); // for demo purposes only
        return Promise.reject(error.message || error);
    }
}
