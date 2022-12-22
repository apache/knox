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

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * This class is a partial match of org.apache.hadoop.util.HttpExceptionUtils to
 * reduce the need for hadoop-common dependency just for creating an error
 * response in Knox.
 */
public class HttpExceptionUtils {

  private static final String ERROR_JSON = "RemoteException";
  private static final String ERROR_EXCEPTION_JSON = "exception";
  private static final String ERROR_CLASSNAME_JSON = "javaClassName";
  private static final String ERROR_MESSAGE_JSON = "message";
  private static final String APPLICATION_JSON_MIME = "application/json";
  private static final String ENTER = System.getProperty("line.separator");
  private static final ObjectWriter WRITER = new ObjectMapper().writerWithDefaultPrettyPrinter();

  /**
   * Creates a HTTP servlet response serializing the exception in it as JSON.
   *
   * @param response the servlet response
   * @param status   the error code to set in the response
   * @param ex       the exception to serialize in the response
   * @throws IOException thrown if there was an error while creating the response
   */
  public static void createServletExceptionResponse(HttpServletResponse response, int status, Throwable ex) throws IOException {
    response.setStatus(status);
    response.setContentType(APPLICATION_JSON_MIME);
    final Map<String, Object> json = new LinkedHashMap<>();
    json.put(ERROR_MESSAGE_JSON, getOneLineMessage(ex));
    json.put(ERROR_EXCEPTION_JSON, ex.getClass().getSimpleName());
    json.put(ERROR_CLASSNAME_JSON, ex.getClass().getName());
    final Map<String, Object> jsonResponse = new LinkedHashMap<>();
    jsonResponse.put(ERROR_JSON, json);
    final Writer responseWriter = response.getWriter();
    WRITER.writeValue(response.getWriter(), jsonResponse);
    responseWriter.flush();
  }

  private static String getOneLineMessage(Throwable exception) {
    String message = exception.getMessage();
    if (message != null) {
      int i = message.indexOf(ENTER);
      if (i > -1) {
        message = message.substring(0, i);
      }
    }
    return message;
  }
}
