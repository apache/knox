/*
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
package org.apache.knox.test.mock;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.xmlmatchers.XmlMatchers.isEquivalentTo;
import static org.xmlmatchers.transform.XmlConverters.the;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

public class MockRequestMatcher {

  private String from;
  private MockResponseProvider response;
  private Set<String> methods;
  private String pathInfo;
  private String requestURI;
  private String requestURL;
  Map<String,Matcher> headers;
  Set<Cookie> cookies;
  private Map<String,Object> attributes;
  private Map<String,String> queryParams;
  private String contentType;
  private String characterEncoding;
  private Integer contentLength;
  private byte[] entity;
  private Map<String,String[]> formParams;

  public MockRequestMatcher( MockResponseProvider response ) {
    this.response = response;
  }

  public MockResponseProvider respond() {
    return response;
  }

  public MockRequestMatcher from( String from ) {
    this.from = from;
    return this;
  }

  public MockRequestMatcher method( String... methods ) {
    if( this.methods == null ) {
      this.methods = new HashSet<>();
    }
    if( methods != null ) {
      Collections.addAll(this.methods, methods);
    }
    return this;
  }

  public MockRequestMatcher pathInfo( String pathInfo ) {
    this.pathInfo = pathInfo;
    return this;
  }

  public MockRequestMatcher requestURI( String requestURI ) {
    this.requestURI = requestURI;
    return this;
  }

  public MockRequestMatcher requestUrl( String requestUrl ) {
    this.requestURL = requestUrl;
    return this;
  }

  public MockRequestMatcher header( String name, String value ) {
    if( headers == null ) {
      headers = new HashMap<>();
    }
    headers.put( name, Matchers.is(value) );
    return this;
  }

  public MockRequestMatcher header( String name, Matcher matcher ) {
    if( headers == null ) {
      headers = new HashMap<>();
    }
    headers.put( name, matcher );
    return this;
  }

  public MockRequestMatcher cookie( Cookie cookie ) {
    if( cookies == null ) {
      cookies = new HashSet<>();
    }
    cookies.add( cookie );
    return this;
  }

  public MockRequestMatcher attribute( String name, Object value ) {
    if( this.attributes == null ) {
      this.attributes = new HashMap<>();
    }
    attributes.put( name, value );
    return this;
  }

  public MockRequestMatcher queryParam( String name, String value ) {
    if( this.queryParams == null ) {
      this.queryParams = new HashMap<>();
    }
    queryParams.put( name, value );
    return this;
  }

  public MockRequestMatcher formParam( String name, String... values ) {
    if( entity != null ) {
      throw new IllegalStateException( "Entity already specified." );
    }
    if( formParams == null ) {
      formParams = new HashMap<>();
    }
    String[] currentValues = formParams.get( name );
    if( currentValues == null ) {
      currentValues = values;
    } else if ( values != null ) {
      currentValues = ArrayUtils.addAll( currentValues, values );
    }
    formParams.put( name, currentValues );
    return this;
  }

  public MockRequestMatcher content( String string, Charset charset ) {
    content( string.getBytes( charset ) );
    return this;
  }

  public MockRequestMatcher content( byte[] entity ) {
    if( formParams != null ) {
      throw new IllegalStateException( "Form params already specified." );
    }
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
              " is not using one of the expected HTTP methods",
          methods, hasItem( request.getMethod() ) );
    }
    if( pathInfo != null ) {
      assertThat(
          "Request " + request.getMethod() + " " + request.getRequestURL() +
              " does not have the expected pathInfo",
          request.getPathInfo(), is( pathInfo ) );
    }
    if( requestURI != null ) {
      assertThat(
          "Request " + request.getMethod() + " " + request.getRequestURL() +
              " does not have the expected requestURI",
          request.getRequestURI(), is(requestURI) );
    }
    if( requestURL != null ) {
      assertThat(
          "Request " + request.getMethod() + " " + request.getRequestURL() +
              " does not have the expected requestURL",
          request.getRequestURL().toString(), is( requestURL ) );
    }
    if( headers != null ) {
      for( Entry<String, Matcher> entry : headers.entrySet() ) {
        assertThat(
            "Request " + request.getMethod() + " " + request.getRequestURL() +
                " does not have the expected value for header " + entry.getKey(),
            request.getHeader( entry.getKey() ),  entry.getValue() );
      }
    }
    if( cookies != null ) {
      List<Cookie> requestCookies = Arrays.asList( request.getCookies() );
      for( Cookie cookie: cookies ) {
        assertThat(
            "Request " + request.getMethod() + " " + request.getRequestURL() +
                " does not have the expected cookie " + cookie,
            requestCookies, hasItem( cookie ) );
      }
    }
    if( contentType != null ) {
      String[] requestContentType = request.getContentType().split(";",2);
      assertThat(
          "Request " + request.getMethod() + " " + request.getRequestURL() +
              " does not have the expected content type",
          requestContentType[ 0 ], is( contentType ) );
    }
    if( characterEncoding != null ) {
      assertThat(
          "Request " + request.getMethod() + " " + request.getRequestURL() +
              " does not have the expected character encoding",
          request.getCharacterEncoding(), equalToIgnoringCase( characterEncoding ) );
    }
    if( contentLength != null ) {
      assertThat(
          "Request " + request.getMethod() + " " + request.getRequestURL() +
              " does not have the expected content length",
          request.getContentLength(), is( contentLength ) );
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
      List<NameValuePair> requestParams = parseQueryString( queryString == null ? "" : queryString );
      for( Entry<String, String> entry : queryParams.entrySet() ) {
        assertThat(
            "Request " + request.getMethod() + " " + request.getRequestURL() +
                " query string " + queryString + " is missing parameter '" + entry.getKey() + "'",
            requestParams, hasItem( new BasicNameValuePair(entry.getKey(), entry.getValue())) );
      }
    }
    if( formParams != null ) {
      String paramString = IOUtils.toString( request.getInputStream(), request.getCharacterEncoding() );
      List<NameValuePair> requestParams = parseQueryString( paramString == null ? "" : paramString );
      for( Entry<String, String[]> entry : formParams.entrySet() ) {
        String[] expectedValues = entry.getValue();
        for( String expectedValue : expectedValues ) {
          assertThat(
              "Request " + request.getMethod() + " " + request.getRequestURL() +
                  " form params " + paramString + " is missing a value " + expectedValue + " for parameter '" + entry.getKey() + "'",
              requestParams, hasItem( new BasicNameValuePair(entry.getKey(), expectedValue ) ));
        }
      }
    }
    if( entity != null ) {
      if( contentType != null && contentType.endsWith( "/xml" ) ) {
        String expectEncoding = characterEncoding;
        String expect = new String( entity, ( expectEncoding == null ? StandardCharsets.UTF_8.name() : expectEncoding ) );
        String actualEncoding = request.getCharacterEncoding();
        String actual = IOUtils.toString( request.getInputStream(), actualEncoding == null ? StandardCharsets.UTF_8.name() : actualEncoding );
        assertThat( the( actual ), isEquivalentTo( the( expect ) ) );
      } else if ( contentType != null && contentType.endsWith( "/json" ) )  {
        String expectEncoding = characterEncoding;
        String expect = new String( entity, ( expectEncoding == null ? StandardCharsets.UTF_8.name() : expectEncoding ) );
        String actualEncoding = request.getCharacterEncoding();
        String actual = IOUtils.toString( request.getInputStream(), actualEncoding == null ? StandardCharsets.UTF_8.name() : actualEncoding );
        assertThat( actual, sameJSONAs( expect ) );
      } else if( characterEncoding == null || request.getCharacterEncoding() == null ) {
        byte[] bytes = IOUtils.toByteArray( request.getInputStream() );
        assertThat(
            "Request " + request.getMethod() + " " + request.getRequestURL() +
                " content does not match the expected content",
            bytes, is( entity ) );
      } else {
        String expect = new String( entity, characterEncoding );
        String actual = IOUtils.toString( request.getInputStream(), request.getCharacterEncoding() );
        assertThat(
            "Request " + request.getMethod() + " " + request.getRequestURL() +
                " content does not match the expected content",
            actual, is( expect ) );
      }
    }
  }

  @Override
  public String toString() {
    return "from=" + from + ", pathInfo=" + pathInfo + ", requestURI=" + requestURI;
  }

  private static List<NameValuePair> parseQueryString( String queryString ) {
    return URLEncodedUtils.parse(queryString, Charset.defaultCharset());
  }

}
