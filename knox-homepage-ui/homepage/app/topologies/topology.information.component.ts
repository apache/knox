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
import {Component, OnInit} from '@angular/core';
import {HomepageService} from '../homepage.service';
import {TopologyInformation} from './topology.information';
import {Service} from './service';

@Component({
    selector: 'app-topologies-information',
    template: `
        <hr/>
        <h3>Topology browsing</h3>
        <ng-container *ngFor="let topology of topologies">
            <div>
              <span [class]="'clickable inline-glyph
              glyhpicon glyphicon-' + (this['showTopology_' + topology.topology] ? 'minus' : 'plus')"
              (click)="toggleBoolean('showTopology_' + topology.topology)"></span>
              <span (click)="toggleBoolean('showTopology_' + topology.topology)"><strong>{{topology.topology}}</strong></span>
            </div>
            <div class="table-responsive" *ngIf="this['showTopology_' + topology.topology]">
                <table class="table table-hover" [mfData]="topology.services.service" #mf="mfDataTable" [mfRowsOnPage]="5">
                    <tbody>
                        <tr *ngFor="let service of mf.data">
                            <td>
                                <span class="inline-glyph glyphicon glyphicon-info-sign btn btn-xs"
                                title="{{service.description}}"
                                data-toggle="tooltip"></span>
                                {{service.shortDesc}}
                            </td>
                            <td>
                                <a href="{{service.serviceUrl}}">{{service.serviceUrl}}</a>
                            </td>
                        </tr>
                    </tbody>
		            <tfoot>
		                <tr>
		                    <td colspan="4">
		                        <mfBootstrapPaginator [rowsOnPageSet]="[5,10,15]"></mfBootstrapPaginator>
		                    </td>
		                </tr>
		            </tfoot>
                </table>
            </div>
        </ng-container>
        <hr />
    `,
    providers: [HomepageService]
})

export class TopologyInformationsComponent implements OnInit {

    topologies: TopologyInformation[];

    setTopologies(topologies: TopologyInformation[]) {
        this.topologies = topologies;
        for (let topology of topologies) {
            this['showTopology_' + topology.topology] = false;
        }
    }

    toggleBoolean(propertyName: string) {
        this[propertyName] = !this[propertyName];
    }

    constructor(private homepageService: HomepageService) {}

    ngOnInit(): void {
        console.debug('TopologyInformationsComponent --> ngOnInit()');
        this.homepageService.getTopologies().then(topologies => this.setTopologies(topologies));
    }

}
