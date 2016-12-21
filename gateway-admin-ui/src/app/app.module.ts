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
import { NgModule }      from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpModule }    from '@angular/http';
import { FormsModule } from '@angular/forms';

import { AppComponent }  from './app.component';
import {TopologyService} from "./topology.service";
import {GatewayVersionService} from "./gateway-version.service";
import {GatewayVersionComponent} from "./gateway-version.component";
import {TopologyComponent} from "./topology.component";
import {TopologyDetailComponent} from "./topology-detail.component";
import {XmlPipe} from "./utils/xml.pipe";
import {JsonPrettyPipe} from "./utils/json-pretty.pipe";
import { TabComponent } from './utils/tab.component';
import { TabsComponent } from './utils/tabs.component';

import { AceEditorDirective } from 'ng2-ace-editor'; 
import { Ng2Bs3ModalModule } from 'ng2-bs3-modal/ng2-bs3-modal'

@NgModule({
  imports: [ BrowserModule,
    HttpModule,
    FormsModule,
    Ng2Bs3ModalModule
    ],
  declarations: [ AppComponent,
    TopologyComponent,
      TopologyDetailComponent,
    GatewayVersionComponent,
    AceEditorDirective,
    XmlPipe,
    JsonPrettyPipe,
    TabsComponent,
    TabComponent ],
  providers: [ TopologyService,
    GatewayVersionService ],
  bootstrap: [ AppComponent,
    GatewayVersionComponent]
})
export class AppModule { }
