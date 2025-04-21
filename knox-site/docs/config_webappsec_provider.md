<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->
<!---
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
--->

### Web App Security Provider ###
Knox is a Web API (REST) Gateway for Hadoop. The fact that REST interactions are HTTP based means that they are vulnerable to a number of web application security vulnerabilities. This project introduces a web application security provider for plugging in various protection filters.

#### Configuration ####
##### Overview #####
As with all providers in the Knox gateway, the web app security provider is configured through provider parameters. Unlike many other providers, the web app security provider may actually host multiple vulnerability/security filters. Currently, we only have implementations for CSRF, CORS and HTTP STS but others might follow, and you may be interested in creating your own.

Because of this one-to-many provider/filter relationship, there is an extra configuration element for this provider per filter. As you can see in the sample below, the actual filter configuration is defined entirely within the parameters of the WebAppSec provider.

    <provider>
        <role>webappsec</role>
        <name>WebAppSec</name>
        <enabled>true</enabled>
        <param><name>csrf.enabled</name><value>true</value></param>
        <param><name>csrf.customHeader</name><value>X-XSRF-Header</value></param>
        <param><name>csrf.methodsToIgnore</name><value>GET,OPTIONS,HEAD</value></param>
        <param><name>cors.enabled</name><value>false</value></param>
        <param><name>xframe.options.enabled</name><value>true</value></param>
        <param><name>xss.protection.enabled</name><value>true</value></param>
        <param><name>strict.transport.enabled</name><value>true</value></param>
    </provider>

#### Descriptions ####
The following tables describes the configuration options for the web app security provider:

##### CSRF

Cross site request forgery (CSRF) attacks attempt to force an authenticated user to 
execute functionality without their knowledge. By presenting them with a link or image that when clicked invokes a request to another site with which the user may have already established an active session.

CSRF is entirely a browser-based attack. Some background knowledge of how browsers work enables us to provide a filter that will prevent CSRF attacks. HTTP requests from a web browser performed via form, image, iframe, etc. are unable to set custom HTTP headers. The only way to create a HTTP request from a browser with a custom HTTP header is to use a technology such as JavaScript XMLHttpRequest or Flash. These technologies can set custom HTTP headers but have security policies built in to prevent web sites from sending requests to each other 
unless specifically allowed by policy. 

This means that a website www.bad.com cannot send a request to  http://bank.example.com with the custom header X-XSRF-Header unless they use a technology such as a XMLHttpRequest. That technology would prevent such a request from being made unless the bank.example.com domain specifically allowed it. This then results in a REST endpoint that can only be called via XMLHttpRequest (or similar technology).

NOTE: by enabling this protection within the topology, this custom header will be required for *all* clients that interact with it - not just browsers.

###### Config

Name                 | Description | Default
---------------------|-------------|--------
csrf.enabled         | This parameter enables the CSRF protection capabilities | false  
csrf.customHeader    | This is an optional parameter that indicates the name of the header to be used in order to determine that the request is from a trusted source. It defaults to the header name described by the NSA in its guidelines for dealing with CSRF in REST. | X-XSRF-Header
csrf.methodsToIgnore | This is also an optional parameter that enumerates the HTTP methods to allow through without the custom HTTP header. This is useful for allowing things like GET requests from the URL bar of a browser, but it assumes that the GET request adheres to REST principals in terms of being idempotent. If this cannot be assumed then it would be wise to not include GET in the list of methods to ignore. |  GET,OPTIONS,HEAD

###### REST Invocation
The following curl command can be used to request a directory listing from HDFS while passing in the expected header X-XSRF-Header.

    curl -k -i --header "X-XSRF-Header: valid" -v -u guest:guest-password https://localhost:8443/gateway/sandbox/webhdfs/v1/tmp?op=LISTSTATUS

Omitting the `--header "X-XSRF-Header: valid"` above should result in an HTTP 400 bad_request.

Disabling the provider will then allow a request that is missing the header through. 

##### CORS

For security reasons, browsers restrict cross-origin HTTP requests initiated from within scripts. For example, XMLHttpRequest follows the same-origin policy. So, a web application using XMLHttpRequest could only make HTTP requests to its own domain. To improve web applications, developers asked browser vendors to allow XMLHttpRequest to make cross-domain requests.

Cross Origin Resource Sharing is a way to explicitly alter the same-origin policy for a given application or API. In order to allow for applications to make cross domain requests through Apache Knox, we need to configure the CORS filter of the WebAppSec provider.

###### Config

