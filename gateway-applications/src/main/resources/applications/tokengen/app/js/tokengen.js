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

var loginPageSuffix = "tokengen/index.html";
var knoxtokenURL = "knoxtoken/api/v1/token";
var userAgent = navigator.userAgent.toLowerCase();

function get(name){
    //KNOX-820 changing the regex so that multiple query params get included with the 'originalUrl'
   if(name=(new RegExp('[?&]'+encodeURIComponent(name)+'=([^]*)')).exec(location.search))
      return decodeURIComponent(name[1]);
}

function testSameOrigin(url) {
    var loc = window.location,
        a = document.createElement('a');
    a.href = url;
    return a.hostname == loc.hostname &&
           a.port == loc.port &&
           a.protocol == loc.protocol;
}

function redirect(redirectUrl) {
  try { window.location.replace(redirectUrl); } 
  catch(e) { window.location = redirectUrl; }
}

var keypressed = function(event) {
    if (event.keyCode == 13) {
        gen();
    }
}

function b64DecodeUnicode(str) {
    // Going backwards: from bytestream, to percent-encoding, to original string.
    return decodeURIComponent(atob(str).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));
}

var gen = function() {
    var pathname = window.location.pathname;
    var topologyContext = pathname.replace(loginPageSuffix, "");;
    var baseURL = topologyContext.substring(0, topologyContext.lastIndexOf('/'));
    baseURL = baseURL.substring(0, baseURL.lastIndexOf('/') + 1);
    var tokenURL = topologyContext + knoxtokenURL;
    var form = document.forms[0];
    //var comment = form.comment.value;
    var lifespan = form.lifespan.value;
    var _gen = function() {
        var apiUrl = tokenURL;
        //Instantiate HTTP Request
        var params = '';
        if (lifespan != '') {
        	params = '?lifespan=' + lifespan;
        }
        var request = ((window.XMLHttpRequest) ? new XMLHttpRequest() : new ActiveXObject("Microsoft.XMLHTTP"));
        request.open("GET", apiUrl + params, true);
        request.send(null);

        //Process Response
        request.onreadystatechange = function(){
            if (request.readyState == 4) {
                if (request.status==0 || request.status==200) {
                    var resp = JSON.parse(request.responseText);
                    $('#resultBox').show();
                    var accessToken = resp.access_token;
                    $('#accessToken').text(accessToken);
                    var decodedToken = b64DecodeUnicode(accessToken.split(".")[1]);
                    var jwtjson = JSON.parse(decodedToken);
                    $('#accessPasscode').text(jwtjson["knox.id"]);
                    var date = new Date(resp.expires_in);
                    $('#expiry').text(date.toLocaleString());
                    $('#user').text(jwtjson.sub);
                    var homepageURL = resp.homepage_url;
                    $('#homepage_url').html("<a href=\"" + baseURL + homepageURL + "\">Homepage URL</a>");
                    var targetURL = resp.target_url;
                    $('#target_url').text(window.location.protocol + "//" + window.location.host + "/" + baseURL + targetURL);
                }
                else {
                  $('#errorBox').show();
                  if (request.status==401) {
                	window.location.reload();
                  }
                  else {
                    $('#errorBox .errorMsg').text("Response from " + request.responseURL + " - " + request.status + ": " + request.statusText);
                  }
                }
            }
        }
    }

    _gen();
}
