/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletRequest;

public class RequestBodyUtils {

  public static String getRequestBodyParameter(ServletRequest request, String parameter) throws IOException {
    return getRequestBodyParameter(request, parameter, false);
  }

  public static String getRequestBodyParameter(ServletRequest request, String parameter, boolean decode) throws IOException {
    return getRequestBodyParameter(request.getInputStream(), parameter, decode);
  }

  public static String getRequestBodyParameter(InputStream inputStream, String parameter) throws IOException {
    return getRequestBodyParameter(inputStream, parameter, false);
  }

  public static String getRequestBodyParameter(InputStream inputStream, String parameter, boolean decode) throws IOException {
    return getRequestBodyParameter(new InputStreamReader(inputStream, StandardCharsets.UTF_8), parameter, decode);
  }

  public static String getRequestBodyParameter(Reader reader, String parameter) throws IOException {
    return getRequestBodyParameter(reader, parameter, false);
  }

  public static String getRequestBodyParameter(Reader reader, String parameter, boolean decode) throws IOException {
    final BufferedReader bufferedReader = new BufferedReader(reader);
    final StringBuilder requestBodyBuilder = new StringBuilder();
    String line;
    while ((line = bufferedReader.readLine()) != null) {
      requestBodyBuilder.append(line);
    }

    final String requestBodyString = decode ? URLDecoder.decode(requestBodyBuilder.toString(), StandardCharsets.UTF_8.name()) : requestBodyBuilder.toString();
    return getRequestBodyParameter(requestBodyString, parameter);
  }

  public static String getRequestBodyParameter(String requestBodyString, String parameter) {
    if (requestBodyString != null) {
      final String[] requestBodyParams = requestBodyString.split("&");
      for (String requestBodyParam : requestBodyParams) {
        String[] keyValue = requestBodyParam.split("=", 2);
        if (parameter.equals(keyValue[0])) {
          return keyValue[1];
        }
      }
    }
    return null;
  }
}
