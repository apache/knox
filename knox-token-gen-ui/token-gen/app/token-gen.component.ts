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
import { HttpClient, HttpErrorResponse, HttpParams } from "@angular/common/http";
import { Component, OnInit } from "@angular/core";
import { FormControl, FormGroup, ValidatorFn, Validators } from "@angular/forms";
import * as _swal from 'sweetalert';
import { SweetAlert } from 'sweetalert/typings/core';
const swal: SweetAlert = _swal as any;
import { TokenData } from "./token.data.model";
import { TssStatusData } from "./tssStatus.model";


@Component({
  selector: 'app-token-gen',
  templateUrl: './token-gen.component.html',
  providers: []
})
export class TokenGen implements OnInit{
  readonly baseURL: string;
  readonly tokenURL: string;
  readonly tssStatusRequestURL: string;
  
  tssStatusMessageLevel: 'info' | 'warning' | 'error';
  tssStatusMessage: string;
  requestErrorMessage: string;
  hasResult: boolean;

  // Data coming from the form
  tokenGenFrom = new FormGroup({
    comment : new FormControl('', Validators.maxLength(255)),
    lifespanDays : new FormControl(0, [
      Validators.min(0),
      Validators.max(3650),
      Validators.required,
      Validators.pattern('^[0-9]*$')
    ]),
    lifespanHours : new FormControl(1, [
      Validators.min(0),
      Validators.max(23),
      Validators.required,
      Validators.pattern('^[0-9]*$')
    ]),
    lifespanMins : new FormControl(0, [
      Validators.min(0),
      Validators.max(59),
      Validators.required,
      Validators.pattern('^[0-9]*$')
    ]),
    impersonation : new FormControl('', Validators.maxLength(255))
  }, this.allZeroValidator());

  get comment() { return this.tokenGenFrom.get('comment'); }
  get lifespanDays() { return this.tokenGenFrom.get('lifespanDays'); }
  get lifespanHours() { return this.tokenGenFrom.get('lifespanHours'); }
  get lifespanMins() { return this.tokenGenFrom.get('lifespanMins'); }
  get impersonation() { return this.tokenGenFrom.get('impersonation'); }

  // Data coming from token generating request
  accessToken: string;
  accessPasscode: string;
  expiry: string;
  user: string;
  homepageURL: string;
  targetURL: string;

  // Data coming from TokenStateService status request
  tssStatus: TssStatusData;

  constructor(private http: HttpClient) {
    const loginPageSuffix = "token-gen/index.html";
    const knoxtokenURL = "knoxtoken/api/v1/token";
    const tssStatusURL = 'knoxtoken/api/v1/token/getTssStatus';

    let topologyContext = window.location.pathname.replace(loginPageSuffix, "");
    let temporaryURL = topologyContext.substring(0, topologyContext.lastIndexOf('/'));
    this.baseURL = temporaryURL.substring(0, temporaryURL.lastIndexOf('/') + 1);    
    this.tokenURL = topologyContext + knoxtokenURL;
    this.tssStatusRequestURL = topologyContext + tssStatusURL;

    this.tssStatusMessageLevel = 'info';
    this.tssStatusMessage = '';
    this.requestErrorMessage = '';
    this.hasResult = false;

    this.tssStatus = new TssStatusData()
  }

  ngOnInit(): void {
    this.setTokenStateServiceStatus();
  }

  generateToken() {
    if(this.tokenGenFrom.valid && this.tssStatus.tokenManagementEnabled && this.tssStatus.allowedTssForTokengen){
      if(this.isMaximumLifetimeExceeded()){
        swal({
          title: "Warning",
          text: "You are trying to generate a token with a lifetime that exceeds the configured maximum. In this case the generated token's lifetime will be limited to the configured maximum.",
          icon: "warning",
          buttons: ["Adjust request lifetime", "Generate token anyway"],
          dangerMode: true
        })
        .then((willGenerateToken) => {
          if (willGenerateToken) {
            this.requestToken();
          }
        });
      }else{
        this.requestToken();
      }
    }
  }

