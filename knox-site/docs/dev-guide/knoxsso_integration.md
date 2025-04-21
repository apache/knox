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
Knox SSO Integration for UIs
===

Introduction
---
KnoxSSO provides an abstraction for integrating any number of authentication systems and SSO solutions and enables participating web applications to scale to those solutions more easily. Without the token exchange capabilities offered by KnoxSSO each component UI would need to integrate with each desired solution on its own. 

This document examines the way to integrate with Knox SSO in the form of a Servlet Filter. This approach should be easily extrapolated into other frameworks - ie. Spring Security.

### General Flow

The following is a generic sequence diagram for SAML integration through KnoxSSO.

<<general_saml_flow.puml>> 

#### KnoxSSO Setup

##### knoxsso.xml Topology
In order to enable KnoxSSO, we need to configure the IdP topology. The following is an example of this topology that is configured to use HTTP Basic Auth against the Knox Demo LDAP server. This is the lowest barrier of entry for your development environment that actually authenticates against a real user store. What’s great is if you work against the IdP with Basic Auth then you will work with SAML or anything else as well.

```
		<?xml version="1.0" encoding="utf-8"?>
		<topology>
    		<gateway>
        		<provider>
            		<role>authentication</role>
            		<name>ShiroProvider</name>
            		<enabled>true</enabled>
            		<param>
	                	<name>sessionTimeout</name>
                		<value>30</value>
            		</param>
            		<param>
                		<name>main.ldapRealm</name>
                		<value>org.apache.knox.gateway.shirorealm.KnoxLdapRealm</value>
            		</param>
            		<param>
                		<name>main.ldapContextFactory</name>
                		<value>org.apache.knox.gateway.shirorealm.KnoxLdapContextFactory</value>
            		</param>
            		<param>
                		<name>main.ldapRealm.contextFactory</name>
                		<value>$ldapContextFactory</value>
            		</param>
            		<param>
                		<name>main.ldapRealm.userDnTemplate</name>
                		<value>uid={0},ou=people,dc=hadoop,dc=apache,dc=org</value>
            		</param>
            		<param>
                		<name>main.ldapRealm.contextFactory.url</name>
                		<value>ldap://localhost:33389</value>
            		</param>
            		<param>
                		<name>main.ldapRealm.contextFactory.authenticationMechanism</name>
                		<value>simple</value>
            		</param>
            		<param>
                		<name>urls./**</name>
                		<value>authcBasic</value>
            		</param>
        		</provider>
            <provider>
        		    <role>identity-assertion</role>
            		<name>Default</name>
            		<enabled>true</enabled>
        		</provider>
    		</gateway>
        <service>
        		<role>KNOXSSO</role>
        		<param>
          			<name>knoxsso.cookie.secure.only</name>
          			<value>true</value>
        		</param>
        		<param>
          			<name>knoxsso.token.ttl</name>
          			<value>100000</value>
        		</param>
    		</service>
		</topology>
```

Just as with any Knox service, the KNOXSSO service is protected by the gateway providers defined above it. In this case, the ShiroProvider is taking care of HTTP Basic Auth against LDAP for us. Once the user authenticates the request processing continues to the KNOXSSO service that will create the required cookie and do the necessary redirects.

The authenticate/federation provider can be swapped out to fit your deployment environment.

##### sandbox.xml Topology
In order to see the end to end story and use it as an example in your development, you can configure one of the cluster topologies to use the SSOCookieProvider instead of the out of the box ShiroProvider. The following is an example sandbox.xml topology that is configured for using KnoxSSO to protect access to the Hadoop REST APIs.

