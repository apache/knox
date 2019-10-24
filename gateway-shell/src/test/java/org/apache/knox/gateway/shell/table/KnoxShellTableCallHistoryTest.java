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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class KnoxShellTableCallHistoryTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private static final List<KnoxShellTableCall> CALL_LIST = new LinkedList<>();

  @BeforeClass
  public static void init() {
    CALL_LIST.add(new KnoxShellTableCall("org.apache.knox.gateway.shell.table.KnoxShellTableBuilder", "csv", false, Collections.emptyMap()));
    CALL_LIST.add(new KnoxShellTableCall("org.apache.knox.gateway.shell.table.CSVKnoxShellTableBuilder", "withHeaders", false, Collections.emptyMap()));
    final String csvPath = KnoxShellTableCallHistoryTest.class.getClassLoader().getResource("knoxShellTableLocationsWithZipLessThan14.csv").getPath();
    CALL_LIST.add(new KnoxShellTableCall("org.apache.knox.gateway.shell.table.CSVKnoxShellTableBuilder", "url", true, Collections.singletonMap("file://" + csvPath, String.class)));
    CALL_LIST.add(new KnoxShellTableCall("org.apache.knox.gateway.shell.table.KnoxShellTable", "filter", false, Collections.emptyMap()));
    CALL_LIST.add(new KnoxShellTableCall("org.apache.knox.gateway.shell.table.KnoxShellTableFilter", "name", false, Collections.singletonMap("ZIP", String.class)));
    CALL_LIST.add(new KnoxShellTableCall("org.apache.knox.gateway.shell.table.KnoxShellTableFilter", "greaterThan", true, Collections.singletonMap("5", String.class)));
  }

  @Test
  public void shouldReturnEmptyListInCaseThereWasNoCall() throws Exception {
    final long id = KnoxShellTable.getUniqueTableId();
    assertTrue(KnoxShellTableCallHistory.getInstance().getCallHistory(id).isEmpty());
  }

  @Test
  public void shouldReturnCallHistoryInProperOrder() throws Exception {
    final long id = KnoxShellTable.getUniqueTableId();
    recordCallHistory(id, 2);
    final List<KnoxShellTableCall> callHistory = KnoxShellTableCallHistory.getInstance().getCallHistory(id);
    assertFalse(callHistory.isEmpty());
    assertEquals(CALL_LIST.get(0), callHistory.get(0));
    assertEquals(CALL_LIST.get(1), callHistory.get(1));
  }

  @Test
  public void shouldThrowIllegalArgumentExceptionIfReplayingToInappropriateStep() {
    final long id = KnoxShellTable.getUniqueTableId();
    recordCallHistory(id, 3);

    // step 2 - second call - does not produce KnoxShellTable (builderMethod=false)
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("It is not allowed to replay up to step 2 as this step does not produce an intance of KnoxShellTable");
    KnoxShellTableCallHistory.getInstance().replay(id, 2);
  }

  @Test
  public void shouldProduceKnoxShellTableIfReplayingToValidStep() {
    final long id = KnoxShellTable.getUniqueTableId();
    recordCallHistory(id, 3);

    final KnoxShellTable table = KnoxShellTableCallHistory.getInstance().replay(id, 3);
    assertNotNull(table);
    assertFalse(table.headers.isEmpty());
    assertEquals(14, table.rows.size());
    assertEquals(table.values(0).get(13), "14"); // selected the first column (ZIP) where the last element - index 13 - is 14
  }

  @Test
  public void shouldNotRollBackIfNoBuilderMethodRecorded() throws Exception {
    final long id = KnoxShellTable.getUniqueTableId();
    recordCallHistory(id, 1);

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("There is no valid step to be rollback to");
    KnoxShellTableCallHistory.getInstance().rollback(id);
  }

  @Test
  public void shouldNotRollBackIfOnlyOneBuilderMethodRecorded() throws Exception {
    final long id = KnoxShellTable.getUniqueTableId();
    recordCallHistory(id, 3);

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("There is no valid step to be rollback to");
    KnoxShellTableCallHistory.getInstance().rollback(id);
  }

  @Test
  public void shouldRollbackToValidPreviousStep() throws Exception {
    final long id = KnoxShellTable.getUniqueTableId();
    recordCallHistory(id, 6);
    // filtered table where ZIPs are greater than "5" (in CSV everything is String)
    final KnoxShellTable table = KnoxShellTableCallHistory.getInstance().replay(id, 6);
    assertNotNull(table);
    assertEquals(4, table.rows.size()); // only 4 rows with ZIP of 6, 7, 8 and 9
    KnoxShellTableCallHistory.getInstance().saveCalls(table.id, KnoxShellTableCallHistory.getInstance().getCallHistory(id)); // in PROD AspectJ does it for us while replaying

    // rolling back to the original table before filtering
    table.rollback();
    assertNotNull(table);
    assertEquals(14, table.rows.size());
    assertEquals(table.values(0).get(13), "14"); // selected the first column (ZIP) where the last element - index 13 - is 14
  }

  private void recordCallHistory(long id, int steps) {
    for (int i = 0; i < steps; i++) {
      KnoxShellTableCallHistory.getInstance().saveCall(id, CALL_LIST.get(i));
    }
  }

}
