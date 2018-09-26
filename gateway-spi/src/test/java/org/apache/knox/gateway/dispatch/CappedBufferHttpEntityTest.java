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
package org.apache.knox.gateway.dispatch;

import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;

public class CappedBufferHttpEntityTest {

  private static Charset UTF8 = Charset.forName( "UTF-8" );

  // Variables
  // Consumers: C1, C2
  // Reads: FC - Full Content, PC - Partial Content, AC - Any Content
  // Reads: IB - In Buffer, OB - Overflow Buffer
  // Close: XC
  // Expect: EE

  // Test Cases
  // C1 FC
  //   C1 FC/IB.
  //   C1 FC/OB.
  //   C1 FC/IB; C2 FC.
  //   C1 FC/OB; C2 AC; EE
  //   C1 FC/IB; C1 XC; C2 FC.
  //   C1 FC/OB; C1 XC; C2 AC; EE
  // C1 PC
  //   C1 PC/IB.
  //   C1 PC/OB.
  //   C1 PC/IB; C2 FC.
  //   C1 PC/OB; C2 AC; EE
  //   C1 PC/IB; C1 XC; C2 FC.
  //   C1 PC/OB; C1 XC; C2 AC; EE
  // C1 C2 C1
  //   C1 PC/IB; C2 PC/IB; C1 PC/IB; C2 PC/IB - Back and forth before buffer overflow is OK.
  //   C1 PC/IB; C2 PC/OB; C1 AC; EE

