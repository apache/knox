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

var loginURL = "/gateway/knoxsso/api/v1/websso?originalUrl=";
var logoutURL = "/WebServices/LogOff";
var userAgent = navigator.userAgent.toLowerCase();
var firstLogIn = true;

function get(name){
   if(name=(new RegExp('[?&]'+encodeURIComponent(name)+'=([^&]*)')).exec(location.search))
      return decodeURIComponent(name[1]);
}

var login = function() {
    var form = document.forms[0];
    var username = form.username.value;
    var password = form.password.value;
    var _login = function(){
    var originalUrl = get("originalUrl");
    var idpUrl = loginURL + originalUrl;
      //Instantiate HTTP Request
        var request = ((window.XMLHttpRequest) ? new XMLHttpRequest() : new ActiveXObject("Microsoft.XMLHTTP"));
        request.open("POST", loginURL + originalUrl, true);
        request.setRequestHeader("Authorization", "Basic " + btoa(username + ":" + password))
        request.send(null);

      //Process Response
        request.onreadystatechange = function(){
            if (request.readyState == 4) {
                if (request.status==200 || request.status==204 || request.status==307 || request.status==303) {
                  // window.location.replace(originalUrl);
                  // window.location = originalUrl;
                  try { window.location.replace(originalUrl); } 
                  catch(e) { window.location = originalUrl; }
                }
                else {
                    // if (navigator.userAgent.toLowerCase().indexOf("firefox") != -1){
                    //     logoff();
                    // }
                  if (request.status==401) {
                    $('#errorBox').show();
                    $('#signInLoading').hide();
                    $('#signIn').removeAttr('disabled');
                    $('#errorBox .errorMsg').text("The username or password you entered is incorrect.");
                  }
                }
            }
        }
    }

    var userAgent = navigator.userAgent.toLowerCase();
    if (userAgent.indexOf("firefox") != -1){ //TODO: check version number
        if (firstLogIn) _login();
        else logoff(_login);
    }
    else{
        _login();
    }

    if (firstLogIn) firstLogIn = false;
}

var logoff = function(callback){

    if (userAgent.indexOf("msie") != -1) {
        document.execCommand("ClearAuthenticationCache");
    }
    else if (userAgent.indexOf("firefox") != -1){ //TODO: check version number

        var request1 = new XMLHttpRequest();
        var request2 = new XMLHttpRequest();

      //Logout. Tell the server not to return the "WWW-Authenticate" header
        request1.open("GET", logoutURL + "?prompt=false", true);
        request1.send("");
        request1.onreadystatechange = function(){
            if (request1.readyState == 4) {

              //Login with dummy credentials to clear the auth cache
                request2.open("GET", logoutURL, true, "logout", "logout");
                request2.send("");

                request2.onreadystatechange = function(){
                    if (request2.readyState == 4) {
                        if (callback!=null) callback.call();
                    }
                }
            }
        }
    }
    else {
        var request = ((window.XMLHttpRequest) ? new XMLHttpRequest() : new ActiveXObject("Microsoft.XMLHTTP"));
        request.open("GET", logoutURL, true, "logout", "logout");
        request.send("");
    }
}