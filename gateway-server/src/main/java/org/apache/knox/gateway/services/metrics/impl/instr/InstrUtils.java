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
package org.apache.knox.gateway.services.metrics.impl.instr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InstrUtils {

    //This regular expression pattern is used to parse the *first* two elements
    //of a path. For example, if the path is “/webhdfs/v1/d1/d2/d2/d4”, this pattern
    //can be used to get the first two ("/webhdfs/v1/"). The "?" in pattern
    //ensures not to be greedy in matching.
    private static final Pattern p = Pattern.compile("/.*?/.*?/");

    //This regular expression pattern is used to parse the *first* element
    //of a path. For example, if the path is “/webhdfs/v1/d1/d2/d2/d4”, this pattern
    //can be used to get the first ("/webhdfs/"). The "?" in pattern
    //ensures not to be greedy in matching.
    private static final Pattern serviceP = Pattern.compile("/.*?/");

    /**
     * This function parses the pathinfo provided  in any servlet context and
     * returns the segment that is related to the resource.
     * For example, if the path is "/webhdfs/v1/d1/d2/d2/d4". it returns "/webhdfs/v1"
     *
     * @param fullPath full path to determine the resource from
     * @return resource path
     */
    public static String getResourcePath(String fullPath) {
        return getMatchedPath(fullPath, p);
    }

    /**
     * This function parses the pathinfo provided  in any servlet context and
     * returns the segment that is related to the resource.
     * For example, if the path is "/webhdfs/v1/d1/d2/d2/d4". it returns "/webhdfs/v1"
     *
     * @param fullPath full path to determine the resource from
     * @return resource path
     */
    public static String getServiceResourcePath(String fullPath) {
        return getMatchedPath(fullPath, serviceP);
    }

    /**
     * This function return match Pattern pathinfo
     * @param fullPath full path to determine the resource from
     * @param pattern this regular expression pattern is used to parse element
     * @return matched path
     */
    private static String getMatchedPath(String fullPath, Pattern pattern) {
        String resourcePath = "";
        if (fullPath != null && !fullPath.isEmpty()) {
            Matcher m = pattern.matcher(fullPath);
            if (m.find()) {
                resourcePath = m.group(0);
            } else {
                resourcePath = fullPath;
            }
        }
        return resourcePath;
    }

}
