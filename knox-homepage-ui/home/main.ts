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
import { enableProdMode, importProvidersFrom } from '@angular/core';
import { environment } from './environments/environment';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { APP_BASE_HREF } from '@angular/common';
import { MatGridListModule } from '@angular/material/grid-list';
import { provideHttpClient } from '@angular/common/http';
import { SessionInformationComponent } from './app/sessionInformation/session.information.component';
import { GeneralProxyInformationComponent } from './app/generalProxyInformation/general.proxy.information.component';
import { TopologyInformationsComponent } from './app/topologies/topology.information.component';

import './polyfills.ts';

if (environment.production) {
  enableProdMode();
}

const bootstrapComponents = [
  SessionInformationComponent,
  GeneralProxyInformationComponent,
  TopologyInformationsComponent
];

bootstrapComponents.forEach(component => {
  bootstrapApplication(component, {
    providers: [
      importProvidersFrom(MatGridListModule),
      provideHttpClient(),
      provideRouter([]),
      {
        provide: APP_BASE_HREF,
        useValue: window['base-href'] || '/'
      }
    ]
  }).catch(err => console.error(err));
});
