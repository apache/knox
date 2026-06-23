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
import './polyfills.ts';

import { enableProdMode, provideZoneChangeDetection } from '@angular/core';
import { environment } from './environments/environment';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { SessionInformationComponent } from './app/session-information/session.information.component';
import { GatewayVersionComponent } from './app/gateway-version/gateway-version.component';
import { AppComponent } from './app/app.component';

if (environment.production) {
  enableProdMode();
}

const bootstrapComponents = [
  SessionInformationComponent,
  GatewayVersionComponent,
  AppComponent
];

bootstrapComponents.forEach(component => {
  bootstrapApplication(component, {
    providers: [
      provideZoneChangeDetection(),
      provideHttpClient()
    ]
  }).catch(err => console.error(err));
});
