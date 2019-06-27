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
import {Service} from '../resource/service';

export class Descriptor {
    discoveryType: string;
    discoveryAddress: string;
    discoveryUser: string;
    discoveryPassAlias: string;
    discoveryCluster: string;
    providerConfig: string;
    services: Service[];

    private dirty = false;

    // getServiceParamNames must not be static since it is used in a view
    // https://stackoverflow.com/questions/41857047/call-static-function-from-angular2-template
    // noinspection JSMethodCanBeStatic
    getServiceParamNames(service: Service): string[] {
        if (!service.params) {
            service.params = {};
        }
        return Object.getOwnPropertyNames(service.params);
    }

    // getServiceParamValue must not be static since it is used in a view
    // https://stackoverflow.com/questions/41857047/call-static-function-from-angular2-template
    // noinspection JSMethodCanBeStatic
    getServiceParamValue(service: Service, name: string): string {
        return service.params[name];
    }

    setProviderConfig(providerConfigRef: string) {
        if (providerConfigRef !== this.providerConfig) {
            this.providerConfig = providerConfigRef;
            this.setDirty();
        }
    }

    addService(name: string) {
        if (!this.services) {
            this.services = [];
        }
        let s = new Service();
        s.name = name;
        s.params = {};
        s.urls = [];
        this.services.push(s);
        this.setDirty();
    }

    addServiceParam(service: Service, name: string, value: string) {
        if (!service.params) {
            service.params = {};
        }
        service.params[name] = value;
        this.setDirty();
    }

    addServiceURL(service: Service, url: string) {
        if (!service.urls) {
            service.urls = [];
        }
        service.urls.push(url);
        this.setDirty();
    }

    onVersionChanged(service: Service) {
        if (!service.version || service.version.length === 0) {
            delete service.version;
        }
        this.setDirty();
    }

    setDirty() {
        this.dirty = true;
    }

    public isDirty(): boolean {
        return this.dirty;
    }
}
