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
import {TokenManagementService} from './token.management.service';
import {KnoxToken} from './knox.token';
import {MatTableDataSource} from '@angular/material/table';
import {MatPaginator} from '@angular/material/paginator';
import {MatSort} from '@angular/material/sort';
import {MatSlideToggleChange} from '@angular/material/slide-toggle';
import {SelectionModel} from '@angular/cdk/collections';

@Component({
    selector: 'app-token-management',
    templateUrl: './token.management.component.html',
    styleUrls: ['../assets/token-management-ui.css'],
    providers: [TokenManagementService]
})

export class TokenManagementComponent implements OnInit {

    tokenGenerationPageURL = window.location.pathname.replace(new RegExp('token-management/.*'), 'token-generation/index.html');

    userName: string;
    canSeeAllTokens: boolean;
    knoxTokens: MatTableDataSource<KnoxToken> = new MatTableDataSource();
    selection = new SelectionModel<KnoxToken>(true, []);
    allKnoxTokens: KnoxToken[];

    displayedColumns = ['select', 'tokenId', 'issued', 'expires', 'userName', 'impersonated', 'knoxSso', 'comment', 'metadata', 'actions'];
    @ViewChild('knoxTokensPaginator') paginator: MatPaginator;
    @ViewChild('knoxTokensSort') sort: MatSort = new MatSort();

    showDisabledKnoxSsoCookies: boolean;
    showMyTokensOnly: boolean;

    showDisableSelectedTokensButton: boolean;
    showEnableSelectedTokensButton: boolean;
    showRevokeSelectedTokensButton: boolean;

    constructor(private tokenManagementService: TokenManagementService) {
        this.showDisabledKnoxSsoCookies = true;
        let isMatch: (record: KnoxToken, filter: String) => boolean = (record, filter) => {
          let normalizedFilter = filter.trim().toLocaleLowerCase();
          let matchesTokenId = record.tokenId.toLocaleLowerCase().includes(normalizedFilter);
          let matchesComment = record.metadata.comment && record.metadata.comment.toLocaleLowerCase().includes(normalizedFilter);
          let matchesUserName = record.metadata.userName.toLocaleLowerCase().includes(normalizedFilter);
          let matchesCreatedBy = record.metadata.createdBy && record.metadata.createdBy.toLocaleLowerCase().includes(normalizedFilter);
          let matchesCustomMetadata = false;
          if (record.metadata.customMetadataMap) {
            for (let entry of Array.from(Object.entries(record.metadata.customMetadataMap))) {
	          if (entry[0].toLocaleLowerCase().includes(normalizedFilter) || entry[1].toLocaleLowerCase().includes(normalizedFilter)) {
                  matchesCustomMetadata = true;
                  break;
              }
            }
          } else {
            matchesCustomMetadata = true; // nothing to match
          }

          return matchesTokenId || matchesComment || matchesCustomMetadata || matchesUserName || matchesCreatedBy;
        };

        this.knoxTokens.filterPredicate = function (record, filter) {
	      return isMatch(record, filter);
        };

        this.knoxTokens.sortingDataAccessor = (item, property) => {
           switch(property) {
             case 'metadata.comment': return item.metadata.comment;
             case 'metadata.username': return item.metadata.userName;
             case 'metadata.createdBy': return item.metadata.createdBy;
             default: return item[property];
           }
        };
    }

    onChangeShowDisabledCookies(value: MatSlideToggleChange) {
        this.showDisabledKnoxSsoCookies = value.checked;
        this.actualizeTokensToDisplay();
    }

    onChangeShowMyTokensOnly(value: MatSlideToggleChange) {
        this.showMyTokensOnly = value.checked;
        this.actualizeTokensToDisplay();
    }

    ngOnInit(): void {
        console.debug('TokenManagementComponent --> ngOnInit()');
        this.tokenManagementService.getSessionInformation()
            .then(sessionInformation => {
	          this.canSeeAllTokens = sessionInformation.canSeeAllTokens;
	          this.setUserName(sessionInformation.user);
            });
    }

    setUserName(userName: string) {
        this.userName = userName;
        this.fetchKnoxTokens();
    }

    userCanSeeAllTokens(): boolean {
        return this.canSeeAllTokens;
    }

    fetchKnoxTokens(): void {
        this.tokenManagementService.getKnoxTokens(this.userName, this.canSeeAllTokens)
            .then(tokens => this.updateTokens(tokens));
    }

    private isMyToken(token: KnoxToken): boolean {
	    return token.metadata.userName === this.userName || (token.metadata.createdBy && token.metadata.createdBy === this.userName);
    }

    private isDisabledKnoxSsoCookie(token: KnoxToken): boolean {
        return token.metadata.knoxSsoCookie && !token.metadata.enabled;
    }

