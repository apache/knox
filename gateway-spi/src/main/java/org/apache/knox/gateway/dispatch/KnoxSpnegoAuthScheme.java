/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.dispatch;

import org.apache.http.impl.auth.SPNegoScheme;
import org.ietf.jgss.GSSException;

public class KnoxSpnegoAuthScheme extends SPNegoScheme {
  private static long nano = Long.MIN_VALUE;

  public KnoxSpnegoAuthScheme( boolean stripPort ) {
    super( stripPort );
  }

  public KnoxSpnegoAuthScheme() {
    super();
  }

  @Override
  protected byte[] generateToken(final byte[] input, final String authServer) throws GSSException {
    // This is done to avoid issues with Keberos service ticket replay detection on the service side.
    synchronized( KnoxSpnegoAuthScheme.class ) {
      long now;
      // This just insures that the system clock has advanced to a different nanosecond.
      // Kerberos uses microsecond resolution and 1ms=1000ns.
      while( ( now = System.nanoTime() ) == nano ) {
        try {
          Thread.sleep( 0 );
        } catch( InterruptedException e ) {
          Thread.currentThread().interrupt();
        }
      }
      nano = now;
      return super.generateToken( input, authServer );
    }
  }

}
