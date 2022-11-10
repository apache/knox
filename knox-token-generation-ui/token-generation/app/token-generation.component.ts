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
import { HttpClient} from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import Swal from 'sweetalert2';
import { TokenResultData, TssStatusData } from './token-generation.models';
import { TokenGenService } from './token-generation.service';

@Component({
  selector: 'app-token-generation',
  templateUrl: './token-generation.component.html',
  providers: []
})
export class TokenGenerationComponent implements OnInit {
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

  // Data coming from Token generating request
  tokenResultData: TokenResultData;

  // Data coming from TokenStateService status request
  tssStatus: TssStatusData;

  constructor(private http: HttpClient, private tokenGenService: TokenGenService) {
    this.tssStatusMessageLevel = 'info';
    this.tssStatusMessage = '';
    this.requestErrorMessage = '';
    this.hasResult = false;

    this.tssStatus = new TssStatusData();
    this.tokenResultData = new TokenResultData();
  }

  ngOnInit(): void {
    this.setTokenStateServiceStatus();
  }

  generateToken() {
    if (this.tokenGenFrom.valid && this.tssStatus.tokenManagementEnabled && this.tssStatus.allowedTssForTokengen) {
      if (this.isMaximumLifetimeExceeded()) {
        Swal.fire(
          {
            title: 'Warning',
            text: `You are trying to generate a token with a lifetime that exceeds the configured maximum.
            In this case the generated token's lifetime will be limited to the configured maximum.`,
            icon: 'warning',
            reverseButtons: true,
            confirmButtonText: 'Generate token anyway',
            confirmButtonColor: '#e64942',
            showCancelButton: true,
            cancelButtonText: '<span style="color: #555;">Adjust request lifetime</span>',
            cancelButtonColor: '#efefef'
          }).then((willGenerateToken) => {
              if (willGenerateToken.isConfirmed) {
                this.requestToken();
              }
          });
      } else {
        this.requestToken();
      }
    }
  }

  setTokenStateServiceStatus() {
    this.tokenGenService.getTokenStateServiceStatus()
    .then(tssStatus => {
      this.tssStatus = tssStatus;
      this.decideTssMessage();
    })
    .catch((errorMessage) => {
      this.requestErrorMessage = errorMessage;
    });
  }

  copyTextToClipboard(elementId) {
    let toBeCopied = document.getElementById(elementId).innerText.trim();
    const tempTextArea = document.createElement('textarea');
    tempTextArea.value = toBeCopied;
    document.body.appendChild(tempTextArea);
    tempTextArea.select();
    document.execCommand('copy');
    document.body.removeChild(tempTextArea);
    Swal.fire({
      text: 'Copied to clipboard!',
      timer: 1000,
      showConfirmButton: false,
    });
  }

  private requestToken() {
    this.requestErrorMessage = '';
    this.hasResult = false;

    let params = {
      lifespanInputEnabled: this.tssStatus.lifespanInputEnabled,
      comment: this.comment.value,
      impersonation: this.impersonation.value,
      lifespanDays: this.lifespanDays.value,
      lifespanHours: this.lifespanHours.value,
      lifespanMins: this.lifespanMins.value
    };

    this.tokenGenService.getGeneratedTokenData(params)
    .then(tokenResultData => {
      this.hasResult = true;
      this.tokenResultData = tokenResultData;
    })
    .catch((errorMessage) => {
      this.requestErrorMessage = errorMessage;
    });
  }

  private allZeroValidator(): ValidatorFn {
    return (formGroup: FormGroup) => {
      if (
        formGroup.get('lifespanDays').value === 0 &&
        formGroup.get('lifespanHours').value === 0 &&
        formGroup.get('lifespanMins').value === 0
        ) {
          return {allZero: true};
      }
      return null;
    };
  }

  private isMaximumLifetimeExceeded() {
    if (this.tssStatus.maximumLifetimeSeconds === -1) {
      return false;
    }
    if (!this.tssStatus.lifespanInputEnabled) {
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
      if (this.tssStatus.allowedTssForTokengen) {
        if (this.tssStatus.actualTssBackend === 'AliasBasedTokenStateService') {
          this.setTssMessage('warning', `Token management backend is configured to store tokens in keystores.
            This is only valid non-HA environments!`);
        } else {
          this.setTssMessage('info', 'Token management backend is properly configured for HA and production deployments.');
        }
      } else {
        this.setTssMessage('error', 'Token management backend initialization failed, token generation disabled.');
      }
    } else {
      this.setTssMessage('error', 'Token management is disabled');
    }
  }

  private setTssMessage(level: 'info' | 'warning' | 'error', message: string) {
    this.tssStatusMessageLevel = level;
    this.tssStatusMessage = message;
  }
}
