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
package org.apache.knox.gateway.shell.table;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import org.junit.BeforeClass;
import org.junit.Test;

public class KnoxShellTableFilterTest {
  private static final DateFormat TEST_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

  private static final KnoxShellTable TABLE = new KnoxShellTable();

  @BeforeClass
  public static void init() throws ParseException {
    TABLE.header("Column Integer").header("Column Double").header("Column String").header("Column Date");
    synchronized (TEST_DATE_FORMAT) { //sync is needed due to https://pmd.github.io/latest/pmd_rules_java_multithreading.html#unsynchronizedstaticformatter
      TABLE.row().value(10).value(20d).value("text30").value(TEST_DATE_FORMAT.parse("2010-01-01"));
      TABLE.row().value(30).value(40d).value("text50").value(TEST_DATE_FORMAT.parse("2012-01-01"));
      TABLE.row().value(50).value(60d).value("text60").value(TEST_DATE_FORMAT.parse("2012-02-01"));
    }
  }

  @Test
  public void testRegexFilter() throws KnoxShellTableFilterException {
    final KnoxShellTable filtered = TABLE.filter().name("Column Integer").regex("30");
    assertEquals(filtered.getRows().size(), 1);
  }

  @Test
  public void testNameMethod() throws KnoxShellTableFilterException {
    assertEquals(TABLE.filter().name("cOlUmN iNtEgEr").greaterThan(10).getRows().size(), 2);
    assertEquals(TABLE.filter().name("COLUMN DOUBLE").greaterThan(25d).getRows().size(), 2);
    assertEquals(TABLE.filter().name("column string").greaterThan("text20").getRows().size(), 3);
  }

  @Test
  public void testGreaterThanFilter() throws KnoxShellTableFilterException, ParseException {
    assertEquals(TABLE.filter().name("Column Integer").greaterThan(5).getRows().size(), 3);
    assertEquals(TABLE.filter().name("Column Integer").greaterThan(25).getRows().size(), 2);

    assertEquals(TABLE.filter().name("Column Double").greaterThan(10d).getRows().size(), 3);
    assertEquals(TABLE.filter().name("Column Double").greaterThan(30d).getRows().size(), 2);

    assertEquals(TABLE.filter().name("Column String").greaterThan("text20").getRows().size(), 3);
    assertEquals(TABLE.filter().name("Column String").greaterThan("text30").getRows().size(), 2);

    synchronized (TEST_DATE_FORMAT) {
      assertEquals(TABLE.filter().name("Column Date").greaterThan(TEST_DATE_FORMAT.parse("2009-12-31")).getRows().size(), 3);
      assertEquals(TABLE.filter().name("Column Date").greaterThan(TEST_DATE_FORMAT.parse("2011-01-01")).getRows().size(), 2);
    }
  }

  @Test
  public void testLessThanFilter() throws KnoxShellTableFilterException, ParseException {
    assertEquals(TABLE.filter().name("Column Integer").lessThan(20).getRows().size(), 1);
    assertEquals(TABLE.filter().name("Column Integer").lessThan(100).getRows().size(), 3);

    assertEquals(TABLE.filter().name("Column Double").lessThan(30d).getRows().size(), 1);
    assertEquals(TABLE.filter().name("Column Double").lessThan(100d).getRows().size(), 3);

    assertEquals(TABLE.filter().name("Column String").lessThan("text40").getRows().size(), 1);
    assertEquals(TABLE.filter().name("Column String").lessThan("text70").getRows().size(), 3);

    synchronized (TEST_DATE_FORMAT) {
      assertEquals(TABLE.filter().name("Column Date").lessThan(TEST_DATE_FORMAT.parse("2011-12-31")).getRows().size(), 1);
      assertEquals(TABLE.filter().name("Column Date").lessThan(TEST_DATE_FORMAT.parse("2013-01-01")).getRows().size(), 3);
    }
  }