```
	<?xml version="1.0" encoding="utf-8"?>
<topology>
  <gateway>
    <provider>
        <role>federation</role>
        <name>SSOCookieProvider</name>
        <enabled>true</enabled>
        <param>
            <name>sso.authentication.provider.url</name>
            <value>https://localhost:9443/gateway/idp/api/v1/websso</value>
        </param>
    </provider>
    <provider>
        <role>identity-assertion</role>
        <name>Default</name>
        <enabled>true</enabled>
    </provider>
  </gateway>    
  <service>
      <role>NAMENODE</role>
      <url>hdfs://localhost:8020</url>
  </service>
  <service>
      <role>JOBTRACKER</role>
      <url>rpc://localhost:8050</url>
  </service>
  <service>
      <role>WEBHDFS</role>
      <url>http://localhost:50070/webhdfs</url>
  </service>
  <service>
      <role>WEBHCAT</role>
      <url>http://localhost:50111/templeton</url>
  </service>
  <service>
      <role>OOZIE</role>
      <url>http://localhost:11000/oozie</url>
  </service>
  <service>
      <role>WEBHBASE</role>
      <url>http://localhost:60080</url>
  </service>
  <service>
      <role>HIVE</role>
      <url>http://localhost:10001/cliservice</url>
  </service>
  <service>
      <role>RESOURCEMANAGER</role>
      <url>http://localhost:8088/ws</url>
  </service>
</topology>
```

* NOTE: Be aware that when using Chrome as your browser that cookies don’t seem to work for “localhost”. Either use a VM or like I did - use 127.0.0.1. Safari works with localhost without problems.

As you can see above, the only thing being configured is the SSO provider URL. Since Knox is the issuer of the cookie and token, we don’t need to configure the public key since we have programmatic access to the actual keystore for use at verification time.

#### Curl the Flow
We should now be able to walk through the SSO Flow at the command line with curl to see everything that happens.

First, issue a request to WEBHDFS through knox.

```
	bash-3.2$ curl -iku guest:guest-password https://localhost:8443/gateway/sandbox/webhdfs/v1/tmp?op+LISTSTATUS
	
	HTTP/1.1 302 Found
	Location: https://localhost:8443/gateway/idp/api/v1/websso?originalUrl=https://localhost:8443/gateway/sandbox/webhdfs/v1/tmp?op+LISTSTATUS
	Content-Length: 0
	Server: Jetty(8.1.14.v20131031)
```

Note the redirect to the knoxsso endpoint and the loginUrl with the originalUrl request parameter. We need to see that come from your integration as well.

Let’s manually follow that redirect with curl now:

```
	bash-3.2$ curl -iku guest:guest-password "https://localhost:8443/gateway/idp/api/v1/websso?originalUrl=https://localhost:9443/gateway/sandbox/webhdfs/v1/tmp?op=LISTSTATUS"

	HTTP/1.1 307 Temporary Redirect
	Set-Cookie: JSESSIONID=mlkda4crv7z01jd0q0668nsxp;Path=/gateway/idp;Secure;HttpOnly
	Set-Cookie: hadoop-jwt=eyJhbGciOiJSUzI1NiJ9.eyJleHAiOjE0NDM1ODUzNzEsInN1YiI6Imd1ZXN0IiwiYXVkIjoiSFNTTyIsImlzcyI6IkhTU08ifQ.RpA84Qdr6RxEZjg21PyVCk0G1kogvkuJI2bo302bpwbvmc-i01gCwKNeoGYzUW27MBXf6a40vylHVR3aZuuBUxsJW3aa_ltrx0R5ztKKnTWeJedOqvFKSrVlBzJJ90PzmDKCqJxA7JUhyo800_lDHLTcDWOiY-ueWYV2RMlCO0w;Path=/;Domain=localhost;Secure;HttpOnly
	Expires: Thu, 01 Jan 1970 00:00:00 GMT
	Location: https://localhost:8443/gateway/sandbox/webhdfs/v1/tmp?op=LISTSTATUS
	Content-Length: 0
	Server: Jetty(8.1.14.v20131031)
```

Note the redirect back to the original URL in the Location header and the Set-Cookie for the hadoop-jwt cookie. This is what the SSOCookieProvider in sandbox (and ultimately in your integration) will be looking for.

Finally, we should be able to take the above cookie and pass it to the original url as indicated in the Location header for our originally requested resource:

