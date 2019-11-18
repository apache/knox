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

import java.util.ArrayList;
import java.util.List;

import javax.security.auth.x500.X500Principal;

public class X500PrincipalParser {
  public static final int LEASTSIGNIFICANT = 0;
  public static final int MOSTSIGNIFICANT = 1;

  public static final String attrCN = "CN";
  public static final String attrOU = "OU";
  public static final String attrO = "O";
  public static final String attrC = "C";
  public static final String attrL = "L";
  public static final String attrST = "ST";
  public static final String attrSTREET = "STREET";
  public static final String attrEMAIL = "EMAILADDRESS";
  public static final String attrUID = "UID";

  private List<List<String>> rdnNameArray = new ArrayList<>();

  private static final String attrTerminator = "=";

  public X500PrincipalParser(X500Principal principal) {
    parseDN(principal.getName(X500Principal.RFC2253));
  }

  public List<Object> getAllValues(String attributeID) {
    List<Object> retList = new ArrayList<>();
    String searchPart = attributeID + attrTerminator;

    for (List<String> nameList : rdnNameArray) {
      String namePart = nameList.get(0);

      if (namePart.startsWith(searchPart)) {
        // Return the string starting after the ID string and the = sign that follows it.
        retList.add(namePart.substring(searchPart.length()));
      }
    }

    return retList;
  }

  public String getC() {
    return findPart(attrC);
  }

  public String getCN() {
    return findPart(attrCN);
  }

  public String getEMAILDDRESS() {
    return findPart(attrEMAIL);
  }

  public String getL() {
    return findPart(attrL);
  }

  public String getO() {
    return findPart(attrO);
  }

  public String getOU() {
    return findPart(attrOU);
  }

  public String getST() {
    return findPart(attrST);
  }

  public String getSTREET() {
    return findPart(attrSTREET);
  }

  public String getUID() {
    return findPart(attrUID);
  }

  private String findPart(String attributeID) {
    return findSignificantPart(attributeID, MOSTSIGNIFICANT);
  }

  private String findSignificantPart(String attributeID, int significance) {
    String retNamePart = null;
    String searchPart = attributeID + attrTerminator;

    for (Object o : rdnNameArray) {
      ArrayList nameList = (ArrayList) o;
      String namePart = (String) nameList.get(0);

      if (namePart.startsWith(searchPart)) {
        // Return the string starting after the ID string and the = sign that follows it.
        retNamePart = namePart.substring(searchPart.length());
        // By definition the first one is most significant
        if (significance == MOSTSIGNIFICANT) {
          break;
        }
      }
    }

    return retNamePart;
  }

  private void parseDN(String dn) throws IllegalArgumentException {
    int startIndex = 0;
    char c = '\0';
    List<String> nameValues = new ArrayList<>();

    rdnNameArray.clear();

    boolean terminatedProperly = true;
    while(startIndex < dn.length()) {
      int endIndex;
      for(endIndex = startIndex; endIndex < dn.length(); endIndex++) {
        c = dn.charAt(endIndex);
        if(c == ',' || c == '+') {
          break;
        }
        if(c == '\\') {
          endIndex++; // skip the escaped char
        }
      }

      if(endIndex > dn.length()) {
        throw new IllegalArgumentException("unterminated escape " + dn);
      }

      nameValues.add(dn.substring(startIndex, endIndex));

      if(c != '+') {
        rdnNameArray.add(nameValues);
        if(endIndex != dn.length()) {
          nameValues = new ArrayList<>();
          terminatedProperly = false;
        } else {
          terminatedProperly = true;
        }
      }
      startIndex = endIndex + 1;
    }
    if(!terminatedProperly) {
      throw new IllegalArgumentException("improperly terminated DN " + dn);
    }
  }
}
