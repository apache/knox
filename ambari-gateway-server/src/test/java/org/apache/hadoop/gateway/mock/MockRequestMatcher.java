/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.gateway.mock;

import org.apache.commons.io.IOUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.hasItem;

public class MockRequestMatcher {

  private MockResponseProvider response;
  private Set<String> methods = null;
  private String pathInfo = null;
  Map<String,String> headers = null;
  Set<Cookie> cookies = null;
  private Map<String,String> params = null;
  private String contentType = null;
  private String characterEncoding = null;
  private Integer contentLength = null;
  private byte[] entity = null;
//    request.getAuthType()
//    request.getContextPath()
//    request.getQueryString()
//    request.getRemoteUser()
//    request.getRequestedSessionId()
//    request.getRequestURI()
//    request.getRequestURL()
//    request.getRemoteAddr()
//    request.getRemoteHost()
//    request.getRemotePort()
//    request.getServletPath()
//    request.getScheme()
//    request.getUserPrincipal()

  public MockRequestMatcher( MockResponseProvider response ) {
    this.response = response;
  }

  public MockResponseProvider response() {
    return response;
  }

  public MockRequestMatcher method( String... methods ) {
    if( methods == null ) {
      this.methods = new HashSet<String>();
      for( String method: methods ) {
        this.methods.add( method );
      }
    }
    return this;
  }

  public MockRequestMatcher pathInfo( String pathInfo ) {
    this.pathInfo = pathInfo;
    return this;
  }

  public MockRequestMatcher header( String name, String value ) {
    if( headers == null ) {
      headers = new HashMap<String, String>();
    }
    headers.put( name, value );
    return this;
  }

  public MockRequestMatcher cookie( Cookie cookie ) {
    if( cookies == null ) {
      cookies = new HashSet<Cookie>();
    }
    cookies.add( cookie );
    return this;
  }

  public MockRequestMatcher param( String name, String value ) {
    if( this.params == null ) {
      this.params = new HashMap<String, String>();
    }
    params.put( name, value );
    return this;
  }

  public MockRequestMatcher entity( byte[] entity ) {
    this.entity = entity;
    return this;
  }

  public MockRequestMatcher entity( URL url ) throws IOException {
    entity( url.openStream() );
    return this;
  }

  public MockRequestMatcher entity( InputStream stream ) throws IOException {
    entity( IOUtils.toByteArray( stream ) );
    return this;
  }

  public MockRequestMatcher contentType( String contentType ) {
    this.contentType = contentType;
    return this;
  }

  public MockRequestMatcher contentLength( int length ) {
    this.contentLength = length;
    return this;
  }

  public MockRequestMatcher characterEncoding( String charset ) {
    this.characterEncoding = charset;
    return this;
  }

  public void match( HttpServletRequest request ) throws IOException {
    if( methods != null ) {
      assertThat(
           "Request " + request.getMethod() + " " + request.getRequestURL() +
                " is not using one of the required HTTP methods",
           methods, hasItem( request.getMethod() ) );
    }
    if( pathInfo != null ) {
      assertThat(
          "Request " + request.getMethod() + " " + request.getRequestURL() +
               " does not have the required pathInfo",
          request.getPathInfo(), is( pathInfo ) );
    }
    if( headers != null ) {
      for( String name: headers.keySet() ) {
        assertThat(
            "Request " + request.getMethod() + " " + request.getRequestURL() +
                 " does not have the required value for header " + name,
            request.getHeader( name ), is( headers.get( name ) ) );
      }
    }
    if( cookies != null ) {
      List<Cookie> requestCookies = Arrays.asList( request.getCookies() );
      for( Cookie cookie: cookies ) {
        assertThat(
            "Request " + request.getMethod() + " " + request.getRequestURL() +
                 " does not have the required cookie " + cookie,
            requestCookies, hasItem( cookie ) );
      }
    }
    if( contentType != null ) {
      String[] requestContentType = request.getContentType().split(";",2);
      assertThat(
          "Request " + request.getMethod() + " " + request.getRequestURL() +
              " does not have the required content type",
          requestContentType[0], is( contentType ) );
    }
    if( characterEncoding != null ) {
      assertThat(
          "Request " + request.getMethod() + " " + request.getRequestURL() +
              " does not have the required character encoding",
          request.getCharacterEncoding(), is( characterEncoding ) );
    }
    if( contentLength != null ) {
      assertThat(
          "Request " + request.getMethod() + " " + request.getRequestURL() +
              " does not have the required content length",
          request.getContentLength(), is( contentLength ) );
    }
    if( entity != null ) {
      byte[] bytes = IOUtils.toByteArray( request.getInputStream() );
      assertThat(
          "Request " + request.getMethod() + " " + request.getRequestURL() +
              " content does not match the required content",
          bytes, is( entity ) );
    }
    // Note: Cannot use any of the request.getParameter*() methods because they will ready the request
    // body and we don't want that to happen.
    if( params != null ) {
      String queryString = request.getQueryString();
      Map<String,String[]> requestParams = parseQueryString( queryString == null ? "" : queryString );
      for( String name: params.keySet() ) {
        String[] values = requestParams.get( name );
        assertThat(
            "Request " + request.getMethod() + " " + request.getRequestURL() +
            " query string " + queryString + " is missing parameter '" + name + "'",
            values, notNullValue() );
        assertThat(
            "Request " + request.getMethod() + " " + request.getRequestURL() +
            " query string " + queryString + " is missing a value for parameter '"+name+"'",
            Arrays.asList( values ), hasItem( params.get( name ) ) );
      }
    }
  }

  @SuppressWarnings("deprecation")
  private static Map<String,String[]> parseQueryString( String queryString ) {
    return javax.servlet.http.HttpUtils.parseQueryString( queryString );
  }

}