Name                         | Description | Default
-----------------------------|-------------|---------
cors.enabled                 | Setting this parameter to true allows cross origin requests. The default of false prevents cross origin requests.|false
cors.allowGenericHttpRequests| {true\|false} defaults to true. If true, generic HTTP requests will be allowed to pass through the filter, else only valid and accepted CORS requests will be allowed (strict CORS filtering).|true
cors.allowOrigin             | {"\*"\|origin-list} defaults to "\*". Whitespace-separated list of origins that the CORS filter must allow. Requests from origins not included here will be refused with an HTTP 403 "Forbidden" response. If set to \* (asterisk) any origin will be allowed.|"\*"
cors.allowSubdomains         | {true\|false} defaults to false. If true, the CORS filter will allow requests from any origin which is a subdomain origin of the allowed origins. A subdomain is matched by comparing its scheme and suffix (host name / IP address and optional port number).|false
cors.supportedMethods        | {method-list} defaults to GET, POST, HEAD, OPTIONS. List of the supported HTTP methods. These are advertised through the Access-Control-Allow-Methods header and must also be implemented by the actual CORS web service. Requests for methods not included here will be refused by the CORS filter with an HTTP 405 "Method not allowed" response.| GET, POST, HEAD, OPTIONS
cors.supportedHeaders        | {"\*"\|header-list} defaults to \*. The names of the supported author request headers. These are advertised through the Access-Control-Allow-Headers header. If the configuration property value is set to \* (asterisk) any author request header will be allowed. The CORS Filter implements this by simply echoing the requested value back to the browser.|\*
cors.exposedHeaders          | {header-list} defaults to empty list. List of the response headers other than simple response headers that the browser should expose to the author of the cross-domain request through the XMLHttpRequest.getResponseHeader() method. The CORS filter supplies this information through the Access-Control-Expose-Headers header.| empty
cors.supportsCredentials     | {true\|false} defaults to true. Indicates whether user credentials, such as cookies, HTTP authentication or client-side certificates, are supported. The CORS filter uses this value in constructing the Access-Control-Allow-Credentials header.|true
cors.maxAge                  | {int} defaults to -1 (unspecified). Indicates how long the results of a preflight request can be cached by the web browser, in seconds. If -1 unspecified. This information is passed to the browser via the Access-Control-Max-Age header.| -1
cors.tagRequests             | {true\|false} defaults to false (no tagging). Enables HTTP servlet request tagging to provide CORS information to downstream handlers (filters and/or servlets).| false

##### X-Frame-Options

Cross Frame Scripting and Clickjacking are attacks that can be prevented by controlling the ability for a third-party to embed an application or resource within a Frame, IFrame or Object html element. This can be done adding the X-Frame-Options HTTP header to responses.

###### Config

Name                   | Description | Default
-----------------------|-------------|---------
xframe.options.enabled | This parameter enables the X-Frame-Options capabilities|false
xframe.options.value   | This parameter specifies a particular value for the X-Frame-Options header. Most often the default value of DENY will be most appropriate. You can also use SAMEORIGIN or ALLOW-FROM uri|DENY

##### X-XSS-Protection

Cross-site Scripting (XSS) type attacks can be prevented by adding the X-XSS-Protection header to HTTP response. The `1; mode=block` value will force browser to stop rendering the page if XSS attack is detected. 

###### Config

Name                   | Description | Default
-----------------------|-------------|---------
xss.protection.enabled   | This parameter specifies a particular value for the X-XSS-Protection header. When it is set to true, it will add `X-Xss-Protection: '1; mode=block'` header to HTTP response|false

##### X-Content-Type-Options

Browser MIME content type sniffing can be exploited for malicious purposes. Adding the X-Content-Type-Options HTTP header to responses directs the browser to honor the type specified in the Content-Type header, rather than trying to determine the type from the content itself. Most modern browsers support this.

###### Config

Name                         | Description | Default
-----------------------------|-------------|---------
xcontent-type.options.enabled                 | This param enables the X-Content-Type-Options header inclusion|false
xcontent-type.options                | This param specifies a particular value for the X-Content-Type-Options header. The default value is really the only meaningful value|nosniff

##### HTTP Strict Transport Security

HTTP Strict Transport Security (HSTS) is a web security policy mechanism which helps to protect websites against protocol downgrade attacks and cookie hijacking. It allows web servers to declare that web browsers (or other complying user agents) should only interact with it using secure HTTPS connections and never via the insecure HTTP protocol.

###### Config

Name                     | Description | Default
-------------------------|-------------|---------
strict.transport.enabled | This parameter enables the HTTP Strict-Transport-Security response header|false
strict.transport         | This parameter specifies a particular value for the HTTP Strict-Transport-Security header. Default value is max-age=31536000. You can also use `max-age=<expire-time>` or `max-age=<expire-time>; includeSubDomains` or `max-age=<expire-time>;preload`|max-age=31536000

##### Rate limiting

Rate limiting is very useful for limiting exposure to abuse from request flooding, whether malicious, or as a result of a misconfigured client.
Following are the configurable options:

Config Name              | Description | Default
-------------------------|-------------|---------
rate.limiting.maxRequestsPerSec | Maximum number of requests from a connection per second. Requests in excess of this are first delayed, then throttled.  | 25
rate.limiting.delayMs         | Delay imposed on all requests over the rate limit, before they are considered at all, in ms. `-1 = Reject request`, `0 = No delay`, `any other value = Delay in ms`. *NOTE:* with a non-negative value (including `0`) the `gateway.servlet.async.supported` property in the `gateway-site.xml` has to be set to `true` (it is false by default). | 100
rate.limiting.maxWaitMs     | Length of time to blocking wait for the throttle semaphore in ms.   | 50
rate.limiting.throttledRequests     | Number of requests over the rate limit able to be considered at once.   | 5
rate.limiting.throttleMs     | Length of time, in ms, to async wait for semaphore.   | 30000L
rate.limiting.maxRequestMs    | Length of time, in ms, to allow the request to run.  | 30000L
rate.limiting.maxIdleTrackerMs     | Length of time, in ms, to keep track of request rates for a connection, before deciding that the user has gone away, and discarding it.   | 30000L
rate.limiting.insertHeaders     | If true, insert the DoSFilter headers into the response.   | true
rate.limiting.trackSessions     | If true, usage rate is tracked by session if a session exists.   | true
rate.limiting.remotePort     | If true and session tracking is not used, then rate is tracked by IP and port (effectively connection).   | false
rate.limiting.ipWhitelist     | A comma-separated list of IP addresses that will not be rate limited.   | empty



When using the gateway with long running requests the `rate.limiting.maxRequestMs` parameter should be configured accordingly, otherwise the requests running longer then the default `30000ms` value, will be unsuccessful.

###### Config

Name                     | Description | Default
-------------------------|-------------|---------
rate.limiting.enabled | This parameter enables the rate limiting feature|false
