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
import { HttpHeaders, HttpClient} from '@angular/common/http';
import 'rxjs/add/operator/toPromise';
import { Subject } from 'rxjs/Subject';
import { Resource } from './resource';


@Injectable()
export class ResourceService {

    apiUrl = '/gateway/manager/api/v1/';
    providersUrl = this.apiUrl + 'providerconfig';
    descriptorsUrl = this.apiUrl + 'descriptors';
    topologiesUrl = this.apiUrl + 'topologies';

    selectedResourceTypeSource = new Subject<string>();
    selectedResourceType$ = this.selectedResourceTypeSource.asObservable();

    selectedResourceSource = new Subject<Resource>();
    selectedResource$ = this.selectedResourceSource.asObservable();

    changedResourceSource = new Subject<string>();
    changedResource$ = this.changedResourceSource.asObservable();

    constructor(private http: HttpClient) { }

    getResources(resType: string): Promise<Resource[]> {
        switch(resType) {
          case 'Provider Configurations': {
            return this.getProviderConfigResources();
          }
          case 'Descriptors': {
            return this.getDescriptorResources();
          }
          case 'Topologies': {
            return this.getTopologyResources();
          }
        }
    }

    getProviderConfigResources(): Promise<Resource[]> {
        let headers = new HttpHeaders();
        headers = this.addJsonHeaders(headers);
        return this.http.get(this.providersUrl, { headers: headers })
                        .toPromise()
                        .then(response => response['items'] as Resource[])
                        .catch(this.handleError);
    }

    getDescriptorResources(): Promise<Resource[]> {
        let headers = new HttpHeaders();
        headers = this.addJsonHeaders(headers);
        return this.http.get(this.descriptorsUrl, { headers: headers })
                        .toPromise()
                        .then(response => response['items'] as Resource[])
                        .catch(this.handleError);
    }

    getTopologyResources(): Promise<Resource[]> {
        let headers = new HttpHeaders();
        headers = this.addJsonHeaders(headers);
        return this.http.get(this.topologiesUrl, { headers: headers })
                        .toPromise()
                        .then(response => response['topologies'].topology as Resource[])
                        .catch(this.handleError);
    }

    getResource(resType: string, res : Resource): Promise<string> {
        let headers = new HttpHeaders();
        headers = (resType === 'Topologies') ? this.addXmlHeaders(headers) : this.addHeaders(headers, res.name);

        return this.http.get(res.href, { headers: headers, responseType: 'text' })
                        .toPromise()
                        .then(response => { return response; })
                        .catch(this.handleError);
    }

    saveResource(url: string, xml : string): Promise<string> {
        let xheaders = new HttpHeaders();
        this.addXmlHeaders(xheaders);
        this.addCsrfHeaders(xheaders);
        return this.http.put(url, xml, {headers: xheaders})
                        .toPromise()
                        .then(() => xml)
                        .catch(this.handleError);
    }

    createResource(name: string, xml : string): Promise<string> {
        let xheaders = new HttpHeaders();
        this.addXmlHeaders(xheaders);
        this.addCsrfHeaders(xheaders);
        let url = this.topologiesUrl + "/" + name;
        return this.http.put(url, xml, {headers: xheaders})
                        .toPromise()
                        .then(() => xml)
                        .catch(this.handleError);
    }

    deleteResource(href: string): Promise<string> {
        let headers = new HttpHeaders();
        this.addJsonHeaders(headers);
        this.addCsrfHeaders(headers);
        return this.http.delete(href, { headers: headers } )
                        .toPromise()
                        .then(response => response)
                        .catch(this.handleError);
    }

    addHeaders(headers: HttpHeaders, resName: string): HttpHeaders {
        let ext = resName.split('.').pop();
        switch(ext) {
          case 'xml': {
              headers = this.addXmlHeaders(headers);
              break;
          }
          case 'json': {
              headers = this.addJsonHeaders(headers);
              break;
          }
          case 'yaml':
          case 'yml': {
              headers = this.addTextPlainHeaders(headers);
              break;
          }
        }
        return headers;
    }

    addTextPlainHeaders(headers: HttpHeaders) {
        return headers.append('Accept', 'text/plain')
                      .append('Content-Type', 'text/plain');
    }

    addJsonHeaders(headers: HttpHeaders): HttpHeaders {
        return headers.append('Accept', 'application/json')
                      .append('Content-Type', 'application/json');
    }

    addXmlHeaders(headers: HttpHeaders): HttpHeaders {
        return headers.append('Accept', 'application/xml')
                      .append('Content-Type', 'application/xml');
    }

    addCsrfHeaders(headers: HttpHeaders): HttpHeaders {
        return headers.append('X-XSRF-Header', 'admin-ui');
    }

    selectedResourceType(value: string) {
        this.selectedResourceTypeSource.next(value);
    }

    selectedResource(value: Resource) {
        this.selectedResourceSource.next(value);
    }

    changedResource(value: string) {
        this.changedResourceSource.next(value);
    }


    public getResourceDisplayName(res: Resource): string {
        if (res.name) {
            let idx = res.name.lastIndexOf('.');
            return idx >0 ? res.name.substring(0, res.name.lastIndexOf('.')) : res.name;
        } else {
            return null;
        }
    }

    private handleError(error: any): Promise<any> {
        console.error('An error occurred', error); // for demo purposes only
        return Promise.reject(error.message || error);
    }

    private logHeaders(headers: HttpHeaders) {
        console.log('Header count: ' + headers.keys().length);
        headers.keys().forEach(key => {
            console.log('Header: ' + key + '=' + headers.get(key));
        });
    }

}

