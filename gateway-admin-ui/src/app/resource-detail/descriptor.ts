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
import {Service} from "../resource/service";


export class Descriptor {
    discoveryAddress: string;
    discoveryUser: string;
    discoveryPassAlias: string;
    discoveryCluster: string;
    providerConfig: string;
    services: Service[];

    private dirty: boolean = false;

    getServiceParamKeys(service: Service): string[] {
        let result = [];
        for(let key in service.params){
            if (service.params.hasOwnProperty(key)){
                result.push(key);
            }
        }
        return result;
    }

    getServiceParamValue(service: Service, name: string): string {
       return  service.params[name];
    }

    setProviderConfig(providerConfigRef: string) {
      console.debug('Descriptor --> setProviderConfig() --> ' + providerConfigRef);
      if (providerConfigRef !== this.providerConfig) {
        this.providerConfig = providerConfigRef;
        this.setDirty();
      }
    }

    setDirty() {
        this.dirty = true;
    }

    public isDirty(): boolean {
        return this.dirty;
    }

}