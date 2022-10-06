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
import { FormControl, Validators } from "@angular/forms";
import { TokenData } from "./token.data.model";
import { TssStatusData } from "./tssStatus.model";


@Component({
  selector: 'app-token-gen',
  templateUrl: './token-gen.component.html',
  providers: []
})
export class TokenGen implements OnInit{
  tssStatusMessageLevel: 'info' | 'warning' | 'error';
  tssStatusMessage: string;

  loginPageSuffix = "token-gen/index.html";
  knoxtokenURL = "knoxtoken/api/v1/token";
  tssStatusURL = 'knoxtoken/api/v1/token/getTssStatus'
  
  //These are not global for some reason
  pathname = window.location.pathname;
  topologyContext = this.pathname.replace(this.loginPageSuffix, "");;
  baseURL = this.topologyContext.substring(0, this.topologyContext.lastIndexOf('/'));
  tokenURL = this.topologyContext + this.knoxtokenURL;

  // Data coming from input fields
  comment = new FormControl('', Validators.maxLength(255));
  maxLifeTimeSec = new FormControl('');
  isLifespanInputEnabled = new FormControl(false);
  lifespanDays = new FormControl(0, [
    Validators.min(0),
    Validators.max(3650)
    // TODO egesz legyen
  ]);
  lifespanHours = new FormControl(1, [
    Validators.min(0),
    Validators.max(23)
    // TODO egesz legyen
  ]);
  lifespanMins = new FormControl(0, [
    Validators.min(0),
    Validators.max(59)
    // TODO egesz legyen
  ]);
  impersonation = new FormControl('', Validators.maxLength(255));

  // Data from token generating request
  accessToken: string;
  accessPasscode: string;
  expiry: string;
  user: string;
  homepageURL: string;
  targetURL: string;

  // Data from TokenStateService status request
  tssStatus: TssStatusData;

  constructor(private http: HttpClient) {
    this.tssStatusMessageLevel = 'info';
    this.tssStatusMessage = '';
    // TODO maybe init the whole thing
    this.tssStatus = new TssStatusData();
    this.tssStatus.lifespanInputEnabled = false;
    this.tssStatus.impersonationEnabled = false;
  }

  ngOnInit(): void {
    console.log("token URL: " + this.tokenURL);
    this.setTokenStateServiceStatus();
  }

  generateToken() {
    if(this.isFromValid()){
      // errorBox hide
      // resultBox hide
      var params = new HttpParams();
      if(this.isLifespanInputEnabled.value){
        params = params.append('lifespan', 'P' + this.lifespanDays + "DT" + this.lifespanHours + "H" + this.lifespanMins + "M");
      }
      if(this.comment.value){
        params = params.append('comment', this.comment.value);
      }
      if(this.impersonation.value){
        params = params.append('doAs', this.impersonation.value);
      }
      // XMLHttpRequest or ActiveXObject 3rd parameter true in open means async
      this.http.get<TokenData>(this.tokenURL, { params: params })
      .subscribe(responseData => {
        // status is 0 when your html file containing the script is opened in the browser via the file scheme.
        // result show
        this.accessToken = responseData.access_token;
        let decodedToken = this.b64DecodeUnicode(this.accessToken.split(".")[1]);
        let jwtJson = JSON.parse(decodedToken);
        this.user = jwtJson.sub;
        if(responseData.passcode){
          // Passcode show
          this.accessPasscode = responseData.passcode;
        }
        this.expiry = new Date(responseData.expires_in).toLocaleString();
        // change dom to link
        this.homepageURL = this.baseURL + responseData.homepage_url;
        this.targetURL = window.location.protocol + "//" + window.location.host + "/" + this.baseURL + responseData.target_url;
        console.log(responseData);
      }, (e: HttpErrorResponse) => {
        // show error box
        // if status code is 401 reload????
        // else error message in error box
        console.log(e.status);
      });    
    }
  }

  setTokenStateServiceStatus() {
    var tssStatusRequestURL = this.topologyContext + this.tssStatusURL;
    this.http.get<TssStatusData>(tssStatusRequestURL)
    .subscribe(responseData => {
      this.tssStatus = responseData;
      if (this.tssStatus.tokenManagementEnabled) {
        if(this.tssStatus.allowedTssForTokengen){
          if(this.tssStatus.actualTssBackend == 'AliasBasedTokenStateService'){
            // display warning (msg3)
            this.setTssMessage('warning','Token management backend is configured to store tokens in keystores. This is only valid non-HA environments!');
          }else{
            // display info (msg4)
            this.setTssMessage('info','Token management backend is properly configured for HA and production deployments.');
          }
        }else{
            // diasble token management(msg2)
            this.setTssMessage('error','Token management backend initialization failed, token generation disabled.');
            // TODO disable
        }
      } else {
        // diasble token management(msg1)
        this.setTssMessage('error','Token management is disabled');
        // TODO disable
      }
    }, (e: HttpErrorResponse) => {    
      console.log(e.status);
    });  
  }

  private setTssMessage(level: 'info' | 'warning' | 'error', message: string){
    this.tssStatusMessageLevel = level;
    this.tssStatusMessage = message;
  }

  private isFromValid(): boolean {
    if(this.comment.errors || this.lifespanDays.errors || this.lifespanHours.errors || this.lifespanMins.errors || this.impersonation.errors){
      return false;
    }
    return true;
  }

  private b64DecodeUnicode(str) {
    // Going backwards: from bytestream, to percent-encoding, to original string.
    return decodeURIComponent(atob(str).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));
  }

}