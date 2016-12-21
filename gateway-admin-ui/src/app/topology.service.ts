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
import { Injectable }    from '@angular/core';
import { Headers, Http } from '@angular/http';

import 'rxjs/add/operator/toPromise';
import { Subject }    from 'rxjs/Subject';
import { Observable }    from 'rxjs/Observable';

import { Topology } from './topology';

@Injectable()
export class TopologyService {

    apiUrl = '/gateway/admin/api/v1/';
    topologiesUrl = this.apiUrl + 'topologies';
    selectedTopologySource = new Subject<Topology>();
    selectedTopology$ = this.selectedTopologySource.asObservable();
    changedTopologySource = new Subject<string>();
    changedTopology$ = this.changedTopologySource.asObservable();

    constructor(private http: Http) { }

    getTopologies(): Promise<Topology[]> {
        let headers = new Headers();
        this.addJsonHeaders(headers);
        return this.http.get(this.topologiesUrl, {
            headers: headers
        } )
            .toPromise()
            .then(response => response.json().topologies.topology as Topology[])
            .catch(this.handleError);
    }

    getTopology(href : string): Promise<string> {
        let headers = new Headers();
        this.addXmlHeaders(headers);
        return this.http.get(href, {
            headers: headers
        } )
            .toPromise()
            .then(response => response.text())
            .catch(this.handleError);

    }

    saveTopology(url: string, xml : string): Promise<string> {
        let xheaders = new Headers();
        this.addXmlHeaders(xheaders);
        this.addCsrfHeaders(xheaders);
        return this.http
            .put(url, xml, {headers: xheaders})
            .toPromise()
            .then(() => xml)
            .catch(this.handleError);

    }

    createTopology(name: string, xml : string): Promise<string> {
        let xheaders = new Headers();
        this.addXmlHeaders(xheaders);
        this.addCsrfHeaders(xheaders);
        let url = this.topologiesUrl + "/" + name;
        return this.http
            .put(url, xml, {headers: xheaders})
            .toPromise()
            .then(() => xml)
            .catch(this.handleError);
    }

    deleteTopology(href: string): Promise<string> {
        let headers = new Headers();
        this.addJsonHeaders(headers);
        this.addCsrfHeaders(headers);
        return this.http.delete(href, {
            headers: headers
        } )
            .toPromise()
            .then(response => response.text())
            .catch(this.handleError);
    }

    addJsonHeaders(headers: Headers) {
        headers.append('Accept', 'application/json');
        headers.append('Content-Type', 'application/json');
    }

    addXmlHeaders(headers: Headers) {
        headers.append('Accept', 'application/xml');
        headers.append('Content-Type', 'application/xml');
    }

    addCsrfHeaders(headers: Headers) {
        headers.append('X-XSRF-Header', 'admin-ui');
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