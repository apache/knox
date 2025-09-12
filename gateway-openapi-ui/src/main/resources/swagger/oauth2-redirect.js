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
"use strict";function run(){var e,r,t,a=window.opener.swaggerUIRedirectOauth2,o=a.state,n=a.redirectUrl;if((t=(r=/code|token|error/.test(window.location.hash)?window.location.hash.substring(1).replace("?","&"):location.search.substring(1)).split("&")).forEach((function(e,r,t){t[r]='"'+e.replace("=",'":"')+'"'})),e=(r=r?JSON.parse("{"+t.join()+"}",(function(e,r){return""===e?r:decodeURIComponent(r)})):{}).state===o,"accessCode"!==a.auth.schema.get("flow")&&"authorizationCode"!==a.auth.schema.get("flow")&&"authorization_code"!==a.auth.schema.get("flow")||a.auth.code)a.callback({auth:a.auth,token:r,isValid:e,redirectUrl:n});else if(e||a.errCb({authId:a.auth.name,source:"auth",level:"warning",message:"Authorization may be unsafe, passed state was changed in server Passed state wasn't returned from auth server"}),r.code)delete a.state,a.auth.code=r.code,a.callback({auth:a.auth,redirectUrl:n});else{let e;r.error&&(e="["+r.error+"]: "+(r.error_description?r.error_description+". ":"no accessCode received from the server. ")+(r.error_uri?"More info: "+r.error_uri:"")),a.errCb({authId:a.auth.name,source:"auth",level:"error",message:e||"[Authorization failed]: no accessCode received from the server"})}window.close()}"loading"!==document.readyState?run():document.addEventListener("DOMContentLoaded",(function(){run()}));