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
import {NgModule} from '@angular/core';
import {DataTableModule} from 'angular2-datatable';
import {BrowserModule} from '@angular/platform-browser';
import {HttpClientModule, HttpClientXsrfModule} from '@angular/common/http';
import {FormsModule} from '@angular/forms';
import {CustomFormsModule} from 'ng2-validation';
import {APP_BASE_HREF} from '@angular/common';

import {AppComponent} from './app.component';
import {TopologyService} from './topology.service';
import {ServiceDefinitionService} from './service-definition/servicedefinition.service';
import {ServiceDefinitionDetailComponent} from './service-definition/servicedefinition-detail.component';
import {GatewayVersionService} from './gateway-version.service';
import {GatewayVersionComponent} from './gateway-version.component';
import {TopologyComponent} from './topology.component';
import {TopologyDetailComponent} from './topology-detail.component';
import {XmlPipe} from './utils/xml.pipe';
import {JsonPrettyPipe} from './utils/json-pretty.pipe';
import {TabComponent} from './utils/tab.component';
import {TabsComponent} from './utils/tabs.component';

import {AceEditorModule} from 'ng2-ace-editor';
import {BsModalModule} from 'ng2-bs3-modal/ng2-bs3-modal';
import {ResourcetypesComponent} from './resourcetypes/resourcetypes.component';
import {ResourceTypesService} from './resourcetypes/resourcetypes.service';
import {ResourceComponent} from './resource/resource.component';
import {ResourceService} from './resource/resource.service';
import {DescriptorComponent} from './descriptor/descriptor.component';
import {ResourceDetailComponent} from './resource-detail/resource-detail.component';
import {ProviderConfigSelectorComponent} from './provider-config-selector/provider-config-selector.component';
import {NewDescWizardComponent} from './new-desc-wizard/new-desc-wizard.component';
import {ProviderConfigWizardComponent} from './provider-config-wizard/provider-config-wizard.component';

@NgModule({
    imports: [BrowserModule,
        HttpClientModule,
        HttpClientXsrfModule,
        FormsModule,
        CustomFormsModule,
        BsModalModule,
        AceEditorModule,
        DataTableModule
    ],
    declarations: [AppComponent,
        TopologyComponent,
        TopologyDetailComponent,
        ServiceDefinitionDetailComponent,
        GatewayVersionComponent,
        XmlPipe,
        JsonPrettyPipe,
        TabsComponent,
        TabComponent,
        ResourcetypesComponent,
        ResourceComponent,
        DescriptorComponent,
        ResourceDetailComponent,
        ProviderConfigSelectorComponent,
        NewDescWizardComponent,
        ProviderConfigWizardComponent
    ],
    providers: [TopologyService,
        ServiceDefinitionService,
        GatewayVersionService,
        ResourceComponent,
        ResourceTypesService,
        ResourceService,
        {provide: APP_BASE_HREF, useValue: '/'}
    ],
    bootstrap: [AppComponent,
        GatewayVersionComponent
    ]
})
export class AppModule {
}
