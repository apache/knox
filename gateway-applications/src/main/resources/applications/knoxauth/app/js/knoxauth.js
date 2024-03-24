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

const loginPageSuffix = "/knoxauth/login.html";
const webssoURL = "/api/v1/websso?originalUrl=";

function getQueryParam(name) {
  return new URLSearchParams(window.location.search).get(name);
}

function isSameOrigin(a, b = window.location.href) {
  return new URL(a, b).origin === new URL(b).origin;
}

function redirect(redirectUrl) {
  window.location.replace(redirectUrl);
}

function unicodeBase64Encode(str) {
  return btoa(unescape(encodeURIComponent(str)));
}

function handleKeyPress(event) {
  if (event.keyCode === 13) {
    login();
  }
}

function login() {
  const pathname = window.location.pathname;
  const topologyContext = pathname.replace(loginPageSuffix, "");
  const loginURL = topologyContext + webssoURL;
  const form = document.forms[0];
  const username = form.username.value;
  const password = form.password.value;

  function doLogin() {
    const originalUrl = getQueryParam("originalUrl");
    const idpUrl = loginURL + originalUrl;

    const request = new XMLHttpRequest();
    request.open("POST", idpUrl, true);
    request.setRequestHeader("Authorization", "Basic " + unicodeBase64Encode(username + ":" + password));
    request.send(null);

    request.onreadystatechange = function() {
      if (request.readyState === XMLHttpRequest.DONE) {
        if ([0, 200, 204, 307, 303].includes(request.status)) {
          const redirectUrl = isSameOrigin(originalUrl) ? originalUrl : "redirecting.html?originalUrl=" + originalUrl;
          redirect(redirectUrl);
        } else {
          handleError(request);
        }
      }
    };
  }

  doLogin();
}

function handleError(request) {
  const errorBox = document.getElementById('errorBox');
  const signInLoading = document.getElementById('signInLoading');
  const signInButton = document.getElementById('signIn');
  errorBox.style.display = 'block';
  signInLoading.style.display = 'none';
  signInButton.removeAttribute('disabled');
  
  if (request.status === 401) {
    errorBox.querySelector('.errorMsg').textContent = "The username or password you entered is incorrect.";
  } else {
    errorBox.querySelector('.errorMsg').textContent = "Response from " + request.responseURL + " - " + request.status + ": " + request.statusText;
  }
}