```
	bash-3.2$ curl -ikH "Cookie: hadoop-jwt=eyJhbGciOiJSUzI1NiJ9.eyJleHAiOjE0NDM1ODY2OTIsInN1YiI6Imd1ZXN0IiwiYXVkIjoiSFNTTyIsImlzcyI6IkhTU08ifQ.Os5HEfVBYiOIVNLRIvpYyjeLgAIMbBGXHBWMVRAEdiYcNlJRcbJJ5aSUl1aciNs1zd_SHijfB9gOdwnlvQ_0BCeGHlJBzHGyxeypIoGj9aOwEf36h-HVgqzGlBLYUk40gWAQk3aRehpIrHZT2hHm8Pu8W-zJCAwUd8HR3y6LF3M;Path=/;Domain=localhost;Secure;HttpOnly" https://localhost:9443/gateway/sandbox/webhdfs/v1/tmp?op=LISTSTATUS

	TODO: cluster was down and needs to be recreated :/
```

#### Browse the Flow
At this point, we can use a web browser instead of the command line and see how the browser will challenge the user for Basic Auth Credentials and then manage the cookies such that the SSO and token exchange aspects of the flow are hidden from the user.

Simply, try to invoke the same webhdfs API from the browser URL bar.


```
		https://localhost:8443/gateway/sandbox/webhdfs/v1/tmp?op=LISTSTATUS
```

Based on our understanding of the flow it should behave like:

* SSOCookieProvider checks for hadoop-jwt cookie and in its absence redirects to the configured SSO provider URL (knoxsso endpoint)
* ShiroProvider on the KnoxSSO endpoint returns a 401 and the browser challenges the user for username/password
* The ShiroProvider authenticates the user against the Demo LDAP Server using a simple LDAP bind and establishes the security context for the WebSSO request
* The WebSSO service exchanges the normalized Java Subject into a JWT token and sets it on the response as a cookie named hadoop-jwt
* The WebSSO service then redirects the user agent back to the originally requested URL - the webhdfs Knox service
subsequent invocations will find the cookie in the incoming request and not need to engage the WebSSO service again until it expires.

#### Filter by Example
We have added a federation provider to Knox for accepting KnoxSSO cookies for REST APIs. This provides us with a couple benefits:
KnoxSSO support for REST APIs for XmlHttpRequests from JavaScript (basic CORS functionality is also included). This is still rather basic and considered beta code.
A model and real world usecase for others to base their integrations on

In addition, https://issues.apache.org/jira/browse/HADOOP-11717 added support for the Hadoop UIs to the hadoop-auth module and it can be used as another example.

We will examine the new SSOCookieFederationFilter in Knox here.

```
package org.apache.knox.gateway.provider.federation.jwt.filter;

	import java.io.IOException;
		import java.security.Principal;
		import java.security.PrivilegedActionException;
		import java.security.PrivilegedExceptionAction;
		import java.util.ArrayList;
		import java.util.Date;
		import java.util.HashSet;
		import java.util.List;
		import java.util.Set;
		
		import javax.security.auth.Subject;
		import javax.servlet.Filter;
		import javax.servlet.FilterChain;
		import javax.servlet.FilterConfig;
		import javax.servlet.ServletException;
		import javax.servlet.ServletRequest;
		import javax.servlet.ServletResponse;
		import javax.servlet.http.Cookie;
		import javax.servlet.http.HttpServletRequest;
		import javax.servlet.http.HttpServletResponse;
		
		import org.apache.knox.gateway.i18n.messages.MessagesFactory;
		import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;
		import org.apache.knox.gateway.security.PrimaryPrincipal;
		import org.apache.knox.gateway.services.GatewayServices;
		import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
		import org.apache.knox.gateway.services.security.token.TokenServiceException;
		import org.apache.knox.gateway.services.security.token.impl.JWTToken;
		
		public class SSOCookieFederationFilter implements Filter {
		  private static JWTMessages log = MessagesFactory.get( JWTMessages.class );
		  private static final String ORIGINAL_URL_QUERY_PARAM = "originalUrl=";
		  private static final String SSO_COOKIE_NAME = "sso.cookie.name";
		  private static final String SSO_EXPECTED_AUDIENCES = "sso.expected.audiences";
		  private static final String SSO_AUTHENTICATION_PROVIDER_URL = "sso.authentication.provider.url";
		  private static final String DEFAULT_SSO_COOKIE_NAME = "hadoop-jwt";
```

