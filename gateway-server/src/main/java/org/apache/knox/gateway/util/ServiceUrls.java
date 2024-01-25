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
