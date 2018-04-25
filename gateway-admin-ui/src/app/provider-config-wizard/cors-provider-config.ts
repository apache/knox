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

import {ValidationUtils} from "../utils/validation-utils";
import {WebAppSecurityContributor} from "./webappsec-contributor";

export class CORSProviderConfig extends WebAppSecurityContributor {

  public static TYPE: string = 'cors';

  public static ALLOW_GENERIC_REQUESTS: string = 'Allow generic requests';
  public static ALLOWED_ORIGINS: string        = 'Allowed origins';
  public static ALLOW_SUBDOMAINS: string       = 'Allow sub-domains';
  public static SUPPORTED_METHODS: string      = 'Supported HTTP methods';
  public static SUPPORTED_HEADERS: string      = 'Supported HTTP headers';
  public static EXPOSED_HEADERS: string        = 'Exposed HTTP headers';
  public static SUPPORTS_CREDS: string         = 'Supports credentials';
  public static MAX_AGE: string                = 'Access control max age';
  public static TAG_REQUESTS: string           = 'Request tagging';

  private static displayPropertyNames = [ CORSProviderConfig.ALLOW_GENERIC_REQUESTS,
                                          CORSProviderConfig.ALLOWED_ORIGINS,
                                          CORSProviderConfig.ALLOW_SUBDOMAINS,
                                          CORSProviderConfig.SUPPORTED_METHODS,
                                          CORSProviderConfig.SUPPORTED_HEADERS,
                                          CORSProviderConfig.EXPOSED_HEADERS,
                                          CORSProviderConfig.SUPPORTS_CREDS,
                                          CORSProviderConfig.MAX_AGE,
                                          CORSProviderConfig.TAG_REQUESTS
                                        ];

  private static displayPropertyNameBindings: Map<string, string> =
    new Map([
      [CORSProviderConfig.ALLOW_GENERIC_REQUESTS, 'cors.allowGenericHttpRequests'],
      [CORSProviderConfig.ALLOWED_ORIGINS,        'cors.allowOrigin'],
      [CORSProviderConfig.ALLOW_SUBDOMAINS,       'cors.allowSubdomains'],
      [CORSProviderConfig.SUPPORTED_METHODS,      'cors.supportedMethods'],
      [CORSProviderConfig.SUPPORTED_HEADERS,      'cors.supportedHeaders'],
      [CORSProviderConfig.EXPOSED_HEADERS,        'cors.exposedHeaders'],
      [CORSProviderConfig.SUPPORTS_CREDS,         'cors.supportsCredentials'],
      [CORSProviderConfig.MAX_AGE,                'cors.maxAge'],
      [CORSProviderConfig.TAG_REQUESTS,           'cors.tagRequests']
    ] as [string, string][]);


  constructor() {
    super();
    // Set default values
    this.setParam('cors.enabled', 'true');
    this.setParam(CORSProviderConfig.displayPropertyNameBindings.get(CORSProviderConfig.ALLOW_GENERIC_REQUESTS), 'true');
    this.setParam(CORSProviderConfig.displayPropertyNameBindings.get(CORSProviderConfig.ALLOWED_ORIGINS), '*');
    this.setParam(CORSProviderConfig.displayPropertyNameBindings.get(CORSProviderConfig.ALLOW_SUBDOMAINS), 'false');
    this.setParam(CORSProviderConfig.displayPropertyNameBindings.get(CORSProviderConfig.SUPPORTED_METHODS), 'GET,POST,HEAD,OPTIONS');
    this.setParam(CORSProviderConfig.displayPropertyNameBindings.get(CORSProviderConfig.SUPPORTED_HEADERS), '*');
    this.setParam(CORSProviderConfig.displayPropertyNameBindings.get(CORSProviderConfig.SUPPORTS_CREDS), 'true');
    this.setParam(CORSProviderConfig.displayPropertyNameBindings.get(CORSProviderConfig.MAX_AGE), '-1');
    this.setParam(CORSProviderConfig.displayPropertyNameBindings.get(CORSProviderConfig.TAG_REQUESTS), 'false');
  }

  getDisplayPropertyNames(): string[] {
    return CORSProviderConfig.displayPropertyNames;
  }

  getDisplayNamePropertyBinding(name: string): string {
    return CORSProviderConfig.displayPropertyNameBindings.get(name);
  }

  isValidParamValue(paramName: string): boolean {
    let isValid: boolean = true;

    let value = this.getParam(this.getDisplayNamePropertyBinding(paramName));
    switch (paramName) {
      case CORSProviderConfig.SUPPORTED_METHODS:
        if (value) {
          let methodList: string[] = value.split(',');
          for (let method of methodList) {
            isValid = isValid && ValidationUtils.isValidHTTPMethod(method.trim().toUpperCase());
          }
        }
        break;
      case CORSProviderConfig.ALLOW_GENERIC_REQUESTS:
      case CORSProviderConfig.ALLOW_SUBDOMAINS:
      case CORSProviderConfig.SUPPORTS_CREDS:
      case CORSProviderConfig.TAG_REQUESTS:
        if (value) {
          isValid = ValidationUtils.isValidBoolean(value);
        }
        break;
      case CORSProviderConfig.MAX_AGE:
        isValid = ValidationUtils.isValidSignedNumber(value);
        break;
      default:
        if (value) {
          isValid = ValidationUtils.isValidString(value);
        }
    }

    return isValid;
  }

}

