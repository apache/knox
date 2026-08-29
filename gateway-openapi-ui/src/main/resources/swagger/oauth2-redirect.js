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
"use strict";

function getResponseParams() {
  var queryString;

  if (/code|token|error/.test(window.location.hash)) {
    queryString = window.location.hash.substring(1).replace("?", "&");
  } else {
    queryString = location.search.substring(1);
  }

  var pairs = queryString.split("&");
  pairs.forEach(function (value, index, allPairs) {
    allPairs[index] = '"' + value.replace("=", '":"') + '"';
  });

  return queryString ? JSON.parse("{" + pairs.join() + "}", function (key, value) {
    return key === "" ? value : decodeURIComponent(value);
  }) : {};
}

function isAuthorizationCodeFlow(oauth2) {
  var flow = oauth2.auth.schema.get("flow");
  return flow === "accessCode"
    || flow === "authorizationCode"
    || flow === "authorization_code";
}

function reportAuthorizationCodeError(oauth2, response) {
  var oauthErrorMsg;

  if (response.error) {
    oauthErrorMsg = "[" + response.error + "]: "
      + (response.error_description ? response.error_description + ". " : "no accessCode received from the server. ")
      + (response.error_uri ? "More info: " + response.error_uri : "");
  }

  oauth2.errCb({
    authId: oauth2.auth.name,
    source: "auth",
    level: "error",
    message: oauthErrorMsg || "[Authorization failed]: no accessCode received from the server"
  });
}

function run() {
  var oauth2 = window.opener.swaggerUIRedirectOauth2;
  var sentState = oauth2.state;
  var redirectUrl = oauth2.redirectUrl;
  var response = getResponseParams();
  var isValid = response.state === sentState;

  if (!isAuthorizationCodeFlow(oauth2) || oauth2.auth.code) {
    oauth2.callback({
      auth: oauth2.auth,
      token: response,
      isValid: isValid,
      redirectUrl: redirectUrl
    });
    window.close();
    return;
  }

  if (!isValid) {
    oauth2.errCb({
      authId: oauth2.auth.name,
      source: "auth",
      level: "warning",
      message: "Authorization may be unsafe, passed state was changed in server Passed state wasn't returned from auth server"
    });
  }

  if (response.code) {
    if (isValid) {
      delete oauth2.state;
      oauth2.auth.code = response.code;
      oauth2.callback({auth: oauth2.auth, redirectUrl: redirectUrl});
    }
  } else {
    reportAuthorizationCodeError(oauth2, response);
  }

  window.close();
}

if (document.readyState !== "loading") {
  run();
} else {
  document.addEventListener("DOMContentLoaded", function () {
    run();
  });
}