  setTokenStateServiceStatus() {
    this.http.get<TssStatusData>(this.tssStatusRequestURL)
    .subscribe(responseData => {
      this.tssStatus = responseData;
      this.decideTssMessage();
    }, (e: HttpErrorResponse) => {    
      console.log(e.status);
    });  
  }

  // From angular 9 there's cdk clipboard
  copyTextToClipboard(elementId) {
    let toBeCopied = document.getElementById(elementId).innerText.trim();
    const tempTextArea = document.createElement('textarea');
    tempTextArea.value = toBeCopied;
    document.body.appendChild(tempTextArea);
    tempTextArea.select();
    document.execCommand('copy');
    document.body.removeChild(tempTextArea);
    swal("Copied to clipboard!", {buttons: [false],timer: 1000});
  }

  private requestToken() {
    this.requestErrorMessage = '';
    this.hasResult = false;

    let params = new HttpParams();
    if (this.tssStatus.lifespanInputEnabled) {
      params = params.append('lifespan', 'P' + this.lifespanDays.value + "DT" + this.lifespanHours.value + "H" + this.lifespanMins.value + "M");
    }
    if (this.comment.value) {
      params = params.append('comment', this.comment.value);
    }
    if (this.impersonation.value) {
      params = params.append('doAs', this.impersonation.value);
    }

    this.http.get<TokenData>(this.tokenURL, { params: params })
      .subscribe(responseData => {
        this.hasResult = true;
        this.accessToken = responseData.access_token;
        let decodedToken = this.b64DecodeUnicode(this.accessToken.split(".")[1]);
        let jwtJson = JSON.parse(decodedToken);
        this.user = jwtJson.sub;
        this.accessPasscode = responseData.passcode;
        this.expiry = new Date(responseData.expires_in).toLocaleString();
        this.homepageURL = this.baseURL + responseData.homepage_url;
        this.targetURL = window.location.protocol + "//" + window.location.host + "/" + this.baseURL + responseData.target_url;
      }, (e: HttpErrorResponse) => {
        this.requestErrorMessage = "Response from " + e.url + " - " + e.status + ": " + e.statusText;
        if(e.status === 401){
          // TODO reload
        } else {
          if(e.error){
            this.requestErrorMessage += " (" + e.error + ")"
          }
        }
      }
    );
  }

  private allZeroValidator(): ValidatorFn {
    return (formGroup: FormGroup) => {
      if(
        formGroup.get('lifespanDays').value == 0 &&
        formGroup.get('lifespanHours').value == 0 &&
        formGroup.get('lifespanMins').value == 0
        ){
          return {allZero: true};
      }
        return null;
     }
  }

  private isMaximumLifetimeExceeded() {
    if (this.tssStatus.maximumLifetimeSeconds == -1) {
      return false;
    }
    let daysInSeconds = this.lifespanDays.value * 86400;
    let hoursInSeconds = this.lifespanHours.value * 3600;
    let minsInSeconds = this.lifespanMins.value * 60;
    let suppliedLifetime = daysInSeconds + hoursInSeconds + minsInSeconds;
    return suppliedLifetime > this.tssStatus.maximumLifetimeSeconds;
  }

  private decideTssMessage() {
    if (this.tssStatus.tokenManagementEnabled) {
      if(this.tssStatus.allowedTssForTokengen){
        if(this.tssStatus.actualTssBackend == 'AliasBasedTokenStateService'){
          this.setTssMessage('warning','Token management backend is configured to store tokens in keystores. This is only valid non-HA environments!');
        }else{
          this.setTssMessage('info','Token management backend is properly configured for HA and production deployments.');
        }
      }else{
        this.setTssMessage('error','Token management backend initialization failed, token generation disabled.');
      }
    } else {
      this.setTssMessage('error','Token management is disabled');
    }
  }

  private setTssMessage(level: 'info' | 'warning' | 'error', message: string){
    this.tssStatusMessageLevel = level;
    this.tssStatusMessage = message;
  }

  private b64DecodeUnicode(string) {
    // Going backwards: from bytestream, to percent-encoding, to original string.
    return decodeURIComponent(atob(string).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));
  }

}