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

function displayInfo(infoMsg) {
    $('#tokenStateServiceStatusInfo').show();
    $('#tokenStateServiceStatusInfo').text(infoMsg);
    $('#tokenStateServiceStatusError').hide();
    $('#tokenStateServiceStatusWarning').hide();
}

function displayWarning(warningMsg) {
    $('#tokenStateServiceStatusWarning').show();
    $('#tokenStateServiceStatusWarning').text(warningMsg);
    $('#tokenStateServiceStatusError').hide();
    $('#tokenStateServiceStatusInfo').hide();
}

function disableTokenGen(errorMsg) {
    $('#tokenStateServiceStatusError').show();
    $('#tokenStateServiceStatusError').text(errorMsg);
    $('#tokenStateServiceStatusInfo').hide();
    $('#tokenStateServiceStatusWarning').hide();
    document.getElementById('genToken').disabled = true;
    document.getElementById('lifespan').disabled = true;
}

function setTokenStateServiceStatus() {
    var pathname = window.location.pathname;
    var topologyContext = pathname.replace(loginPageSuffix, "");
    var baseURL = topologyContext.substring(0, topologyContext.lastIndexOf('/'));
    baseURL = baseURL.substring(0, baseURL.lastIndexOf('/') + 1);
    var getTssStausURL = topologyContext + 'knoxtoken/api/v1/token/getTssStatus';
    var request = ((window.XMLHttpRequest) ? new XMLHttpRequest() : new ActiveXObject("Microsoft.XMLHTTP"));
    request.open("GET", getTssStausURL, true);
    request.send(null);
    request.onreadystatechange = function() {
        if (request.readyState == 4) {
            if (request.status==200) {
                var resp = JSON.parse(request.responseText);
                var tokenManagementEnabled = resp.tokenManagementEnabled;
                if (tokenManagementEnabled === 'true') {
                    var allowedTssForTokengen = resp.allowedTssForTokengen;
                    if (allowedTssForTokengen == 'true') {
                        var actualTssBackend = resp.actualTssBackend;
                        if (actualTssBackend == 'AliasBasedTokenStateService') {
                            displayWarning('Token management backend is configured to store tokens in keystores. This is only valid non-HA environments!');
                        } else {
                            displayInfo('Token management backend is properly configured for HA and production deployments.');
                        }
                    } else {
                        disableTokenGen('Token management backend initialization failed, token generation disabled.');
                    }
                } else {
                    disableTokenGen('Token management is disabled');
                }

                $('#maximumLifetimeText').text(resp.maximumLifetimeText);
                $('#maximumLifetimeSeconds').text(resp.maximumLifetimeSeconds);

                if (resp.lifespanInputEnabled === "true") {
                    $('#lifespanFields').show();
                    document.getElementById("lifespanInputEnabled").value = "true";
                }
            }
        }
    }
}

function validateLifespan(lifespanInputEnabled, days, hours, mins) {
    if (lifespanInputEnabled === "false") {
        return true;
    }

    //show possible contraint violations
    days.reportValidity();
    hours.reportValidity();
    mins.reportValidity();

    //check basic contraint validations (less than/ greater then)
    var valid = days.checkValidity() && hours.checkValidity() && mins.checkValidity();

    if (days.value == '0' && hours.value == '0' && mins.value == '0') {
        valid = false;
    }

    if (days.value == '' || hours.value == '' || mins.value == '') {
        valid = false;
    }

    if (!valid) {
        $('#invalidLifetimeText').show();
    }

    return valid;
}

function validateComment(comment) {
    var valid = true;
    if (comment.value != '') {
        comment.reportValidity();
        valid = comment.checkValidity();
        if (!valid) {
            $('#invalidCommentText').show();
        }
    }

    return valid;
}

function maximumLifetimeExceeded(maximumLifetime, days, hours, mins) {
	if (maximumLifetime == -1) {
		return false;
	}

    var daysInSeconds = days * 86400;
    var hoursInSeconds = hours * 3600;
    var minsInSeconds = mins * 60;
    var suppliedLifetime = daysInSeconds + hoursInSeconds + minsInSeconds;
    //console.debug("Supplied lifetime in seconds = " + suppliedLifetime);
    //console.debug("Maximum lifetime = " + maximumLifetime);
    return suppliedLifetime > maximumLifetime;
}

var gen = function() {
	$('#invalidLifetimeText').hide();
    var pathname = window.location.pathname;
    var topologyContext = pathname.replace(loginPageSuffix, "");;
    var baseURL = topologyContext.substring(0, topologyContext.lastIndexOf('/'));
    baseURL = baseURL.substring(0, baseURL.lastIndexOf('/') + 1);
    var tokenURL = topologyContext + knoxtokenURL;
    var form = document.forms[0];
    var lt_days = form.lt_days.value;
    var lt_hours = form.lt_hours.value;
    var lt_mins = form.lt_mins.value;
    var lifespanInputEnabled = form.lifespanInputEnabled.value;
    var _gen = function() {
        $('#errorBox').hide();
        $('#resultBox').hide();
        var apiUrl = tokenURL;
        var params = "";
        if (lifespanInputEnabled === "true") {
            params = params + '?lifespan=P' + lt_days + "DT" + lt_hours + "H" + lt_mins + "M";  //we need to support Java's Duration pattern
        }

        if (form.comment.value != '') {
            params = params + (lifespanInputEnabled === "true" ? "&" : "?") + 'comment=' + encodeURIComponent(form.comment.value);
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
                    $('#accessPasscode').text(resp.passcode);
                    $('#expiry').text(new Date(resp.expires_in).toLocaleString());
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
                    var errorMsg = "Response from " + request.responseURL + " - " + request.status + ": " + request.statusText;
                    if (request.responseText) {
                        errorMsg += " (" + request.responseText + ")";
                    }
                    $('#errorBox .errorMsg').text(errorMsg);
                  }
                }
            }
        }
    }

    if (validateLifespan(lifespanInputEnabled, form.lt_days, form.lt_hours, form.lt_mins) && validateComment(form.comment)) {
        if (maximumLifetimeExceeded(form.maximumLifetimeSeconds.textContent, lt_days, lt_hours, lt_mins)) {
            swal({
                title: "Warning",
                text: "You are trying to generate a token with a lifetime that exceeds the configured maximum. In this case the generated token's lifetime will be limited to the configured maximum.",
                icon: "warning",
                buttons: ["Adjust request lifetime", "Generate token anyway"],
                dangerMode: true
              })
              .then((willGenerateToken) => {
                if (willGenerateToken) {
                    _gen();
                }
              });
          } else {
              _gen();
          }
    }
}

function copyTextToClipboard(elementId) {
    var toBeCopied = document.getElementById(elementId).innerText.trim();
    const tempTextArea = document.createElement('textarea');
    tempTextArea.value = toBeCopied;
    document.body.appendChild(tempTextArea);
    tempTextArea.select();
    document.execCommand('copy');
    document.body.removeChild(tempTextArea);
    swal("Copied to clipboard!", {buttons: false, timer: 1000});
}