    private updateTokens(tokens: KnoxToken[]): void {
        this.allKnoxTokens = tokens;
        this.selection.clear();
        this.showHideBatchOperations();
        this.actualizeTokensToDisplay();
    }

    private actualizeTokensToDisplay(): void {
	    let tokensToDisplay = this.allKnoxTokens;

        if (!this.showDisabledKnoxSsoCookies) {
            tokensToDisplay = tokensToDisplay.filter(token => !this.isDisabledKnoxSsoCookie(token));
        }

        if (this.showMyTokensOnly) {
             tokensToDisplay = tokensToDisplay.filter(token => this.isMyToken(token));
         }

         this.knoxTokens.data = tokensToDisplay;

         setTimeout(() => {
            this.knoxTokens.paginator = this.paginator;
            this.knoxTokens.sort = this.sort;
         });
    }

    disableToken(tokenId: string) {
        this.tokenManagementService.setEnabledDisabledFlag(false, tokenId).then((response: string) => this.fetchKnoxTokens());
    }

    disableSelectedTokens(): void {
        this.tokenManagementService.setEnabledDisabledFlagsInBatch(false, this.getSelectedTokenIds())
            .then((response: string) => this.fetchKnoxTokens());
    }

    private getSelectedTokenIds(): string[] {
        let selectedTokenIds = [] as string[];
        this.selection.selected.forEach(token => selectedTokenIds.push(token.tokenId));
        return selectedTokenIds;
    }

    enableToken(tokenId: string) {
        this.tokenManagementService.setEnabledDisabledFlag(true, tokenId).then((response: string) => this.fetchKnoxTokens());
    }

    enableSelectedTokens(): void {
        this.tokenManagementService.setEnabledDisabledFlagsInBatch(true, this.getSelectedTokenIds())
            .then((response: string) => this.fetchKnoxTokens());
    }

    revokeToken(tokenId: string) {
        this.tokenManagementService.revokeToken(tokenId).then((response: string) => this.fetchKnoxTokens());
    }

    revokeSelectedTokens() {
        this.tokenManagementService.revokeTokensInBatch(this.getSelectedTokenIds()).then((response: string) => this.fetchKnoxTokens());
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

    getExpirationColor(expiration: number): string {
        return this.isTokenExpired(expiration) ? 'red' : 'green';
    }

    getCustomMetadataArray(knoxToken: KnoxToken): [string, string][] {
      let mdMap = new Map();
      if (knoxToken.metadata.customMetadataMap) {
        mdMap = new Map(Object.entries(knoxToken.metadata.customMetadataMap));
      }

      return Array.from(mdMap);
    }

    isKnoxSsoCookie(knoxToken: KnoxToken): boolean {
      return knoxToken.metadata.knoxSsoCookie;
    }

    isDisabledKnoxSSoCookie(knoxToken: KnoxToken): boolean {
      return this.isKnoxSsoCookie(knoxToken) && !knoxToken.metadata.enabled;
    }

    applyFilter(filterValue: string) {
        filterValue = filterValue.trim(); // Remove whitespace
        filterValue = filterValue.toLowerCase(); // Datasource defaults to lowercase matches
        this.knoxTokens.filter = filterValue;
    }

    /** Whether the number of selected elements matches the total number of rows. */
    isAllSelected(): boolean {
      const numSelected = this.selection.selected.length;
      const numRows = this.knoxTokens.filteredData.length;
      return numSelected === numRows;
    }

    /** Selects all rows if they are not all selected; otherwise clear selection. */
    masterToggle(): void {
        if (this.isAllSelected()) {
            this.selection.clear();
        } else {
            this.knoxTokens.filteredData.forEach(row => {
	            if (!this.isDisabledKnoxSsoCookie(row)) {
                    this.selection.select(row);
                }
            });
        }
        this.showHideBatchOperations();
    }

    onRowSelectionChange(knoxToken: KnoxToken): void {
        this.selection.toggle(knoxToken);
        this.showHideBatchOperations();
    }

    showHideBatchOperations() {
	    if (this.selection.isEmpty()) {
		    this.showDisableSelectedTokensButton = false;
            this.showEnableSelectedTokensButton = false;
            this.showRevokeSelectedTokensButton = false;
	    } else {
            this.showDisableSelectedTokensButton = true;
            this.showEnableSelectedTokensButton = true;
            this.showRevokeSelectedTokensButton = this.selectionHasZeroKnoxSsoCookie(); // KnoxSSO cookies must not be revoked
	    }
    }

    private selectionHasZeroKnoxSsoCookie(): boolean {
        return this.selection.selected.every(token => !token.metadata.knoxSsoCookie);
    }

}
