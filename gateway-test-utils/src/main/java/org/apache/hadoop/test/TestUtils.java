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
package org.apache.hadoop.test;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.UUID;

public class TestUtils {

  public static String getResourceName( Class clazz, String name ) {
    name = clazz.getName().replaceAll( "\\.", "/" ) + "/" + name;
    return name;
  }

  public static URL getResourceUrl( Class clazz, String name ) throws FileNotFoundException {
    name = getResourceName( clazz, name );
    URL url = ClassLoader.getSystemResource( name );
    if( url == null ) {
      throw new FileNotFoundException( name );
    }
    return url;
  }

  public static InputStream getResourceStream( Class clazz, String name ) throws IOException {
    URL url = getResourceUrl( clazz, name );
    InputStream stream = url.openStream();
    return stream;
  }

  public static Reader getResourceReader( Class clazz, String name, String charset ) throws IOException {
    return new InputStreamReader( getResourceStream( clazz, name ), charset );
  }

  public static String getResourceString( Class clazz, String name, String charset ) throws IOException {
    return IOUtils.toString( getResourceReader( clazz, name, charset ) );
  }

  public static File createTempDir( String prefix ) throws IOException {
    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File tempDir = new File( targetDir, prefix + UUID.randomUUID() );
    FileUtils.forceMkdir( tempDir );
    return tempDir;
  }

}