The above represent the configurable aspects of the integration

```
    private JWTokenAuthority authority = null;
    private String cookieName = null;
    private List<String> audiences = null;
    private String authenticationProviderUrl = null;

    @Override
    public void init( FilterConfig filterConfig ) throws ServletException {
      GatewayServices services = (GatewayServices) filterConfig.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
      authority = (JWTokenAuthority)services.getService(GatewayServices.TOKEN_SERVICE);
```

The above is a Knox specific internal service that we use to issue and verify JWT tokens. This will be covered separately and you will need to be implement something similar in your filter implementation.

```
    // configured cookieName
    cookieName = filterConfig.getInitParameter(SSO_COOKIE_NAME);
    if (cookieName == null) {
      cookieName = DEFAULT_SSO_COOKIE_NAME;
    }
```

The configurable cookie name is something that can be used to change a cookie name to fit your deployment environment. The default name is hadoop-jwt which is also the default in the Hadoop implementation. This name must match the name being used by the KnoxSSO endpoint when setting the cookie.

```
    // expected audiences or null
    String expectedAudiences = filterConfig.getInitParameter(SSO_EXPECTED_AUDIENCES);
    if (expectedAudiences != null) {
      audiences = parseExpectedAudiences(expectedAudiences);
    }
```

Audiences are configured as a comma separated list of audience strings. Names of intended recipients or intents. The semantics that we are using for this processing is that - if not configured than any (or none) audience is accepted. If there are audiences configured then as long as one of the expected ones is found in the set of claims in the token it is accepted.

```
    // url to SSO authentication provider
    authenticationProviderUrl = filterConfig.getInitParameter(SSO_AUTHENTICATION_PROVIDER_URL);
    if (authenticationProviderUrl == null) {
      log.missingAuthenticationProviderUrlConfiguration();
    }
  }
```

This is the URL to the KnoxSSO endpoint. It is required and SSO/token exchange will not work without this set correctly.

```
	/**
   	* @param expectedAudiences
   	* @return
   	*/
   	private List<String> parseExpectedAudiences(String expectedAudiences) {
     ArrayList<String> audList = null;
       // setup the list of valid audiences for token validation
       if (expectedAudiences != null) {
         // parse into the list
         String[] audArray = expectedAudiences.split(",");
         audList = new ArrayList<String>();
         for (String a : audArray) {
           audList.add(a);
         }
       }
       return audList;
     }
```

The above method parses the comma separated list of expected audiences and makes it available for interrogation during token validation.

```
    public void destroy() {
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
        throws IOException, ServletException {
      String wireToken = null;
      HttpServletRequest req = (HttpServletRequest) request;
  
      String loginURL = constructLoginURL(req);
      wireToken = getJWTFromCookie(req);
      if (wireToken == null) {
        if (req.getMethod().equals("OPTIONS")) {
          // CORS preflight requests to determine allowed origins and related config
          // must be able to continue without being redirected
          Subject sub = new Subject();
          sub.getPrincipals().add(new PrimaryPrincipal("anonymous"));
          continueWithEstablishedSecurityContext(sub, req, (HttpServletResponse) response, chain);
        }
        log.sendRedirectToLoginURL(loginURL);
        ((HttpServletResponse) response).sendRedirect(loginURL);
      }
      else {
        JWTToken token = new JWTToken(wireToken);
        boolean verified = false;
        try {
          verified = authority.verifyToken(token);
          if (verified) {
            Date expires = token.getExpiresDate();
            if (expires == null || new Date().before(expires)) {
              boolean audValid = validateAudiences(token);
              if (audValid) {
                Subject subject = createSubjectFromToken(token);
                continueWithEstablishedSecurityContext(subject, (HttpServletRequest)request, (HttpServletResponse)response, chain);
              }
              else {
                log.failedToValidateAudience();
                ((HttpServletResponse) response).sendRedirect(loginURL);
              }
            }
            else {
              log.tokenHasExpired();
            ((HttpServletResponse) response).sendRedirect(loginURL);
            }
          }
          else {
            log.failedToVerifyTokenSignature();
          ((HttpServletResponse) response).sendRedirect(loginURL);
          }
        } catch (TokenServiceException e) {
          log.unableToVerifyToken(e);
        ((HttpServletResponse) response).sendRedirect(loginURL);
        }
      }
    }
```

