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
package org.apache.knox.gateway.shell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.shell.KnoxShellTable.KnoxShellTableCell;
import org.junit.Test;

public class KnoxShellTableTest {
  @Test
  public void testSimpleTableRendering() {
    String expectedResult = "+------------+------------+------------+\n"
        + "|  Column A  |  Column B  |  Column C  |\n" + "+------------+------------+------------+\n"
        + "|    123     |    456     |     3      |\n" + "+------------+------------+------------+\n";

    KnoxShellTable table = new KnoxShellTable();

    table.header("Column A").header("Column B").header("Column C");

    table.row().value("123").value("456").value("3");

    assertEquals(expectedResult, table.toString());
  }

  @Test
  public void testValueLongerThanHeader() {
    String expectedResult = "+------------+------------+--------------+\n"
        + "|  Column A  |  Column B  |   Column C   |\n" + "+------------+------------+--------------+\n"
        + "|    123     |    456     |  344444444   |\n" + "+------------+------------+--------------+\n";

    KnoxShellTable table = new KnoxShellTable();

    table.header("Column A").header("Column B").header("Column C");

    table.row().value("123").value("456").value("344444444");

    assertEquals(expectedResult, table.toString());
  }

  @Test
  public void testMultipleRowsTableRendering() {
    String expectedResult = "+------------+------------+--------------+\n"
        + "|  Column A  |  Column B  |   Column C   |\n" + "+------------+------------+--------------+\n"
        + "|    123     |    456     |  344444444   |\n"
        + "|    789     |    012     |  844444444   |\n" + "+------------+------------+--------------+\n";

    KnoxShellTable table = new KnoxShellTable();

    table.header("Column A").header("Column B").header("Column C");

    table.row().value("123").value("456").value("344444444");
    table.row().value("789").value("012").value("844444444");

    assertEquals(expectedResult, table.toString());
  }

  @Test
  public void testMultipleRowsTableNoHeadersRendering() {
    String expectedResult = "+--------+--------+--------------+\n"
        + "|  123   |  456   |  344444444   |\n"
        + "|  789   |  012   |  844444444   |\n" + "+--------+--------+--------------+\n";

    KnoxShellTable table = new KnoxShellTable();

    table.row().value("123").value("456").value("344444444");
    table.row().value("789").value("012").value("844444444");

    assertEquals(expectedResult, table.toString());

    assertEquals(expectedResult, table.toString());
  }

  @Test(expected = IllegalStateException.class)
  public void testMultipleRowsTableMismatchedColAndHeadersCountError() {
    KnoxShellTable table = new KnoxShellTable();

    table.header("Column A").header("Column B");

    table.row().value("123").value("456").value("344444444");
    table.row().value("789").value("012").value("844444444");

    assertNull(table.toString());
    table.toString();
  }

  @Test
  public void testLoadCSVToAndFromURL() {
//  table = KnoxShellTable.builder().db().driver("HiveDriver2").connect("sdklfjsdjflsd").sql("select * from test;");
    KnoxShellTable table = new KnoxShellTable();
    table.title("From URL");

    table.header("Column A").header("Column B").header("Column C");

    table.row().value("123").value("456").value("344444444");
    table.row().value("789").value("012").value("844444444");

    String csv = table.toCSV();
    try {
      // write file to /tmp to read back in
      FileUtils.writeStringToFile(new File("/tmp/testtable.csv"), csv, StandardCharsets.UTF_8);

      KnoxShellTable urlTable = KnoxShellTable.builder().csv()
          .withHeaders()
          .url("file:///tmp/testtable.csv");
          //.url("http://samplecsvs.s3.amazonaws.com/Sacramentorealestatetransactions.csv");
      urlTable.title("From URL");
      assertEquals(urlTable.toString(), table.toString());

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testToAndFromJSON() throws IOException {
    KnoxShellTable table = new KnoxShellTable();

    table.header("Column A").header("Column B").header("Column C");

    table.row().value("123").value("456").value("344444444");
    table.row().value("789").value("012").value("844444444");

    String json = table.toJSON();

    KnoxShellTable table2 = KnoxShellTable.builder().json().string(json);
    assertEquals(table.toString(), table2.toString());
  }

  @Test
  public void testCells() throws IOException {
    KnoxShellTable table = new KnoxShellTable();

    table.header("Column A").header("Column B").header("Column C");

    table.row().value("123").value("456").value("344444444");
    table.row().value("789").value("012").value("844444444");

    KnoxShellTableCell cell = table.cell(1, 1);
    assertEquals(cell.header(), "Column B");
    assertEquals(cell.value(), "012");
    cell.header("Column Beeee");
    cell.value("234");
    table.apply(cell);
    assertEquals(table.cell(1, 1).value(), "234");
    assertEquals(table.cell(1, 1).header(), "Column Beeee");
  }

  @Test
  public void testFilterTable() {
    KnoxShellTable table = new KnoxShellTable();

    table.header("Column A").header("Column B").header("Column C");

    table.row().value("123").value("456").value("344444444");
    table.row().value("789").value("012").value("844444444");

    KnoxShellTable filtered = table.filter().name("Column A").regex("123");

    assertEquals(filtered.getRows().size(), 1);
  }

  @Test
  public void testJoinTables() throws IOException {
    KnoxShellTable table = new KnoxShellTable();

    table.title("Left Table").header("Column A").header("Column B").header("Column C");

    table.row().value("123").value("456").value("344444444");
    table.row().value("789").value("012").value("844444444");

    KnoxShellTable table2 = new KnoxShellTable();

    table2.title("Right Table").header("Column D").header("Column E").header("Column F");

    table2.row().value("123").value("367").value("244444444");
    table2.row().value("780").value("908").value("944444444");

    KnoxShellTable joined = KnoxShellTable.builder().join().left(table).right(table2).on(0, 0);
    joined.title("Joined Table");

    assertEquals(joined.getRows().size(), 1);
    assertEquals(joined.getTitle(), "Joined Table");
    assertEquals(joined.cell(0, 0).value(), "123");
    String json = joined.toJSON();

    KnoxShellTable zombie = KnoxShellTable.builder().json().string(json);
    zombie.title("Zombie Table");

    assertEquals(zombie.getRows().size(), 1);
    assertEquals(zombie.getTitle(), "Zombie Table");
    assertEquals(zombie.cell(0, 0).value(), "123");
  }
}
