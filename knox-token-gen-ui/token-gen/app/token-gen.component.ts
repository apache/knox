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
import { FormControl, FormGroup, Validators } from "@angular/forms";
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
  requestErrorMessage: string;
  hasResult: boolean;

  loginPageSuffix = "token-gen/index.html";
  knoxtokenURL = "knoxtoken/api/v1/token";
  tssStatusURL = 'knoxtoken/api/v1/token/getTssStatus'
  
  //These are not global for some reason? -> changed and then sent request might be problem
  pathname = window.location.pathname;
  topologyContext = this.pathname.replace(this.loginPageSuffix, "");;
  baseURL = this.topologyContext.substring(0, this.topologyContext.lastIndexOf('/'));
  tokenURL = this.topologyContext + this.knoxtokenURL;

  // Data coming from input fields
  // TODO cross field validation
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
  }, )

  // Data from token generating request
  // TODO might put them in class
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
    this.tssStatus = new TssStatusData();
    this.tssStatus.lifespanInputEnabled = true;
    this.hasResult = false;
  }

  ngOnInit(): void {
    this.setTokenStateServiceStatus();
  }

  generateToken() {
    if(this.isFromValid()){
      this.requestErrorMessage = '';
      this.hasResult = false;
      var params = new HttpParams();
      if(this.tssStatus.lifespanInputEnabled){
        params = params.append('lifespan', 'P' + this.tokenGenFrom.get('lifespanDays').value + "DT" + this.tokenGenFrom.get('lifespanHours').value + "H" + this.tokenGenFrom.get('lifespanMins').value + "M");
      }
      if(this.tokenGenFrom.get('comment').value){
        params = params.append('comment', this.tokenGenFrom.get('comment').value);
      }
      if(this.tokenGenFrom.get('impersonation').value){
        params = params.append('doAs', this.tokenGenFrom.get('impersonation.value').value);
      }
      // TODO look up these: XMLHttpRequest or ActiveXObject 3rd parameter true in open means async
      this.http.get<TokenData>(this.tokenURL, { params: params })
      .subscribe(responseData => {
        // TODO status is 0 when your html file containing the script is opened in the browser via the file scheme.
        this.hasResult = true;
        this.accessToken = responseData.access_token;
        let decodedToken = this.b64DecodeUnicode(this.accessToken.split(".")[1]);
        let jwtJson = JSON.parse(decodedToken);
        this.user = jwtJson.sub;
        if(responseData.passcode){
          // TODO show jwtPasscodeTokenLabel?? why only the label -> beacuse then the passcode '' anyway but i can make the wholething dissapear maybe
          this.accessPasscode = responseData.passcode;
        } else {
          // TODO hide jwtPasscodeTokenLabel?? why only the label
        }
        this.expiry = new Date(responseData.expires_in).toLocaleString();
        this.homepageURL = this.baseURL + responseData.homepage_url;
        this.targetURL = window.location.protocol + "//" + window.location.host + "/" + this.baseURL + responseData.target_url;
      }, (e: HttpErrorResponse) => {
        this.requestErrorMessage = "Response from " + e.url + " - " + e.status + ": " + e.statusText;
        /** TODO
         * if (request.responseText) {
         *   errorMsg += " (" + request.responseText + ")";
         * }
         */ 
        // TODO if status code == 401 reload???? else error message in error box
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
            this.setTssMessage('warning','Token management backend is configured to store tokens in keystores. This is only valid non-HA environments!');
          }else{
            this.setTssMessage('info','Token management backend is properly configured for HA and production deployments.');
          }
        }else{
            // disable token management(msg2)
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

  private notZeroValidator(controls: FormControl[]) {
    // TODO
  }

  private setTssMessage(level: 'info' | 'warning' | 'error', message: string){
    this.tssStatusMessageLevel = level;
    this.tssStatusMessage = message;
  }

  // TODO if validators work mightn not need a separate function
  private isFromValid(): boolean {
    if(this.tokenGenFrom.valid){
      return true;
    }
    return false;
  }

  private b64DecodeUnicode(str) {
    // Going backwards: from bytestream, to percent-encoding, to original string.
    return decodeURIComponent(atob(str).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));
  }

}