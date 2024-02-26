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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

public class ServiceUrls {
  private final List<String> urls;

  public static ServiceUrls fromFile(String urlsFilePath) {
    return fromFile(new File(urlsFilePath));
  }

  public static ServiceUrls fromFile(File urlsFilePath) {
    try {
      List<String> lines = FileUtils.readLines(urlsFilePath, Charset.defaultCharset()).stream()
              .map(String::trim)
              .filter(StringUtils::isNotBlank)
              .collect(Collectors.toList());
      return new ServiceUrls(lines);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public ServiceUrls(List<String> urls) {
    this.urls = urls;
  }

  public List<String> toList() {
    return Collections.unmodifiableList(urls);
  }
}