The doFilter method above is where all the real work is done. We look for a cookie by the configured name. If it isn’t there then we redirect to the configured SSO provider URL in order to acquire one. That is unless it is an OPTIONS request which may be a preflight CORS request. You shouldn’t need to worry about this aspect. It is really a REST API concern not a web app UI one.

Once we get a cookie, the underlying JWT token is extracted and returned as the wireToken from which we create a Knox specific JWTToken. This abstraction is around the use of the nimbus JWT library which you can use directly. We will cover those details separately.

We then ask the token authority component to verify the token. This involves signature validation of the signed token. In order to verify the signature of the token you will need to have the public key of the Knox SSO server configured and provided to the nimbus library through its API at verification time. NOTE: This is a good place to look at the Hadoop implementation as an example.

Once we know the token is signed by a trusted party we then validate whether it is expired and that it has an expected (or no) audience claims.

Finally, when we have a valid token, we create a Java Subject from it and continue the request through the filterChain as the authenticated user.

```
	/**
   	* Encapsulate the acquisition of the JWT token from HTTP cookies within the
   	* request.
   	*
   	* @param req servlet request to get the JWT token from
   	* @return serialized JWT token
   	*/
  	protected String getJWTFromCookie(HttpServletRequest req) {
    String serializedJWT = null;
    Cookie[] cookies = req.getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (cookieName.equals(cookie.getName())) {
          log.cookieHasBeenFound(cookieName);
          serializedJWT = cookie.getValue();
          break;
        }
      }
    }
    return serializedJWT;
  	}
```
  
The above method extracts the serialized token from the cookie and returns it as the wireToken.

```
  	/**
   	* Create the URL to be used for authentication of the user in the absence of
   	* a JWT token within the incoming request.
   	*
   	* @param request for getting the original request URL
   	* @return url to use as login url for redirect
   	*/
  	protected String constructLoginURL(HttpServletRequest request) {
    String delimiter = "?";
    if (authenticationProviderUrl.contains("?")) {
      delimiter = "&";
    }
    String loginURL = authenticationProviderUrl + delimiter
        + ORIGINAL_URL_QUERY_PARAM
        + request.getRequestURL().toString()+ getOriginalQueryString(request);
    	return loginURL;
  	}

  	private String getOriginalQueryString(HttpServletRequest request) {
    	String originalQueryString = request.getQueryString();
    	return (originalQueryString == null) ? "" : "?" + originalQueryString;
  	}
```

The above method creates the full URL to be used in redirecting to the KnoxSSO endpoint. It includes the SSO provider URL as well as the original request URL so that we can redirect back to it after authentication and token exchange.

```
  	/**
   	* Validate whether any of the accepted audience claims is present in the
   	* issued token claims list for audience. Override this method in subclasses
   	* in order to customize the audience validation behavior.
   	*
   	* @param jwtToken
   	*          the JWT token where the allowed audiences will be found
   	* @return true if an expected audience is present, otherwise false
   	*/
  	protected boolean validateAudiences(JWTToken jwtToken) {
    	boolean valid = false;
    	String[] tokenAudienceList = jwtToken.getAudienceClaims();
    	// if there were no expected audiences configured then just
    	// consider any audience acceptable
    	if (audiences == null) {
      		valid = true;
    	} else {
      		// if any of the configured audiences is found then consider it
      		// acceptable
      		for (String aud : tokenAudienceList) {
        	if (audiences.contains(aud)) {
          		//log.debug("JWT token audience has been successfully validated");
          		log.jwtAudienceValidated();
          		valid = true;
          		break;
        	}
      	}
    }
    return valid;
  	}
```

The above method implements the audience claim semantics explained earlier.

