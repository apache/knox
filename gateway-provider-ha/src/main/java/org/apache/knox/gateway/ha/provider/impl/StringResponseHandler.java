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
package org.apache.knox.gateway.ha.provider.impl;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;

/**
 * Apache HttpClient ResponseHandler for String HttpResponse
 */
public class StringResponseHandler implements ResponseHandler<String>
{
  @Override
  public String handleResponse(HttpResponse response)
  throws ClientProtocolException, IOException
  {
    int status = response.getStatusLine().getStatusCode();

    if (status >= 200 && status < 300)
    {
      HttpEntity entity = response.getEntity();
      return entity != null ?EntityUtils.toString(entity) : null;
    }
    else
    {
      throw new ClientProtocolException("Unexcepted response status: " + status);
    }
  }
}