  @Test
  public void testS__C1_FC_IB() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( "UTF-8" ) ) );
    replay = new CappedBufferHttpEntity( basic, 20 );

    String output;

    output = byteRead( replay.getContent(), -1 );
    assertThat( output, is( data ) );
  }

  @Test
  public void testB__C1_FC_IB() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( "UTF-8" ) ) );
    replay = new CappedBufferHttpEntity( basic, 20 );

    String output;

    output = blockRead( replay.getContent(), UTF8, -1, 3 );
    assertThat( output, is( data ) );
  }

  @Test
  public void testS__C1_FC_OB() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( "UTF-8" ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );

    String output;

    try {
      output = byteRead( replay.getContent(), -1 );
      fail("expected IOException");
    } catch (IOException e) {
      // expected
    }
  }

  @Test
  public void testB__C1_FC_OB() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( "UTF-8" ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );

    String output;

    try {
      output = blockRead( replay.getContent(), UTF8, -1, 3 );
      fail("expected IOException");
    } catch (IOException e) {
      // expected
    }
  }

  @Test
  public void testS_C1_FC_IB__C2_FC_IB() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( "UTF-8" ) ) );
    replay = new CappedBufferHttpEntity( basic, 20 );

    String output;

    output = byteRead( replay.getContent(), -1 );
    assertThat( output, is( data ) );

    output = byteRead( replay.getContent(), -1 );
    assertThat( output, is( data ) );
  }

  @Test
  public void testB_C1_FC_IB__C2_FC_IB() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( "UTF-8" ) ) );
    replay = new CappedBufferHttpEntity( basic, 20 );

    String output;

    output = blockRead( replay.getContent(), UTF8, -1, 3 );
    assertThat( output, is( data ) );

    output = blockRead( replay.getContent(), UTF8, -1, 3 );
    assertThat( output, is( data ) );
  }

  @Test
  public void testS_C1_FC_OB__C2_AC__EE() throws Exception {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );

    String output;

    try {
      output = byteRead( replay.getContent(), -1 );
    fail( "Expected IOException" );
    } catch( IOException e ) {
     // Expected.
   }
 
  }

  @Test
  public void testB_C1_FC_OB__C2_AC__EE() throws Exception {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );

    String output;
    try {
      output = blockRead( replay.getContent(), UTF8, -1, 3 );
      fail( "Expected IOException" );
    } catch( IOException e ) {
      // Expected.
    }
  }

  //   C1 FC/IB; C1 XC; C2 FC.
  @Test
  public void testS_C1_FC_IB__C1_XC__C2_FC() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 20 );
    stream = replay.getContent();
    text = byteRead( stream, -1 );
    assertThat( text, is( "0123456789" ) );
    stream.close();

    stream = replay.getContent();
    text = byteRead( stream, -1 );
    assertThat( text, is( "0123456789" ) );
  }

  //   C1 FC/IB; C1 XC; C2 FC.
  @Test
  public void testB_C1_FC_IB__C1_XC__C2_FC() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 20 );

    stream = replay.getContent();
    text = blockRead( stream, UTF8, -1, 3 );
    assertThat( text, is( "0123456789" ) );
    stream.close();

    stream = replay.getContent();
    text = blockRead( stream, UTF8, -1, 3 );
    assertThat( text, is( "0123456789" ) );
  }

  //   C1 FC/OB; C1 XC; C2 AC; EE
  @Test
  public void testS_C1_FC_OB__C1_XC__C2_AC__EE() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );

    stream = replay.getContent();
    try {
      text = byteRead( stream, -1 );
      fail( "Expected IOException" );
    } catch( IOException e ) {
      // Expected.
    }
  }

  //   C1 FC/OB; C1 XC; C2 AC; EE
  @Test
  public void testB_C1_FC_OB__C1_XC__C2_AC_EE() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );

    stream = replay.getContent();
    try {
      text = blockRead( stream, UTF8, -1, 3 );
      fail( "Expected IOException" );
    } catch( IOException e ) {
      // Expected.
    }
  }

  //   C1 PC/IB.
  @Test
  public void testS_C1_PC_IB() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 20 );

    stream = replay.getContent();
    text = byteRead( stream, 3 );
    assertThat( text, is( "012" ) );
  }

  //   C1 PC/IB.
  @Test
  public void testB_C1_PC_IB() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 20 );

    stream = replay.getContent();
    text = blockRead( stream, UTF8, 3, 3 );
    assertThat( text, is( "012" ) );
  }

  //   C1 PC/OB.
  @Test
  public void testS_C1_PC_OB() throws IOException {

    try {
      String data = "0123456789";
      BasicHttpEntity basic;
      CappedBufferHttpEntity replay;
      InputStream stream;
      String text;

      basic = new BasicHttpEntity();
      basic.setContent(new ByteArrayInputStream(data.getBytes(UTF8)));
      replay = new CappedBufferHttpEntity(basic, 5);
      stream = replay.getContent();
      text = byteRead(stream, -1);
      fail("Expected IOException");
      assertThat(text, is("0123456789"));
      stream.close();
    } catch (IOException e) {
      // expected
    }
  }

  //   C1 PC/OB.
  @Test
  public void testB_C1_PC_OB() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );

    stream = replay.getContent();
    try {
      text = blockRead( stream, UTF8, -1, 4 );
      fail( "Expected IOException" );
    } catch (IOException e) {
      // expected
    }
  }

  //   C1 PC/IB; C2 FC.
  @Test
  public void testS_C1_PC_IB__C2_FC() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 20 );

    stream = replay.getContent();
    text = byteRead( stream, 4 );
    assertThat( text, is( "0123" ) );
    stream.close();

    stream = replay.getContent();
    text = byteRead( stream, -1 );
    assertThat( text, is( "0123456789" ) );
  }

  //   C1 PC/IB; C2 FC.
  @Test
  public void testB_C1_PC_IB__C2_FC() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 20 );

    stream = replay.getContent();
    text = blockRead( stream, UTF8, 4, 1 );
    assertThat( text, is( "0123" ) );
    stream.close();

    stream = replay.getContent();
    text = blockRead( stream, UTF8, -1, 7 );
    assertThat( text, is( "0123456789" ) );
  }

  //   C1 PC/OB; C2 AC; EE
  @Test
  public void testS_C1_PC_OB__C2_AC__EE() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream;
    String text;

    try {
      basic = new BasicHttpEntity();
      basic.setContent(new ByteArrayInputStream(data.getBytes(UTF8)));
      replay = new CappedBufferHttpEntity(basic, 5);

      stream = replay.getContent();
      text = byteRead(stream, 7);
      assertThat(text, is("0123456"));
      stream.close();
      fail("Expected IOException");
    } catch (IOException e) {
      // Expected.
    }
  }

  //   C1 PC/OB; C2 AC; EE
  @Test
  public void testB_C1_PC_OB__C2_AC__EE() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );

    stream = replay.getContent();
    try {
      text = blockRead( stream, UTF8, 7, 2 );
      fail("Expected IOExceptin");
    } catch (IOException e) {
      // expected
    }
  }

  //   C1 PC/IB; C1 XC; C2 FC.
  @Test
  public void testS_C1_PC_IB__C1_XC__C2_FC() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 20 );

    stream = replay.getContent();
    text = byteRead( stream, 7 );
    assertThat( text, is( "0123456" ) );
    stream.close();

    stream = replay.getContent();
    text = byteRead( stream, -1 );
    assertThat( text, is( "0123456789" ) );
  }

  //   C1 PC/IB; C1 XC; C2 FC.
  @Test
  public void testB_C1_PC_IB__C1_XC__C2_FC() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 20 );

    stream = replay.getContent();
    text = blockRead( stream, UTF8, 7, 2 );
    assertThat( text, is( "0123456" ) );
    stream.close();

    stream = replay.getContent();
    text = blockRead( stream, UTF8, -1, 7 );
    assertThat( text, is( "0123456789" ) );
  }

  //   C1 PC/OB; C1 XC; C2 AC; EE
  @Test
  public void testS_C1_PC_OB__C1_XC__C2_AC__EE() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );

    try {
      stream = replay.getContent();
    } catch ( IOException e ) {
      // Expected.
    }
  }

  //   C1 PC/OB; C1 XC; C2 AC; EE
  @Test
  public void testB_C1_PC_OB__C1_XC__C2_AC__EE() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );

    stream = replay.getContent();
    try {    
      text = blockRead( stream, UTF8, 7, 2 );
      fail( "Expected IOException" );
    } catch ( IOException e ) {
      // Expected.
    }
  }

  //   C1 PC/IB; C2 PC/IB; C1 PC/IB; C2 PC/IB - Back and forth before buffer overflow is OK.
  @Test
  public void testS_C1_PC_IB__C2_PC_IB__C2_PC_IB() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream1, stream2;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 20 );

    stream1 = replay.getContent();
    text = byteRead( stream1, 3 );
    assertThat( text, is( "012" ) );

    stream2 = replay.getContent();
    text = byteRead( stream2, 4 );
    assertThat( text, is( "0123" ) );

    text = byteRead( stream1, 3 );
    assertThat( text, is( "345" ) );
  }

  //   C1 PC/IB; C2 PC/IB; C1 PC/IB; C2 PC/IB - Back and forth before buffer overflow is OK.
  @Test
  public void testB_C1_PC_IB__C2_PC_IB__C2_PC_IB() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream1, stream2;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 20 );
    stream1 = replay.getContent();
    text = blockRead( stream1, UTF8, 3, 2 );
    assertThat( text, is( "012" ) );

    stream2 = replay.getContent();
    text = blockRead( stream2, UTF8, 4, 3 );
    assertThat( text, is( "0123" ) );

    text = blockRead( stream1, UTF8, 3, 2 );
    assertThat( text, is( "345" ) );
  }

  //   C1 PC/IB; C2 PC/OB; C1 AC; EE
  @Test
  public void testS_C1_PC_IB__C2_PC_OB__C1_AC__EE() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream1, stream2;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );

    stream1 = replay.getContent();
    text = byteRead( stream1, 3 );
    assertThat( text, is( "012" ) );

    stream2 = replay.getContent();
    text = byteRead( stream2, 5 );
    assertThat( text, is( "01234" ) );
  }

  //   C1 PC/IB; C2 PC/OB; C1 AC; EE
  @Test
  public void testB_C1_PC_IB__C2_PC_OB__C1_AC__EE() throws IOException {
    String data = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;
    InputStream stream1, stream2;
    String text;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( data.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );

    stream1 = replay.getContent();
    text = blockRead( stream1, UTF8, 3, 2 );
    assertThat( text, is( "012" ) );

    stream2 = replay.getContent();
    try {
      text = blockRead( stream2, UTF8, 6, 4 );
      fail("expected IOException");
    } catch (IOException e) {
      // expected
    }
 
  }

  @Test
  public void testWriteTo() throws Exception {
    String input = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( input.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try {
      replay.writeTo( buffer );
      fail("expected IOException");
    } catch (IOException e) {
      // expected
    }
  }

  @Test
  public void testIsRepeatable() throws Exception {
    String text = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( text.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic );
    assertThat( replay.isRepeatable(), is( true ) );

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( text.getBytes( UTF8 ) ) );
    BufferedHttpEntity buffered = new BufferedHttpEntity( basic );
    replay = new CappedBufferHttpEntity( buffered );
    assertThat( replay.isRepeatable(), is( true ) );
  }

  @Test
  public void testIsChunked() throws Exception {
    String input = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( input.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );
    assertThat( replay.isChunked(), is( false ) );

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( input.getBytes( UTF8 ) ) );
    basic.setChunked( true );
    replay = new CappedBufferHttpEntity( basic, 5 );
    assertThat( replay.isChunked(), is( true ) );
  }

  @Test
  public void testGetContentLength() throws Exception {
    String input = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( input.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );
    assertThat( replay.getContentLength(), is( -1L ) );

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( input.getBytes( UTF8 ) ) );
    basic.setContentLength( input.length() );
    replay = new CappedBufferHttpEntity( basic, 5 );
    assertThat( replay.getContentLength(), is( 10L ) );
  }

  @Test
  public void testGetContentType() throws Exception {
    String input = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( input.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );
    assertThat( replay.getContentType(), nullValue() );

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( input.getBytes( UTF8 ) ) );
    basic.setContentType( ContentType.APPLICATION_JSON.getMimeType() );
    replay = new CappedBufferHttpEntity( basic, 5 );
    assertThat( replay.getContentType().getValue(), is( "application/json" ) );
  }

  @Test
  public void testGetContentEncoding() throws Exception {
    String input = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( input.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );
    assertThat( replay.getContentEncoding(), nullValue() );

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( input.getBytes( UTF8 ) ) );
    basic.setContentEncoding( "UTF-8" );
    replay = new CappedBufferHttpEntity( basic, 5 );
    assertThat( replay.getContentEncoding().getValue(), is( "UTF-8" ) );
  }

  @Test
  public void testIsStreaming() throws Exception {
    String input = "0123456789";
    BasicHttpEntity basic;
    InputStreamEntity streaming;
    CappedBufferHttpEntity replay;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( input.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );
    assertThat( replay.isStreaming(), is( true ) );

    basic = new BasicHttpEntity();
    basic.setContent( null );
    replay = new CappedBufferHttpEntity( basic, 5 );
    assertThat( replay.isStreaming(), is( false ) );

    streaming = new InputStreamEntity( new ByteArrayInputStream( input.getBytes( UTF8 ) ), 10, ContentType.TEXT_PLAIN );
    replay = new CappedBufferHttpEntity( streaming, 5 );
    assertThat( replay.isStreaming(), is( true ) );
  }

  @Test
  public void testConsumeContent() throws Exception {
    String input = "0123456789";
    BasicHttpEntity basic;
    CappedBufferHttpEntity replay;

    basic = new BasicHttpEntity();
    basic.setContent( new ByteArrayInputStream( input.getBytes( UTF8 ) ) );
    replay = new CappedBufferHttpEntity( basic, 5 );

    try {
      replay.consumeContent();
      fail( "Expected UnsupportedOperationException" );
    } catch ( UnsupportedOperationException e ) {
      // Expected.
    }
  }

  private static String byteRead( InputStream stream, int total ) throws IOException {
    StringBuilder string = null;
    int c = 0;
    if( total < 0 ) {
      total = Integer.MAX_VALUE;
    }
    while( total > 0 && c >= 0 ) {
      c = stream.read();
      if( c >= 0 ) {
        total--;
        if( string == null ) {
          string = new StringBuilder();
        }
        string.append( (char)c );
      }
    }
    return string == null ? null : string.toString();
  }

  private static String blockRead( InputStream stream, Charset charset, int total, int chunk ) throws IOException {
    StringBuilder string = null;
    byte buffer[] = new byte[ chunk ];
    int count = 0;
    if( total < 0 ) {
      total = Integer.MAX_VALUE;
    }
    while( total > 0 && count >= 0 ) {
      count = stream.read( buffer, 0, Math.min( buffer.length, total ) );
      if( count >= 0 ) {
        total -= count;
        if( string == null ) {
          string = new StringBuilder();
        }
        string.append( new String( buffer, 0, count, charset ) );
      }
    }
    return string == null ? null : string.toString();
  }

}
