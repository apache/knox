package org.apache.knox.gateway.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

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
  @Rule
  public TemporaryFolder folder= new TemporaryFolder();

  @Test
  public void testCreateDescriptor() throws Exception {
    DescriptorGenerator generator = new DescriptorGenerator(TEST_DESC_1, TEST_PROV_1, IMPALA_UI, new ServiceUrls(URLS));
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
  }

  @Test(expected = IllegalArgumentException.class)
  public void testOutputAlreadyExists() throws Exception {
    DescriptorGenerator generator = new DescriptorGenerator(TEST_DESC_1, TEST_PROV_1, IMPALA_UI, new ServiceUrls(URLS));
    File outputDir = folder.newFolder().getAbsoluteFile();
    File outputFile = new File(outputDir, TEST_DESC_1);
    outputFile.createNewFile();
    generator.saveDescriptor(outputDir, false);
  }
}