```
	private void continueWithEstablishedSecurityContext(Subject subject, final 		HttpServletRequest request, final HttpServletResponse response, final FilterChain chain) throws IOException, ServletException {
    try {
      Subject.doAs(
        subject,
        new PrivilegedExceptionAction<Object>() {
          @Override
          public Object run() throws Exception {
            chain.doFilter(request, response);
            return null;
          }
        }
        );
    }
    catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      }
      else if (t instanceof ServletException) {
        throw (ServletException) t;
      }
      else {
        throw new ServletException(t);
      }
    }
  	}
```

This method continues the filter chain processing upon successful validation of the token. This would need to be replaced with your environment’s equivalent of continuing the request or login to the app as the authenticated user.

```
  	private Subject createSubjectFromToken(JWTToken token) {
    	final String principal = token.getSubject();
    	@SuppressWarnings("rawtypes")
    	HashSet emptySet = new HashSet();
    	Set<Principal> principals = new HashSet<Principal>();
    	Principal p = new PrimaryPrincipal(principal);
    	principals.add(p);
    	javax.security.auth.Subject subject = new javax.security.auth.Subject(true, principals, emptySet, emptySet);
    	return subject;
  	}
```
This method takes a JWTToken and creates a Java Subject with the principals expected by the rest of the Knox processing. This would need to be implemented in a way appropriate for your operating environment as well. For instance, the Hadoop handler implementation returns a Hadoop AuthenticationToken to the calling filter which in turn ends up in the Hadoop auth cookie.

```
	}
```

#### Token Signature Validation 
The following is the method from the Hadoop handler implementation that validates the signature.

```
	/** 
 	* Verify the signature of the JWT token in this method. This method depends on the 	* public key that was established during init based upon the provisioned public key. 	* Override this method in subclasses in order to customize the signature verification behavior.
 	* @param jwtToken the token that contains the signature to be validated
 	* @return valid true if signature verifies successfully; false otherwise
 	*/
	protected boolean validateSignature(SignedJWT jwtToken){
  		boolean valid=false;
  		if (JWSObject.State.SIGNED == jwtToken.getState()) {
    		LOG.debug("JWT token is in a SIGNED state");
    		if (jwtToken.getSignature() != null) {
      			LOG.debug("JWT token signature is not null");
      			try {
        			JWSVerifier verifier=new RSASSAVerifier(publicKey);
        			if (jwtToken.verify(verifier)) {
          			valid=true;
          			LOG.debug("JWT token has been successfully verified");
        		}
 			else {
          		LOG.warn("JWT signature verification failed.");
        	}
      	}
 		catch (JOSEException je) {
        	LOG.warn("Error while validating signature",je);
      	}
    }
  	}
  	return valid;
	}
```

Hadoop Configuration Example
The following is like the configuration in the Hadoop handler implementation.

OBSOLETE but in the proper spirit of HADOOP-11717 ( HADOOP-11717 - Add Redirecting WebSSO behavior with JWT Token in Hadoop Auth RESOLVED )

```
	<property>
  		<name>hadoop.http.authentication.type</name>
		<value>org.apache.hadoop/security.authentication/server.JWTRedirectAuthenticationHandler</value>
	</property>
```

This is the handler classname in Hadoop auth for JWT token (KnoxSSO) support.

```
	<property>
  		<name>hadoop.http.authentication.authentication.provider.url</name>
  		<value>http://c6401.ambari.apache.org:8888/knoxsso</value>
	</property>
```

The above property is the SSO provider URL that points to the knoxsso endpoint.

