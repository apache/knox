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
import {SessionInformation} from './session.information';

@Component({
    selector: 'app-session-information',
    templateUrl: './session.information.component.html',
    providers: [HomepageService]
})

export class SessionInformationComponent implements OnInit {

    sessionInformation: SessionInformation;
    logoutSupported = true;

    constructor(private homepageService: HomepageService) {
        this['showSessionInformation'] = true;
    }

    getUser() {
        if (this.sessionInformation) {
          console.debug('SessionInformationComponent --> getUser() --> ' + this.sessionInformation.user);
          return this.sessionInformation.user;
        }
        console.debug('SessionInformationComponent --> getUser() --> dr.who');

        return 'dr.who';
    }

    getLogoutUrl() {
        if (this.sessionInformation) {
            console.debug('SessionInformationComponent --> getLogoutUrl() --> ' + this.sessionInformation.logoutUrl);
            return this.sessionInformation.logoutUrl;
        }
        return null;
    }

    getLogoutPageUrl() {
        if (this.sessionInformation) {
            console.debug('SessionInformationComponent --> getLogoutPageUrl() --> ' + this.sessionInformation.logoutPageUrl);
            return this.sessionInformation.logoutPageUrl;
        }
        return null;
    }

    logout() {
        console.debug('SessionInformationComponent --> attempting logout() --> ');
        if (this.sessionInformation) {
            if (!this.logoutSupported) {
                window.alert('Logout for the configured is IDP not supported.\nPlease close all browser windows to logout.');
            }
            else {
                this.homepageService.logout(this.getLogoutUrl())
                                        .then(() => window.location.assign(this.sessionInformation.logoutPageUrl));
            }
        }
    }

    ngOnInit(): void {
        console.debug('SessionInformationComponent --> ngOnInit() --> ');
        this.homepageService.getSessionInformation()
                            .then(sessionInformation => this.setSessonInformation(sessionInformation));
        console.debug('SessionInformationComponent --> ngOnInit() --> ' + this.sessionInformation);
    }

    setSessonInformation(sessionInformation: SessionInformation) {
        this.sessionInformation = sessionInformation;
        if (this.getLogoutUrl() == null) {
            this.logoutSupported = false;
        }
    }
}
