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

    displayedColumns = ['tokenId', 'issued', 'expires', 'userName', 'impersonated', 'knoxSso', 'comment', 'metadata', 'actions'];
    @ViewChild('knoxTokensPaginator') paginator: MatPaginator;
    @ViewChild('knoxTokensSort') sort: MatSort = new MatSort();

    toggleBoolean(propertyName: string) {
        this[propertyName] = !this[propertyName];
    }

    enableServiceText(enableServiceText: string) {
        this[enableServiceText] = true;
    }

    constructor(private tokenManagementService: TokenManagementService) {
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

    ngOnInit(): void {
        console.debug('TokenManagementComponent --> ngOnInit()');
        this.tokenManagementService.getUserName().then(userName => this.setUserName(userName));
    }

    setUserName(userName: string) {
        this.userName = userName;
        this.fetchKnoxTokens();
    }

    fetchKnoxTokens(): void {
        this.tokenManagementService.getKnoxTokens(this.userName).then(tokens => this.knoxTokens.data = tokens);
        setTimeout(() => {
            this.knoxTokens.paginator = this.paginator;
            this.knoxTokens.sort = this.sort;
        });
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

    isKnoxSSoCookie(knoxToken: KnoxToken): boolean {
      return knoxToken.metadata.knoxSsoCookie;
    }

    applyFilter(filterValue: string) {
        filterValue = filterValue.trim(); // Remove whitespace
        filterValue = filterValue.toLowerCase(); // Datasource defaults to lowercase matches
        this.knoxTokens.filter = filterValue;
    }

}
