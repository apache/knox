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
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import Swal from 'sweetalert2/dist/sweetalert2.esm.all.js';

import { Subject, firstValueFrom } from 'rxjs';

import {ServiceDefinition} from '../model/servicedefinition';

 @Injectable({providedIn: 'root'})
export class ServiceDefinitionService {
    apiUrl = window.location.pathname.replace(new RegExp('admin-ui/.*'), 'api/v1/');
    serviceDefinitionsBaseUrl = this.apiUrl + 'servicedefinitions';
    selectedServiceDefinitionSource = new Subject<ServiceDefinition>();
    selectedServiceDefinition$ = this.selectedServiceDefinitionSource.asObservable();

    constructor(private http: HttpClient) {
    }

getServiceDefinitionXml(serviceDefinition: ServiceDefinition): Promise<string> {
        let headers = this.addXmlHeaders(new HttpHeaders());
        let url = this.serviceDefinitionsBaseUrl + '/' + serviceDefinition.service
            + '/' + serviceDefinition.role + '/' + serviceDefinition.version;
        return firstValueFrom(this.http.get(url, {headers: headers, responseType: 'text'}))
            .catch((err: HttpErrorResponse) => {
                console.debug('ServiceDefinitionService --> getServiceDefinitionXml() --> ' + url
                              + '\n  error: ' + err.message);
                if (err.status === 401) {
                    window.location.assign(document.location.pathname);
                }
                return this.handleError(err);
            });
    }

    saveNewServiceDefinition(xml: string): Promise<string> {
        let xheaders = this.addXmlHeaders(new HttpHeaders());
        let serviceDefintionXml = xml.replace('<serviceDefinitions>', '').replace('</serviceDefinitions>', '');
        return firstValueFrom(
            this.http.post(this.serviceDefinitionsBaseUrl, serviceDefintionXml, {headers: xheaders, responseType: 'text'}))
            .catch((err: HttpErrorResponse) => {
                console.debug('ServiceDefinitionService --> saveNewServiceDefinition() --> ' + this.serviceDefinitionsBaseUrl
                              + '\n  error: ' + err.status + ' ' + err.message);
                if (err.status === 401) {
                    window.location.assign(document.location.pathname);
                }
                return this.handleError(err);
            });
    }

    updateServiceDefinition(xml: string): Promise<string> {
        let xheaders = this.addXmlHeaders(new HttpHeaders());
        let serviceDefintionXml = xml.replace('<serviceDefinitions>', '').replace('</serviceDefinitions>', '');
        return firstValueFrom(this.http.put(this.serviceDefinitionsBaseUrl, serviceDefintionXml, {headers: xheaders, responseType: 'text'}))
            .catch((err: HttpErrorResponse) => {
                console.debug('ServiceDefinitionService --> updateServiceDefinition() --> ' + this.serviceDefinitionsBaseUrl
                              + '\n  error: ' + err.status + ' ' + err.message);
                if (err.status === 401) {
                    window.location.assign(document.location.pathname);
                }
                return this.handleError(err);
            });
    }

    deleteServiceDefinition(serviceDefinition: ServiceDefinition): Promise<string> {
        let xheaders = this.addXmlHeaders(new HttpHeaders());
        let url = this.serviceDefinitionsBaseUrl + '/' + serviceDefinition.service
            + '/' + serviceDefinition.role + '/' + serviceDefinition.version;
        return firstValueFrom(this.http.delete(url, {headers: xheaders, responseType: 'text'}))
            .catch((err: HttpErrorResponse) => {
                console.debug('ServiceDefinitionService --> deleteServiceDefinition() --> ' + url
                              + '\n  error: ' + err.status + ' ' + err.message);
                if (err.status === 401) {
                    window.location.assign(document.location.pathname);
                }
                return this.handleError(err);
            });
    }

    selectedServiceDefinition(value: ServiceDefinition) {
        this.selectedServiceDefinitionSource.next(value);
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
