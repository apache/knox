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
package org.apache.knox.gateway.util;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.net.URLCodec;

public class Urls {

  public static String ensureLeadingSlash( String s ) {
    if( s == null ) {
      return "/";
    } else if( s.startsWith( "/" ) ) {
      return s;
    } else {
      return "/" + s;
    }
  }

  public static String stripLeadingSlash( String s ) {
    if( s != null ) {
      s = s.trim();
      while( s.startsWith( "/" ) ) {
        s = s.substring(1);
      }
    }
    return s;
  }

  public static String concatUrl( String prefix, String suffix ) {
    if( suffix == null ) {
      return prefix;
    } else if ( prefix.endsWith( "/" ) && suffix.startsWith( "/" ) ) {
      return prefix + stripLeadingSlash( suffix );
    } else if( !prefix.endsWith( "/" ) && !suffix.startsWith( "/" ) ) {
      return prefix + "/" + suffix;
    } else {
      return prefix + suffix;
    }
  }

  public static boolean isIp(String domain) {
    Pattern p = Pattern.compile("^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
    Matcher m = p.matcher(domain);
    return m.find();
  }

  public static int dotOccurrences(String domain) {
    return domain.length() - domain.replace(".", "").length();
  }

  /**
   * Does the provided URL contain UserInfo
   * @param url
   * @return true if a URL contains userInfo else false
   * @throws MalformedURLException
   */
  public static boolean containsUserInfo(String url) throws MalformedURLException {
    return (new URL(url).getUserInfo() != null);
  }

  /**
   * Compute the domain name from an URL.
   *
   * @param url a given URL
   * @param domainSuffix a domain suffix (can be <code>null</code>)
   * @return the extracted domain name
   * @throws MalformedURLException exception on a bad url
   */
  public static String getDomainName(String url, String domainSuffix) throws MalformedURLException {

    final URL originalUrl = new URL(url);
    final String domain = originalUrl.getHost();

    // if the configured domainSuffix is '*' assume that it should be
    // the default domain which should be the FQHN
    if ("*".equals(domainSuffix)) {
      return domain;
    }

    // if the hostname ends with the domainSuffix the use the domainSuffix as
    // the cookie domain
    if (domainSuffix != null && domain.endsWith(domainSuffix)) {
      return (domainSuffix.startsWith(".")) ? domainSuffix : "." + domainSuffix;
    }

    // if accessing via ip address do not wildcard the cookie domain
    // let's use the default domain
    if (isIp(domain)) {
      return null;
    }

    // if there are fewer than 2 dots than this is likely a
    // specific host and we should use the default domain
    if (dotOccurrences(domain) < 2) {
      return null;
    }

    // assume any non-ip address with more than
    // 3 dots will need the first element removed and
    // all subdmains accepted
    int idx = domain.indexOf('.');
    if (idx == -1) {
      idx = 0;
    }
    return domain.substring(idx);
  }

  public static String encode( String str ) {
    URLCodec codec = new URLCodec();
    try {
      return codec.encode( str, StandardCharsets.UTF_8.name() );
    } catch( UnsupportedEncodingException e ) {
      throw new IllegalArgumentException( e );
    }
  }

  public static String decode( String str ) {
    URLCodec codec = new URLCodec();
    try {
      return codec.decode( str, StandardCharsets.UTF_8.name() );
    } catch( UnsupportedEncodingException | DecoderException e ) {
      throw new IllegalArgumentException( e );
    }
  }

  public static String trimLeadingAndTrailingSlash( String s ) {
    if( s == null ) {
      return "";
    } else {
      int b = 0;
      int e = s.length();
      while( b < e && s.charAt( b ) == '/' ) { b++; }
      while( e > b && s.charAt( e-1 ) == '/' ) { e--; }
      return s.substring( b, e );
    }
  }

  public static String trimLeadingAndTrailingSlashJoin( String... parts ) {
    StringBuilder s = new StringBuilder();
    if( parts != null ) {
      String p = "";
      String n;
      for (String part : parts) {
        n = trimLeadingAndTrailingSlash(part);
        if (!n.isEmpty()) {
          if (!p.isEmpty()) {
            s.append('/');
          }
          s.append(n);
          p = n;
        }
      }
    }
    return s.toString();
  }

}
