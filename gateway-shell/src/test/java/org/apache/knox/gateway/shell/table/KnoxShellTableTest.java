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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.shell.table.KnoxShellTable;
import org.apache.knox.gateway.shell.table.KnoxShellTableCell;
import org.easymock.IAnswer;
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
  public void testTableSelect() {
    String expectedResult = "+------------+------------+--------------+\n"
        + "|  Column A  |  Column B  |   Column C   |\n" + "+------------+------------+--------------+\n"
        + "|    123     |    456     |  344444444   |\n"
        + "|    789     |    012     |  844444444   |\n" + "+------------+------------+--------------+\n";

    String expectedResult2 = "+------------+--------------+\n"
        + "|  Column A  |   Column C   |\n" + "+------------+--------------+\n"
        + "|    123     |  344444444   |\n"
        + "|    789     |  844444444   |\n" + "+------------+--------------+\n";

    KnoxShellTable table = new KnoxShellTable();

    table.header("Column A").header("Column B").header("Column C");

    table.row().value("123").value("456").value("344444444");
    table.row().value("789").value("012").value("844444444");

    assertEquals(expectedResult, table.toString());

    table = table.select("Column A,Column C");
    assertEquals(expectedResult2, table.toString());
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

    KnoxShellTable table2 = KnoxShellTable.builder().json().fromJson(json);
    assertEquals(table.toString(), table2.toString());
  }

  @Test
  public void testSort() throws IOException {
    KnoxShellTable table = new KnoxShellTable();

    table.header("Column A").header("Column B").header("Column C");

    table.row().value("789").value("012").value("844444444");
    table.row().value("123").value("456").value("344444444");

    KnoxShellTable table2 = table.sort("Column A");
    assertEquals(table2.getRows().get(0).get(0), "123");
    assertEquals(table2.getRows().get(1).get(0), "789");
  }

  @Test
  public void testCells() throws IOException {
    KnoxShellTable table = new KnoxShellTable();

    table.header("Column A").header("Column B").header("Column C");

    table.row().value("123").value("456").value("344444444");
    table.row().value("789").value("012").value("844444444");

    KnoxShellTableCell cell = table.cell(1, 1);
    assertEquals(cell.header, "Column B");
    assertEquals(cell.value, "012");
    cell.header("Column Beeee");
    cell.value("234");
    table.apply(cell);
    assertEquals(table.cell(1, 1).value, "234");
    assertEquals(table.cell(1, 1).header, "Column Beeee");
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

    table.title("Left Table").header("Column A").header("Column B").header("Column C").header("Column D");

    table.row().value("123").value("456").value("344444444").value("2");
    table.row().value("789").value("012").value("844444444").value("2");
    table.row().value("980").value("2").value("844444444").value("2");

    KnoxShellTable table2 = new KnoxShellTable();

    table2.title("Right Table").header("Column D").header("Column E").header("Column F").header("Column G");

    table2.row().value("123").value("367").value("244444444").value("2");
    table2.row().value("780").value("908").value("944444444").value("3");

    KnoxShellTable joined = KnoxShellTable.builder().join().title("Joined Table").left(table).right(table2).on(0, 0);

    assertEquals(joined.getRows().size(), 1);
    assertEquals(joined.getTitle(), "Joined Table");
    assertEquals(joined.cell(0, 0).value, "123");
    String json = joined.toJSON();

    KnoxShellTable zombie = KnoxShellTable.builder().json().fromJson(json);
    zombie.title("Zombie Table");

    assertEquals(zombie.getRows().size(), 1);
    assertEquals(zombie.getTitle(), "Zombie Table");
    assertEquals(zombie.cell(0, 0).value, "123");
    KnoxShellTable joined2 = KnoxShellTable.builder().join().title("Joined Table 2").left(table).right(table2).on(1, 3);
    assertEquals(1, joined2.getRows().size());
  }

  @Test
  public void testJDBCBuilderUnManagedConnection() throws Exception {
    Connection connection = createMock(Connection.class);
    Statement statement = createMock(Statement.class);
    ResultSet resultSet = createMock(ResultSet.class);
    ResultSetMetaData metadata = createMock(ResultSetMetaData.class);
    try {
    expect(connection.createStatement()).andReturn(statement);
    expect(statement.executeQuery("select * from book")).andReturn(resultSet);
    expect(resultSet.getMetaData()).andReturn(metadata);
    if (resultSet.next()) {
      // intentionally empty in order to pass PMC and checkstyle!
    }
    expectLastCall().andAnswer(new IAnswer<Boolean>() {
      int called;
      @Override
      public Boolean answer() {
        called++;
        if (called < 2) {
          return true;
        }
        return false;
      }
    }).times(2);
    expect(resultSet.getString("BOOK_ID")).andReturn("1").times(1);
    expect(resultSet.getString("TITLE")).andReturn("Apache Knox: The Definitive Guide").times(1);
    expect(metadata.getTableName(1)).andReturn("BOOK");
    expect(metadata.getColumnCount()).andReturn(2);
    expect(metadata.getColumnName(1)).andReturn("BOOK_ID").anyTimes();
    expect(metadata.getColumnName(2)).andReturn("TITLE").anyTimes();
    resultSet.close();
    expectLastCall().andAnswer(new IAnswer<Object>() {
      @Override
      public Object answer() {
          return null;
      }
    }).times(2);
    // should close the statement
    statement.close();
    expectLastCall().andAnswer(new IAnswer<Object>() {
      @Override
      public Object answer() {
          return null;
      }
    }).times(2);
    connection.close();
    expectLastCall().andAnswer(new IAnswer<Object>() {
      @Override
      public Object answer() {
          return null;
      }
    });
    replay(connection, statement, resultSet, metadata);

    KnoxShellTable table = KnoxShellTable.builder().jdbc().connection(connection).sql("select * from book");
    assertEquals(1, table.getRows().size());
    }
    finally {
      connection.close();
//      expectLastCall().andAnswer(new IAnswer<Object>() {
//        @Override
//        public Object answer() {
//            return null;
//        }
//      });
      statement.close();
      resultSet.close();
    }
    verify(connection, statement, resultSet, metadata);
  }
}
