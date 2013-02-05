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
package org.apache.hadoop.test.mock;

import org.apache.commons.io.IOUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class MockRequestMatcher {

  private MockResponseProvider response;
  private Set<String> methods = null;
  private String pathInfo = null;
  private String requestURL = null;
  Map<String,String> headers = null;
  Set<Cookie> cookies = null;
  private Map<String,Object> attributes = null;
  private Map<String,String> queryParams = null;
  private String contentType = null;
  private String characterEncoding = null;
  private Integer contentLength = null;
  private byte[] entity = null;

  public MockRequestMatcher( MockResponseProvider response ) {
    this.response = response;
  }

  public MockResponseProvider respond() {
    return response;
  }

  public MockRequestMatcher method( String... methods ) {
    if( this.methods == null ) {
      this.methods = new HashSet<String>();
    }
    if( methods != null ) {
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

  public MockRequestMatcher requestURL( String requestURL ) {
    this.requestURL = requestURL;
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

  public MockRequestMatcher attribute( String name, Object value ) {
    if( this.attributes == null ) {
      this.attributes = new HashMap<String,Object>();
    }
    attributes.put( name, value );
    return this;
  }

  public MockRequestMatcher queryParam( String name, String value ) {
    if( this.queryParams == null ) {
      this.queryParams = new HashMap<String, String>();
    }
    queryParams.put( name, value );
    return this;
  }

  public MockRequestMatcher content( String string, Charset charset ) {
    characterEncoding( charset.name() );
    content( string.getBytes( charset ) );
    return this;
  }

  public MockRequestMatcher content( byte[] entity ) {
    this.entity = entity;
    return this;
  }

  public MockRequestMatcher content( URL url ) throws IOException {
    content( url.openStream() );
    return this;
  }

  public MockRequestMatcher content( InputStream stream ) throws IOException {
    content( IOUtils.toByteArray( stream ) );
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
    if( requestURL != null ) {
      assertThat( 
          "Request " + request.getMethod() + " " + request.getRequestURL() +
              " does not have the required requestURL",
          request.getRequestURL().toString(), is( requestURL ) );
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
          requestContentType[ 0 ], is( contentType ) );
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
      if( characterEncoding == null || request.getCharacterEncoding() == null ) {
        byte[] bytes = IOUtils.toByteArray( request.getInputStream() );
        assertThat(
            "Request " + request.getMethod() + " " + request.getRequestURL() +
                " content does not match the required content",
            bytes, is( entity ) );
      } else {
        String expect = new String( entity, characterEncoding );
        String actual = IOUtils.toString( request.getInputStream(), request.getCharacterEncoding() );
        assertThat(
            "Request " + request.getMethod() + " " + request.getRequestURL() +
                " content does not match the required content",
            actual, is( expect ) );
      }
    }
    if( attributes != null ) {
      for( String name: attributes.keySet() ) {
        assertThat(
            "Request " + request.getMethod() + " " + request.getRequestURL() +
                " is missing attribute '" + name + "'",
            request.getAttribute( name ), notNullValue() );
        assertThat(
            "Request " + request.getMethod() + " " + request.getRequestURL() +
                " has wrong value for attribute '" + name + "'",
            request.getAttribute( name ), is( request.getAttribute( name ) ) );
      }
    }
    // Note: Cannot use any of the expect.getParameter*() methods because they will read the
    // body and we don't want that to happen.
    if( queryParams != null ) {
      String queryString = request.getQueryString();
      Map<String,String[]> requestParams = parseQueryString( queryString == null ? "" : queryString );
      for( String name: queryParams.keySet() ) {
        String[] values = requestParams.get( name );
        assertThat(
            "Request " + request.getMethod() + " " + request.getRequestURL() +
                " query string " + queryString + " is missing parameter '" + name + "'",
            values, notNullValue() );
        assertThat(
            "Request " + request.getMethod() + " " + request.getRequestURL() +
                " query string " + queryString + " is missing a value for parameter '" + name + "'",
            Arrays.asList( values ), hasItem( queryParams.get( name ) ) );
      }
    }
  }

  // Separate method to minimally scope the depreciation suppression.
  @SuppressWarnings("deprecation")
  private static Map<String,String[]> parseQueryString( String queryString ) {
    return javax.servlet.http.HttpUtils.parseQueryString( queryString );
  }

}
