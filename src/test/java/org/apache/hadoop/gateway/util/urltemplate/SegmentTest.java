/**
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
package org.apache.hadoop.gateway.util.urltemplate;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class SegmentTest {

  @Test
  public void testEquals() throws Exception {
    Object o = new Object();
    TestSegment s1 = new TestSegment( "p", "v" );
    assertThat( s1.equals( o ), equalTo( false ) );
    assertThat( s1.equals( s1 ), equalTo( true ) );

    TestSegment s2 = new TestSegment( "p", "v" );
    assertThat( s1.equals( s2 ), equalTo( true ) );

    TestSegment s3 = new TestSegment( "p2", "v" );
    assertThat( s2.equals( s3 ), equalTo( false ) );

    TestSegment s4 = new TestSegment( "p2", "v2" );
    assertThat( s3.equals( s4 ), equalTo( false ) );

    TestSegment s5 = new TestSegment( "p", "*" );
    assertThat( s1.equals( s5 ), equalTo( false ) );

  }

  @Test
  public void testMatches() throws Exception {
    TestSegment s1 = new TestSegment( "p", "v" );
    TestSegment s2 = new TestSegment( "p", "v" );

    assertThat( s1.matches( s1 ), equalTo( true ) );
    assertThat( s1.matches( s2 ), equalTo( true ) );

    TestSegment s3 = new TestSegment( "p", "*" );
    assertThat( s3.matches( s1 ), equalTo( true ) );
    assertThat( s1.matches( s3 ), equalTo( true ) );

    TestSegment s4 = new TestSegment( "p", "**" );
    assertThat( s4.matches( s1 ), equalTo( true ) );
    assertThat( s1.matches( s4 ), equalTo( true ) );

    TestSegment s5 = new TestSegment( "p", "*.ext" );
    TestSegment s6 = new TestSegment( "p", "file.ext" );
    assertThat( s5.matches( s5 ), equalTo( true ) );
    assertThat( s5.matches( s6 ), equalTo( true ) );
    assertThat( s6.matches( s5 ), equalTo( true ) );

    assertThat( s3.matches( s4 ), equalTo( true ) );
    assertThat( s4.matches( s3 ), equalTo( true ) );
    assertThat( s3.matches( s5 ), equalTo( true ) );
    assertThat( s5.matches( s3 ), equalTo( true ) );
    assertThat( s4.matches( s5 ), equalTo( true ) );
    assertThat( s5.matches( s4 ), equalTo( true ) );

//    InvalidSegment s7 = new InvalidSegment( "p", "v", Integer.MAX_VALUE );
//    InvalidSegment s8 = new InvalidSegment( "p", "v", Integer.MAX_VALUE-1 );
//    assertThat( s7.matches( s8 ), equalTo( false ) );
//    assertThat( s8.matches( s7 ), equalTo( false ) );
//
//    InvalidSegment s9 = new InvalidSegment( "p", "*", Integer.MAX_VALUE-2 );
//    InvalidSegment s10 = new InvalidSegment( "p", "*", Integer.MAX_VALUE-3 );
//    assertThat( s9.matches( s10 ), equalTo( false ) );
//    assertThat( s10.matches( s9 ), equalTo( false ) );
//
//    InvalidSegment s11 = new InvalidSegment( "p", "**", Integer.MAX_VALUE-4 );
//    InvalidSegment s12 = new InvalidSegment( "p", "**", Integer.MAX_VALUE-5 );
//    assertThat( s11.matches( s12 ), equalTo( false ) );
//    assertThat( s12.matches( s11 ), equalTo( false ) );
//
//    assertThat( s7.matches( s9 ), equalTo( false ) );
//    assertThat( s9.matches( s7 ), equalTo( false ) );
//    assertThat( s7.matches( s11 ), equalTo( false ) );
//    assertThat( s11.matches( s7 ), equalTo( false ) );
//    assertThat( s9.matches( s11 ), equalTo( false ) );
//    assertThat( s11.matches( s9 ), equalTo( false ) );
//
//    assertThat( s1.matches( s7 ), equalTo( false ) );
  }

  private class TestSegment extends Segment {

    public TestSegment( String paramName, String valuePattern ) {
      super( paramName, valuePattern );
    }

  }

//  private class InvalidSegment extends Segment {
//
//    public InvalidSegment( String paramName, String valuePattern ) {
//      super( paramName, valuePattern );
//    }
//
//  }

}
