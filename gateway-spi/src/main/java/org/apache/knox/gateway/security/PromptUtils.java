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
package org.apache.knox.gateway.security;

import java.io.Console;
import java.util.Arrays;

import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

public class PromptUtils {
  private static GatewaySpiMessages LOG = MessagesFactory.get( GatewaySpiMessages.class );

  public static char[] challengeUserForEstablishingMaterSecret() {
    char[] response = null;
    Console c = System.console();
    if (c == null) {
      LOG.unableToPromptForMasterUseKnoxCLI();
      System.err.println("No console.");
      System.exit(1);
    }

    boolean noMatch;
    do {
        char [] newPassword1 = c.readPassword("Enter master secret: ");
        char [] newPassword2 = c.readPassword("Enter master secret again: ");
        noMatch = ! Arrays.equals(newPassword1, newPassword2);
        if (noMatch) {
            c.format("Passwords don't match. Try again.%n");
        } else {
            response = Arrays.copyOf(newPassword1, newPassword1.length);
        }
        Arrays.fill(newPassword1, ' ');
        Arrays.fill(newPassword2, ' ');
    } while (noMatch);

    return response;
  }

  public static UsernamePassword challengeUserNamePassword(String prompt1, String prompt2) {
    UsernamePassword response;
    Console c = System.console();
    if (c == null) {
      System.err.println("No console.");
      System.exit(1);
    }

    String username = c.readLine(prompt1 + ": ");
    char [] pwd = c.readPassword(prompt2 + ": ");
    response = new UsernamePassword(username, pwd);

    return response;
  }

  public static char[] challengeForPassword(String prompt) {
    char[] response;
    Console c = System.console();
    if (c == null) {
      System.err.println("No console.");
      System.exit(1);
    }

    response = c.readPassword(prompt + ": ");

    return response;
  }
}
