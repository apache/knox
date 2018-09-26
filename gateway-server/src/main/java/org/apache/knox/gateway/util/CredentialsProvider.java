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

import org.apache.http.auth.Credentials;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;

public class CredentialsProvider implements CallbackHandler {

  Credentials credentials;

  public CredentialsProvider( Credentials credentials ) {
    this.credentials = credentials;
  }

  @Override
  public void handle( Callback[] callbacks ) throws IOException, UnsupportedCallbackException {
    for( Callback callback : callbacks ) {
      if( callback instanceof NameCallback ) {
        String username = credentials.getUserPrincipal().getName();
        ((NameCallback)callback).setName( username );
        //System.out.println( "Provided username: " + username );
      } else if ( callback instanceof PasswordCallback ) {
        String password = credentials.getPassword();
        ((PasswordCallback)callback).setPassword( password.toCharArray() );
        //System.out.println( "Provided password: " + password );
      }
    }
  }

}