  @Test
  public void testGreaterThanOrEqualToThanFilter() throws KnoxShellTableFilterException, ParseException {
    assertEquals(TABLE.filter().name("Column Integer").greaterThanOrEqualTo(30).getRows().size(), 2);
    assertEquals(TABLE.filter().name("Column Integer").greaterThanOrEqualTo(50).getRows().size(), 1);

    assertEquals(TABLE.filter().name("Column Double").greaterThanOrEqualTo(40d).getRows().size(), 2);
    assertEquals(TABLE.filter().name("Column Double").greaterThanOrEqualTo(20d).getRows().size(), 3);

    assertEquals(TABLE.filter().name("Column String").greaterThanOrEqualTo("text50").getRows().size(), 2);
    assertEquals(TABLE.filter().name("Column String").greaterThanOrEqualTo("text30").getRows().size(), 3);

    synchronized (TEST_DATE_FORMAT) {
      assertEquals(TABLE.filter().name("Column Date").greaterThanOrEqualTo(TEST_DATE_FORMAT.parse("2009-12-31")).getRows().size(), 3);
      assertEquals(TABLE.filter().name("Column Date").greaterThanOrEqualTo(TEST_DATE_FORMAT.parse("2012-01-01")).getRows().size(), 2);
    }
  }

  @Test
  public void testLessThanOrEqualToThanFilter() throws KnoxShellTableFilterException, ParseException {
    assertEquals(TABLE.filter().name("Column Integer").lessThanOrEqualTo(30).getRows().size(), 2);
    assertEquals(TABLE.filter().name("Column Integer").lessThanOrEqualTo(50).getRows().size(), 3);

    assertEquals(TABLE.filter().name("Column Double").lessThanOrEqualTo(40d).getRows().size(), 2);
    assertEquals(TABLE.filter().name("Column Double").lessThanOrEqualTo(20d).getRows().size(), 1);

    assertEquals(TABLE.filter().name("Column String").lessThanOrEqualTo("text50").getRows().size(), 2);
    assertEquals(TABLE.filter().name("Column String").lessThanOrEqualTo("text30").getRows().size(), 1);

    synchronized (TEST_DATE_FORMAT) {
      assertEquals(TABLE.filter().name("Column Date").lessThanOrEqualTo(TEST_DATE_FORMAT.parse("2012-02-01")).getRows().size(), 3);
      assertEquals(TABLE.filter().name("Column Date").lessThanOrEqualTo(TEST_DATE_FORMAT.parse("2010-01-01")).getRows().size(), 1);
    }
  }

  @Test
  public void testEqualToFilter() throws KnoxShellTableFilterException, ParseException {
    assertEquals(TABLE.filter().name("Column Integer").equalTo(60).getRows().size(), 0);
    assertEquals(TABLE.filter().name("Column Integer").equalTo(50).getRows().size(), 1);

    assertEquals(TABLE.filter().name("Column Double").equalTo(40d).getRows().size(), 1);
    assertEquals(TABLE.filter().name("Column Double").equalTo(90d).getRows().size(), 0);

    assertEquals(TABLE.filter().name("Column String").equalTo("text90").getRows().size(), 0);
    assertEquals(TABLE.filter().name("Column String").equalTo("text30").getRows().size(), 1);

    synchronized (TEST_DATE_FORMAT) {
      assertEquals(TABLE.filter().name("Column Date").equalTo(TEST_DATE_FORMAT.parse("2012-02-01")).getRows().size(), 1);
      assertEquals(TABLE.filter().name("Column Date").equalTo(TEST_DATE_FORMAT.parse("2016-01-01")).getRows().size(), 0);
    }
  }

  @Test
  public void testIncomparableColumns() {
    boolean failed = false;
    try {
      TABLE.filter().name("Column Double").greaterThan("20");
    } catch (KnoxShellTableFilterException e) {
      failed = true;
    }
    assertTrue(failed);
  }
}
