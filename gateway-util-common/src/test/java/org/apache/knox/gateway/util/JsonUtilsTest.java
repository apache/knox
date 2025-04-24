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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class JsonUtilsTest {
  private String expiresIn = "\"expires_in\":\"1364487943100\"";
  private String tokenType = "\"token_type\":\"Bearer\"";
  private String accessToken = "\"access_token\":\"ksdfh3489tyiodhfjk\"";
  private String test = '{' + expiresIn + "," + tokenType + "," + accessToken + '}';

  @Test
  public void testRenderAsJson() {
    Map<String, Object> map = new HashMap<>();
    map.put("access_token", "ksdfh3489tyiodhfjk");
    map.put("token_type", "Bearer");
    map.put( "expires_in", "1364487943100" );

    String result = JsonUtils.renderAsJsonString(map);

    assertThat( result, containsString( expiresIn ) );
    assertThat( result, containsString( tokenType ) );
    assertThat( result, containsString( accessToken ) );
  }

  @Test
  public void testGetMapFromString() {
    HashMap map = (HashMap) JsonUtils.getMapFromJsonString(test);
    assertEquals("ksdfh3489tyiodhfjk", map.get("access_token"));
    assertEquals("Bearer", map.get("token_type"));
    assertEquals("1364487943100", map.get("expires_in"));
  }

  @Test
  public void testFileStatusesAsMap() {
    String json = "{\n" +
      "   \"FileStatuses\":{\n" +
      "      \"FileStatus\":[\n" +
      "         {\n" +
      "            \"accessTime\":0,\n" +
      "            \"blockSize\":0,\n" +
      "            \"childrenNum\":3,\n" +
      "            \"fileId\":16389,\n" +
      "            \"group\":\"supergroup\",\n" +
      "            \"length\":0,\n" +
      "            \"modificationTime\":1581578495905,\n" +
      "            \"owner\":\"hdfs\",\n" +
      "            \"pathSuffix\":\"tmp\",\n" +
      "            \"permission\":\"1777\",\n" +
      "            \"replication\":0,\n" +
      "            \"storagePolicy\":0,\n" +
      "            \"type\":\"DIRECTORY\"\n" +
      "         },\n" +
      "         {\n" +
      "            \"accessTime\":0,\n" +
      "            \"blockSize\":0,\n" +
      "            \"childrenNum\":658,\n" +
      "            \"fileId\":16386,\n" +
      "            \"group\":\"supergroup\",\n" +
      "            \"length\":0,\n" +
      "            \"modificationTime\":1581578527580,\n" +
      "            \"owner\":\"hdfs\",\n" +
      "            \"pathSuffix\":\"user\",\n" +
      "            \"permission\":\"755\",\n" +
      "            \"replication\":0,\n" +
      "            \"storagePolicy\":0,\n" +
      "            \"type\":\"DIRECTORY\"\n" +
      "          } \n" +
      "       ]\n" +
      "   }\n" +
      "}";
    Map<String,HashMap<String, ArrayList<HashMap<String, String>>>> map = JsonUtils.getFileStatusesAsMap(json);
    assertNotNull(map);
  }

    @Test
    public void testRenderAsJsonStringObject() {
        // Create a test object
        TestObject testObject = new TestObject("test-value", 123);

        // Render the object as JSON
        String json = JsonUtils.renderAsJsonString(testObject);

        // Verify the JSON contains the expected values
        assertNotNull(json);
        assertThat(json, containsString("\"stringProperty\" : \"test-value\""));
        assertThat(json, containsString("\"intProperty\" : 123"));
    }

    @Test
    public void testGetObjectFromJsonString() {
        // Create a simple JSON string
        String json = "{\"key1\":\"value1\",\"key2\":42}";

        // Parse the JSON string
        Map<String, Object> result = (Map<String, Object>) JsonUtils.getObjectFromJsonString(json);

        // Verify the parsed object
        assertNotNull(result);
        assertEquals("value1", result.get("key1"));
        assertEquals(42, result.get("key2"));
    }

    @Test
    public void testRenderAsJsonStringWithDateFormat() {
        // Create a test object with a date
        Date testDate = new Date(1609459200000L); // 2020-12-31 00:00:00 UTC
        TestObjectWithDate testObject = new TestObjectWithDate("test-value", testDate);

        // Create a custom date format
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);

        // Render the object as JSON with the custom date format
        String json = JsonUtils.renderAsJsonString(testObject, null, dateFormat);

        // Verify the JSON contains the expected values
        assertNotNull(json);
        assertThat(json, containsString("\"stringProperty\" : \"test-value\""));
        assertThat(json, containsString("\"dateProperty\" : \"2020-12-31\""));
    }

    // Simple test class for JSON serialization
    private static class TestObject {
        private String stringProperty;
        private int intProperty;

        TestObject(String stringProperty, int intProperty) {
            this.stringProperty = stringProperty;
            this.intProperty = intProperty;
        }

        public String getStringProperty() {
            return stringProperty;
        }

        public int getIntProperty() {
            return intProperty;
        }
    }

    // Test class with a date property for testing date formatting
    private static class TestObjectWithDate {
        private String stringProperty;
        private Date dateProperty;

        TestObjectWithDate(String stringProperty, Date dateProperty) {
            this.stringProperty = stringProperty;
            this.dateProperty = dateProperty;
        }

        public String getStringProperty() {
            return stringProperty;
        }

        public Date getDateProperty() {
            return dateProperty;
        }
    }
}
