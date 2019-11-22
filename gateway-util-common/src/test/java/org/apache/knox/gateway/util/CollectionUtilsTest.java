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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.Test;

public class CollectionUtilsTest {

  private final Comparator<Integer> integerComparator = Comparator.naturalOrder();

  @Test
  public void shouldReturnFalseWhenCheckingIfCollectionIsSortedAndGivenCollectionIsNull() throws Exception {
    assertFalse(CollectionUtils.isSorted(null, integerComparator));
  }

  @Test
  public void shouldReturnTrueWhenCheckingIfCollectionIsSortedAndGivenCollectionIsEmpty() throws Exception {
    assertTrue(CollectionUtils.isSorted(Collections.emptyList(), integerComparator));
  }

  @Test
  public void shouldReturnTrueWhenCheckingIfCollectionIsSortedAndGivenCollectionHasOneElement() throws Exception {
    final List<Integer> list = Arrays.asList(1);
    assertTrue(CollectionUtils.isSorted(list, integerComparator));
  }

  @Test
  public void shouldReturnTrueWhenCheckingIfCollectionIsSortedAndGivenCollectionIsSorted() throws Exception {
    final List<Integer> list = Arrays.asList(1, 2, 5, 8, 9);
    assertTrue(CollectionUtils.isSorted(list, integerComparator));
  }

  @Test
  public void shouldReturnFalseWhenCheckingIfCollectionIsSortedAndGivenCollectionIsNotSorted() throws Exception {
    final List<Integer> list = Arrays.asList(1, 5, 2, 8, 9);
    assertFalse(CollectionUtils.isSorted(list, integerComparator));
  }
}
