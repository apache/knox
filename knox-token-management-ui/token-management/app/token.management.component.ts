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
import {KnoxToken} from './knox.token';

@Component({
    selector: 'app-token-management',
    templateUrl: './token.management.component.html',
    providers: [TokenManagementService]
})

export class TokenManagementComponent implements OnInit {

    tokenGenerationPageURL = window.location.pathname.replace(new RegExp('token-management/.*'), 'tokengen/index.html');

    userName: string;
    knoxTokens: KnoxToken[];

    toggleBoolean(propertyName: string) {
        this[propertyName] = !this[propertyName];
    }

    enableServiceText(enableServiceText: string) {
        this[enableServiceText] = true;
    }

    constructor(private tokenManagementService: TokenManagementService) {
    }

    ngOnInit(): void {
        console.debug('TokenManagementComponent --> ngOnInit()');
        this.tokenManagementService.getUserName().then(userName => this.setUserName(userName));
    }

    setUserName(userName: string) {
        this.userName = userName;
        this.fetchKnoxTokens();
    }

    fetchKnoxTokens(): void {
        this.tokenManagementService.getKnoxTokens(this.userName).then(tokens => this.knoxTokens = tokens);
    }

    disableToken(tokenId: string) {
        this.tokenManagementService.setEnabledDisabledFlag(false, tokenId).then((response: string) => this.fetchKnoxTokens());
    }

    enableToken(tokenId: string) {
        this.tokenManagementService.setEnabledDisabledFlag(true, tokenId).then((response: string) => this.fetchKnoxTokens());
    }

    revokeToken(tokenId: string) {
        this.tokenManagementService.revokeToken(tokenId).then((response: string) => this.fetchKnoxTokens());
    }

    gotoTokenGenerationPage() {
        window.open(this.tokenGenerationPageURL, '_blank');
    }

    formatDateTime(dateTime: number) {
        return new Date(dateTime).toLocaleString();
    }

    isTokenExpired(expiration: number): boolean {
        return Date.now() > expiration;
    }

}