```
	<property>
   		<name>hadoop.http.authentication.public.key.pem</name>
   		<value>MIICVjCCAb+gAwIBAgIJAPPvOtuTxFeiMA0GCSqGSIb3DQEBBQUAMG0xCzAJBgNV
   	BAYTAlVTMQ0wCwYDVQQIEwRUZXN0MQ0wCwYDVQQHEwRUZXN0MQ8wDQYDVQQKEwZI
   	YWRvb3AxDTALBgNVBAsTBFRlc3QxIDAeBgNVBAMTF2M2NDAxLmFtYmFyaS5hcGFj
   	aGUub3JnMB4XDTE1MDcxNjE4NDcyM1oXDTE2MDcxNTE4NDcyM1owbTELMAkGA1UE
   	BhMCVVMxDTALBgNVBAgTBFRlc3QxDTALBgNVBAcTBFRlc3QxDzANBgNVBAoTBkhh
   	ZG9vcDENMAsGA1UECxMEVGVzdDEgMB4GA1UEAxMXYzY0MDEuYW1iYXJpLmFwYWNo
   	ZS5vcmcwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAMFs/rymbiNvg8lDhsdA
   	qvh5uHP6iMtfv9IYpDleShjkS1C+IqId6bwGIEO8yhIS5BnfUR/fcnHi2ZNrXX7x
   	QUtQe7M9tDIKu48w//InnZ6VpAqjGShWxcSzR6UB/YoGe5ytHS6MrXaormfBg3VW
   	tDoy2MS83W8pweS6p5JnK7S5AgMBAAEwDQYJKoZIhvcNAQEFBQADgYEANyVg6EzE
   	2q84gq7wQfLt9t047nYFkxcRfzhNVL3LB8p6IkM4RUrzWq4kLA+z+bpY2OdpkTOe
   	wUpEdVKzOQd4V7vRxpdANxtbG/XXrJAAcY/S+eMy1eDK73cmaVPnxPUGWmMnQXUi
   	TLab+w8tBQhNbq6BOQ42aOrLxA8k/M4cV1A=</value>
	</property>
```

The above property holds the KnoxSSO server’s public key for signature verification. Adding it directly to the config like this is convenient and is easily done through Ambari to existing config files that take custom properties. Config is generally protected as root access only as well - so it is a pretty good solution.

#### Public Key Parsing
In order to turn the pem encoded config item into a public key the hadoop handler implementation does the following in the init() method.

```
   	if (publicKey == null) {
     String pemPublicKey = config.getProperty(PUBLIC_KEY_PEM);
     if (pemPublicKey == null) {
       throw new ServletException(
           "Public key for signature validation must be provisioned.");
     }
     publicKey = CertificateUtil.parseRSAPublicKey(pemPublicKey);
   }
```

and the CertificateUtil class is below:

```
	package org.apache.hadoop.security.authentication.util;

	import java.io.ByteArrayInputStream;
	import java.io.UnsupportedEncodingException;
	import java.security.PublicKey;
	import java.security.cert.CertificateException;
	import java.security.cert.CertificateFactory;
	import java.security.cert.X509Certificate;
	import java.security.interfaces.RSAPublicKey;

	import javax.servlet.ServletException;

	public class CertificateUtil {
		private static final String PEM_HEADER = "-----BEGIN CERTIFICATE-----\n";
		private static final String PEM_FOOTER = "\n-----END CERTIFICATE-----";

	 /**
	  * Gets an RSAPublicKey from the provided PEM encoding.
 	  *
  	  * @param pem
      *          - the pem encoding from config without the header and footer
      * @return RSAPublicKey the RSA public key
      * @throws ServletException thrown if a processing error occurred
      */
 	public static RSAPublicKey parseRSAPublicKey(String pem) throws ServletException {
   		String fullPem = PEM_HEADER + pem + PEM_FOOTER;
   		PublicKey key = null;
   		try {
     		CertificateFactory fact = CertificateFactory.getInstance("X.509");
     		ByteArrayInputStream is = new ByteArrayInputStream(
         		fullPem.getBytes("UTF8"));
     		X509Certificate cer = (X509Certificate) fact.generateCertificate(is);
     		key = cer.getPublicKey();
   		} catch (CertificateException ce) {
     		String message = null;
     		if (pem.startsWith(PEM_HEADER)) {
       			message = "CertificateException - be sure not to include PEM header "
           			+ "and footer in the PEM configuration element.";
     		} else {
       			message = "CertificateException - PEM may be corrupt";
     		}
     		throw new ServletException(message, ce);
   		} catch (UnsupportedEncodingException uee) {
     		throw new ServletException(uee);
   		}
   		return (RSAPublicKey) key;
 		}
	}
```






