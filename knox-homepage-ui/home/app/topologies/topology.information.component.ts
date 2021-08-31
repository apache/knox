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
import {Component, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {MatGridListModule} from '@angular/material/grid-list';
import {BsModalComponent} from 'ng2-bs3-modal/ng2-bs3-modal';
import {HomepageService} from '../homepage.service';
import {TopologyInformation} from './topology.information';
import {Service} from './service';

@Component({
    selector: 'app-topologies-information',
    templateUrl: './topology.information.component.html',
    styleUrls: ['./topology.information.component.css'],
    providers: [HomepageService]
})

export class TopologyInformationsComponent implements OnInit {

    @ViewChild('apiServiceInformationModal')
    apiServiceInformationModal: BsModalComponent;

    topologies: TopologyInformation[];
    desiredTopologies: string[];
    selectedApiService : Service;

    setTopologies(topologies: TopologyInformation[]) {
        this.topologies = topologies;
        this.filterTopologies();
        for (let topology of topologies) {
            this['showTopology_' + topology.topology] = topology.pinned;
        }
    }

    toggleBoolean(propertyName: string) {
        this[propertyName] = !this[propertyName];
    }

    enableServiceText(enableServiceText: string) {
        this[enableServiceText] = true;
    }

    constructor(private homepageService: HomepageService, private route: ActivatedRoute) {
        this['showTopologies'] = true;
    }

    ngOnInit(): void {
        console.debug('TopologyInformationsComponent --> ngOnInit()');
        this.homepageService.getTopologies().then(topologies => this.setTopologies(topologies));
        this.route.queryParams.subscribe(params => {
            let topologiesParam = params['topologies'];
            console.debug('Topologies query param name = ' + topologiesParam)
            if (topologiesParam) {
                this.desiredTopologies = topologiesParam.split(',');
                this.filterTopologies();
            } else {
	        	    let profileName = params['profile'];
	            console.debug('Profile name = ' + profileName)
	            if (profileName) {
	            	    console.debug('Fetching profile information...');
	            	    this.homepageService.getProfile(profileName).then(profile => this.setDesiredTopologiesFromProfile(profile));
	            }
            }
        });
    }

    setDesiredTopologiesFromProfile(profile: JSON) {
      let topologiesInProfile = profile['topologies'];
      if (topologiesInProfile !== "") {
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
        this.apiServiceInformationModal.open('lg');
    }

}
