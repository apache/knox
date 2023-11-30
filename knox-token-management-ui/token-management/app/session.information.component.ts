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
import {TokenManagementService} from './token.management.service';
import {SessionInformation} from './session.information';

@Component({
    selector: 'app-session-information',
    templateUrl: './session.information.component.html',
    providers: [TokenManagementService]
})

export class SessionInformationComponent implements OnInit {

    sessionInformation: SessionInformation;
    logoutSupported = true;

    constructor(private tokenManagementService: TokenManagementService) {
        this['showSessionInformation'] = true;
    }

    getUser() {
        if (this.sessionInformation) {
          return this.sessionInformation.user;
        } else {
          console.debug('SessionInformationComponent --> getUser() --> dr.who');
          return 'dr.who';
        }
    }

    ngOnInit(): void {
        console.debug('SessionInformationComponent --> ngOnInit() --> ');
        this.tokenManagementService.getSessionInformation()
            .then(sessionInformation => this.setSessionInformation(sessionInformation));
    }

    private setSessionInformation(sessionInformation: SessionInformation) {
	    this.sessionInformation = sessionInformation;
        console.debug('SessionInformationComponent --> setSessionInformation() --> ' + this.sessionInformation.user);
    }

}
