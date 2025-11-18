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
import { Component, OnInit, ViewChildren, QueryList } from '@angular/core';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatPaginatorModule, MatPaginator } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute } from '@angular/router';
import { HomepageService } from '../service/homepage.service';
import { TopologyInformation } from '../model/topology.information';
import { Service } from '../model/service';
import { CommonModule } from '@angular/common';
import { MatGridListModule } from '@angular/material/grid-list';
import { ApiserviceDialogComponent } from '../apiservice-dialog/apiservice-dialog.component';
import { UiserviceDialogComponent } from '../uiservice-dialog/uiservice-dialog.component';
@Component({
    selector: 'app-topologies-information',
    templateUrl: './topology.information.component.html',
    styleUrls: ['./topology.information.component.css'],
    providers: [HomepageService],
    imports: [CommonModule, MatGridListModule, MatTooltipModule, MatIconModule, MatTableModule, MatPaginatorModule]
})

export class TopologyInformationsComponent implements OnInit {

    topologies: TopologyInformation[];
    desiredTopologies: string[];
    selectedApiService: Service;
    selectedGroupService: Service;
    filteredServiceUrls: string[];

    displayedApiColumns: string[] = ['description', 'urls'];
    private topologyDataSources = new Map<string, MatTableDataSource<Service>>();
    @ViewChildren(MatPaginator) allPaginators!: QueryList<MatPaginator>;

    constructor(
        private homepageService: HomepageService,
        private route: ActivatedRoute,
        private dialog: MatDialog
    ) {
        this['showTopologies'] = true;
    }

    setTopologies(topologies: TopologyInformation[]) {
        this.topologies = topologies;
        this.filterTopologies();

        this.topologyDataSources.clear();
        this.createDataSources();

        for (let topology of topologies) {
            this['showTopology_' + topology.topology] = topology.pinned;
        }

        setTimeout(() => this.linkPaginatorsToData(), 50);
    }

    private createDataSources() {
        if (!this.topologies) {
            return;
        }

        this.topologies.forEach(topology => {
            const dataSource = new MatTableDataSource<Service>();
            if (topology.apiServices && topology.apiServices.service) {
                dataSource.data = topology.apiServices.service;
            }
            this.topologyDataSources.set(topology.topology, dataSource);
        });
    }

    ngAfterViewInit() {
        this.linkPaginatorsToData();

        this.allPaginators.changes.subscribe(() => {
            setTimeout(() => this.linkPaginatorsToData(), 50);
        });
    }

    private linkPaginatorsToData() {
        if (!this.topologies || !this.allPaginators) {
            return;
        }

        let currentPaginatorIndex = 0;

        this.topologies.forEach(topology => {
            const shouldShowTable = topology.apiServicesViewVersion === 'v1' &&
                topology.apiServices?.service &&
                topology.apiServices.service.length > 0 &&
                this['showTopology_' + topology.topology];

            if (shouldShowTable) {
                const paginatorInstance = this.allPaginators.toArray()[currentPaginatorIndex];
                const dataSource = this.topologyDataSources.get(topology.topology);

                if (paginatorInstance && dataSource) {
                    dataSource.paginator = paginatorInstance;
                    currentPaginatorIndex++;
                }
            }
        });
    }

    getApiServicesDataSource(topology: TopologyInformation): MatTableDataSource<Service> {
        return this.topologyDataSources.get(topology.topology);
    }

    toggleBoolean(propertyName: string) {
        this[propertyName] = !this[propertyName];
    }

    enableServiceText(enableServiceText: string) {
        this[enableServiceText] = true;
    }

    ngOnInit(): void {
        console.debug('TopologyInformationsComponent --> ngOnInit()');
        this.homepageService.getTopologies().then(topologies => this.setTopologies(topologies));
        this.route.queryParams.subscribe(params => {
            let topologiesParam = params['topologies'];
            console.debug('Topologies query param name = ' + topologiesParam);
            if (topologiesParam) {
                this.desiredTopologies = topologiesParam.split(',');
                this.filterTopologies();
            } else {
                let profileName = params['profile'];
                console.debug('Profile name = ' + profileName);
                if (profileName) {
                    console.debug('Fetching profile information...');
                    this.homepageService.getProfile(profileName).then(profile => this.setDesiredTopologiesFromProfile(profile));
                }
            }
        });
    }

    setDesiredTopologiesFromProfile(profile: JSON) {
        let topologiesInProfile = profile['topologies'];
        if (topologiesInProfile !== '') {
            this.desiredTopologies = topologiesInProfile.split(',');
            this.filterTopologies();
        }
    }

    filterTopologies() {
        if (this.topologies && this.desiredTopologies && this.desiredTopologies.length > 0) {
            console.debug('Filtering topologies...');
            let filteredTopologies = [];
            for (let desiredTopology of this.desiredTopologies) {
                for (let topology of this.topologies) {
                    if (topology.topology === desiredTopology) {
                        filteredTopologies.push(topology);
                    }
                }
            }
            this.topologies = filteredTopologies;

        }
    }

    openApiServiceInformationModal(apiService: Service) {
        this.selectedApiService = apiService;
    }

    openGroupServiceInformationModal(groupService: Service) {
        this.selectedGroupService = groupService;
        this.filteredServiceUrls = this.selectedGroupService.serviceUrls;

    }

    getServiceUrlHostAndPort(serviceUrl: string): string {
        let hostStart = serviceUrl.indexOf('host=');
        if (hostStart > 0) {
            return ' - ' + serviceUrl.slice(hostStart).replace('host=', '').replace('&port=', ':')
                .replace('https://', '').replace('http://', '');
        }
        return '';
    }

    filterServiceUrls(filterText: string): void {
        if (filterText === '') {
            this.filteredServiceUrls = this.selectedGroupService.serviceUrls;
        } else {
            this.filteredServiceUrls = this.selectedGroupService.serviceUrls.filter(serviceUrl => serviceUrl.includes(filterText));
        }
    }

    openApiServiceDialog(service: any): void {
        this.dialog.open(ApiserviceDialogComponent, {
            width: '600px',
            data: service
        });
    }

    openGroupServiceDialog(service: any): void {
        this.dialog.open(UiserviceDialogComponent, {
            width: '800px',
            data: service
        });
    }
}
