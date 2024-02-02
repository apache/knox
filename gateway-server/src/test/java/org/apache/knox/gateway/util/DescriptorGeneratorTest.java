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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.knox.gateway.model.DescriptorConfiguration;
import org.apache.knox.gateway.model.Topology;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DescriptorGeneratorTest {
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final String TEST_DESC_1 = "test_desc1.json";
  private static final String TEST_PROV_1 = "test_prov1.json";
  private static final String IMPALA_UI = "IMPALAUI";
  private static final List<String> URLS =
          Arrays.asList("http://amagyar-1.test.site:25000/", "http://amagyar-2.test.site:25000");

  private static final Map<String,String> PARAMS = new HashMap<>();
  static { PARAMS.put("KEY_1", "VAL_1"); }

  @Rule
  public TemporaryFolder folder= new TemporaryFolder();

  @Test
  public void testCreateDescriptor() throws Exception {
    DescriptorGenerator generator = new DescriptorGenerator(TEST_DESC_1, TEST_PROV_1, IMPALA_UI, new ServiceUrls(URLS), PARAMS);
    File outputDir = folder.newFolder().getAbsoluteFile();
    File outputFile = new File(outputDir, TEST_DESC_1);
    generator.saveDescriptor(outputDir, false);
    System.out.println(FileUtils.readFileToString(outputFile, Charset.defaultCharset()));
    DescriptorConfiguration result = mapper
            .readerFor(DescriptorConfiguration.class)
            .readValue(outputFile);
    assertEquals(FilenameUtils.removeExtension(TEST_PROV_1), result.getProviderConfig());
    assertEquals(FilenameUtils.removeExtension(TEST_DESC_1), result.getName());
    assertEquals(1, result.getServices().size());
    Topology.Service service = result.getServices().get(0);
    assertEquals(IMPALA_UI, service.getRole());
    assertEquals(URLS, service.getUrls());
    assertEquals(1, service.getParams().size());
    assertEquals("KEY_1", service.getParams().get(0).getName());
    assertEquals("VAL_1", service.getParams().get(0).getValue());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testOutputAlreadyExists() throws Exception {
    DescriptorGenerator generator = new DescriptorGenerator(TEST_DESC_1, TEST_PROV_1, IMPALA_UI, new ServiceUrls(URLS), PARAMS);
    File outputDir = folder.newFolder().getAbsoluteFile();
    File outputFile = new File(outputDir, TEST_DESC_1);
    outputFile.createNewFile();
    generator.saveDescriptor(outputDir, false);
  }
}