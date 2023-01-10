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

@Component({
    selector: 'app-token-management',
    templateUrl: './token.management.component.html',
    styleUrls: ['../assets/token-management-ui.css'],
    providers: [TokenManagementService]
})

export class TokenManagementComponent implements OnInit {

    tokenGenerationPageURL = window.location.pathname.replace(new RegExp('token-management/.*'), 'token-generation/index.html');

    userName: string;
    knoxTokens: MatTableDataSource<KnoxToken> = new MatTableDataSource();
    doAsKnoxTokens: MatTableDataSource<KnoxToken> = new MatTableDataSource();
    impersonationEnabled: boolean;

    displayedColumns = ['tokenId', 'issued', 'expires', 'comment', 'metadata', 'actions'];
    @ViewChild('ownPaginator') paginator: MatPaginator;
    @ViewChild('ownSort') sort: MatSort = new MatSort();

    impersonationDisplayedColumns = ['impersonation.tokenId', 'impersonation.issued', 'impersonation.expires', 'impersonation.comment',
                                     'impersonation.metadata', 'impersonation.user.name'];
    @ViewChild('impersonationPaginator') impersonationPaginator: MatPaginator;
    @ViewChild('impersonationSort') impersonationSort: MatSort = new MatSort();

    toggleBoolean(propertyName: string) {
        this[propertyName] = !this[propertyName];
    }

    enableServiceText(enableServiceText: string) {
        this[enableServiceText] = true;
    }

    constructor(private tokenManagementService: TokenManagementService) {
        let isMatch: (record: KnoxToken, filter: String, impersonated: boolean) => boolean = (record, filter, impersonated) => {
          let normalizedFilter = filter.trim().toLocaleLowerCase();
          let matchesTokenId = record.tokenId.toLocaleLowerCase().includes(normalizedFilter);
          let matchesComment = record.metadata.comment && record.metadata.comment.toLocaleLowerCase().includes(normalizedFilter);
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

          let matchesImpersonatedUserName = false;  // doAs username should be checked only if impersonation is enabled
          if (impersonated) {
              matchesImpersonatedUserName = record.metadata.userName.toLocaleLowerCase().includes(normalizedFilter);
          }

          return matchesTokenId || matchesComment || matchesCustomMetadata || matchesImpersonatedUserName;
        };

        this.knoxTokens.filterPredicate = function (record, filter) {
	      return isMatch(record, filter, false);
        };

        this.doAsKnoxTokens.filterPredicate = function (record, filter) {
          return isMatch(record, filter, true);
        };

        this.knoxTokens.sortingDataAccessor = (item, property) => {
           switch(property) {
             case 'metadata.comment': return item.metadata.comment;
             default: return item[property];
           }
        };

        this.doAsKnoxTokens.sortingDataAccessor = (item, property) => {
           let normalizedPropertyName = property.replace('impersonation.', '');
           switch(normalizedPropertyName) {
             case 'metadata.comment': return item.metadata.comment;
             case 'metadata.username': return item.metadata.userName;
             default: return item[normalizedPropertyName];
           }
        };
    }

    ngOnInit(): void {
        console.debug('TokenManagementComponent --> ngOnInit()');
        this.tokenManagementService.getUserName().then(userName => this.setUserName(userName));
        this.tokenManagementService.getImpersonationEnabled()
            .then(impersonationEnabled => this.impersonationEnabled = impersonationEnabled === 'true');
    }

    setUserName(userName: string) {
        this.userName = userName;
        this.fetchAllKnoxTokens();
    }

    fetchAllKnoxTokens(): void {
        this.fetchKnoxTokens(false);
        this.fetchKnoxTokens(true);
    }

    fetchKnoxTokens(impersonated: boolean): void {
        this.tokenManagementService.getKnoxTokens(this.userName, impersonated)
            .then(tokens => this.populateTokens(impersonated, tokens));
    }

    populateTokens(impersonated: boolean, tokens: KnoxToken[]) {
        if (impersonated) {
            this.doAsKnoxTokens.data = tokens;
            setTimeout(() => {
                this.doAsKnoxTokens.paginator = this.impersonationPaginator;
                this.doAsKnoxTokens.sort = this.impersonationSort;
            });
        } else {
            this.knoxTokens.data = tokens;
            setTimeout(() => {
                this.knoxTokens.paginator = this.paginator;
                this.knoxTokens.sort = this.sort;
            });
        }
    }

    disableToken(tokenId: string) {
        this.tokenManagementService.setEnabledDisabledFlag(false, tokenId).then((response: string) => this.fetchAllKnoxTokens());
    }

    enableToken(tokenId: string) {
        this.tokenManagementService.setEnabledDisabledFlag(true, tokenId).then((response: string) => this.fetchAllKnoxTokens());
    }

    revokeToken(tokenId: string) {
        this.tokenManagementService.revokeToken(tokenId).then((response: string) => this.fetchAllKnoxTokens());
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

    isImpersonationEnabled(): boolean {
        return this.impersonationEnabled;
    }

    getCustomMetadataArray(knoxToken: KnoxToken): [string, string][] {
      let mdMap = new Map();
      if (knoxToken.metadata.customMetadataMap) {
        mdMap = knoxToken.metadata.customMetadataMap;
      }
      return Array.from(Object.entries(mdMap));
    }

    applyFilter(impersonated: boolean, filterValue: string) {
        filterValue = filterValue.trim(); // Remove whitespace
        filterValue = filterValue.toLowerCase(); // Datasource defaults to lowercase matches
        if (impersonated) {
            this.doAsKnoxTokens.filter = filterValue;
        } else {
            this.knoxTokens.filter = filterValue;
        }
    }

}
