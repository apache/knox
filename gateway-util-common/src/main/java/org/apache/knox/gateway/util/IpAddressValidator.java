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
import java.util.Collections;

/**
 * Validate a given IP Address against a list of comma separated list of addresses.
 */
public class IpAddressValidator {
  
  /**
   * The parsed list of ip addresses 
   */
  private ArrayList<String> ipaddr = new ArrayList<>();
  
  /**
   * IP addresses from the ipaddr list that contain a wildcard character '*'
   */
  private ArrayList<String> wildCardIPs = new ArrayList<>();
  
  /**
   * Optimization based on empty IP address list or an explicit '*' wildcard
   */
  private boolean anyIP = true;

  /**
   * ctor - initialize an instance with the given ip address list
   */
  public IpAddressValidator(String commaSeparatedIpAddresses) {
    if (commaSeparatedIpAddresses == null) {
      anyIP = true;
      return;
    }
    
    parseIpAddesses(commaSeparatedIpAddresses);
  }

  /**
   * @param commaSeparatedIpAddresses
   */
  private void parseIpAddesses(String commaSeparatedIpAddresses) {
    String[] ips = commaSeparatedIpAddresses.split(",");
    ipaddr = new ArrayList<>();
    wildCardIPs = new ArrayList<>();
    Collections.addAll(ipaddr, ips);
    if (!ipaddr.contains("*")) {
      anyIP = false;
      // check whether there are any wildcarded ip's - example: 192.* or 192.168.* or 192.168.1.*
      for (String addr : ipaddr) {
        if (addr.contains("*")) {
          wildCardIPs.add(addr.substring(0, addr.lastIndexOf('*')));
        }
      }
    }
  }
  
  public boolean validateIpAddress(String addr) {
    boolean valid = false;
    if (addr == null) {
      // LJM TODO: log as possible programming error
      return false;
    }
    
    if (anyIP) {
      valid = true;
    }
    else {
      if (ipaddr.contains(addr)) {
        valid = true;
      }
      else {
        // check for wildcards if there are wildcardIP acls configured
        if (!wildCardIPs.isEmpty()) {
          for (String ip : wildCardIPs) {
            if (addr.startsWith(ip)) {
              valid = true;
              break;
            }
          }
        }
      }
    }
    return valid;
  }

  /**
   * @return
   */
  public boolean allowsAnyIP() {
    return anyIP;
  }

  /**
   * @return
   */
  public ArrayList<String> getIPAddresses() {
    return ipaddr;
  }
}
