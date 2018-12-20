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
package org.apache.knox.gateway.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

public class Launcher {

  private static final String LAUNCHER_PREFIX = "launcher.";
  private static final String CFG_EXT = "cfg";
  private static final String LAUNCHER_CFG = "META-INF/" + LAUNCHER_PREFIX + CFG_EXT;
  private static final String EXTRACT = "extract";
  private static final String OVERRIDE = "override";

  private URL launcherJarUrl;
  private File launcherJarDir;
  private String launcherName;
  private URL embeddedConfigUrl;
  private URL externalConfigUrl;
  private Command command;

  private Boolean extract = Boolean.TRUE;
  private Boolean override = Boolean.TRUE;

  public static void main( String[] args ) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException {
    Launcher launcher = new Launcher( args );
    launcher.run();
  }

  Launcher( String[] args ) throws IOException {
    launcherJarUrl = findLauncherJar();
    launcherJarDir = calcLauncherDir( launcherJarUrl );
    launcherName = calcLauncherName( launcherJarUrl );
    embeddedConfigUrl = findEmbeddedConfig();
    Properties properties = new Properties();
    loadProperties( properties, embeddedConfigUrl );
    consumeLauncherConfig( properties );
    if( override ) {
      externalConfigUrl = findOrCreateExternalConfig( launcherJarDir, launcherName, properties, embeddedConfigUrl, extract );
      if( externalConfigUrl != null ) {
        loadProperties( properties, externalConfigUrl );
      }
    }
    properties.setProperty( LAUNCHER_PREFIX + "dir", launcherJarDir.getAbsolutePath() );
    properties.setProperty( LAUNCHER_PREFIX + "name", launcherName );
    command = new Command( launcherJarDir, properties, args );
  }

  void run() throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    command.run();
  }

  private static URL findLauncherJar() {
    return Launcher.class.getProtectionDomain().getCodeSource().getLocation();
  }

  static String calcLauncherName( URL launcherJarUrl ) {
    String name = launcherJarUrl.getPath();
    name = fileName( name );
    name = baseName( name );
    return name;
  }

  void consumeLauncherConfig( Properties properties ) {
    extract = Boolean.valueOf( properties.getProperty( EXTRACT, extract.toString() ) );
    properties.remove( EXTRACT );
    override = Boolean.valueOf( properties.getProperty( OVERRIDE, override.toString() ) );
    properties.remove( OVERRIDE );
  }

  private static File calcLauncherDir( URL libUrl ) throws UnsupportedEncodingException {
    String libPath = URLDecoder.decode(libUrl.getFile(), StandardCharsets.UTF_8.name());
    File libFile = new File( libPath );
    File dir;
    if( libFile.isDirectory() ) {
      dir = libFile;
    } else {
      dir = libFile.getParentFile();
    }
    return dir;
  }

  private static URL findEmbeddedConfig() {
    return ClassLoader.getSystemResource( LAUNCHER_CFG );
  }

  private static void createExternalConfig( File file, Properties config, URL src ) throws IOException {
    try {
      if( file.createNewFile() ){
        try (OutputStream output = Files.newOutputStream(file.toPath())) {
          config.store(output, "Created from " + src);
        }
      }
    } catch ( IOException e ) {
      // Ignore it and use the embedded config.
    }
  }

  private static URL findOrCreateExternalConfig( File launcherJarDir, String launcherName, Properties embeddedConfig, URL embeddedConfigUrl, boolean extract ) throws IOException {
    File externalConfigFile = new File( launcherJarDir, launcherName + "." + CFG_EXT );
    if( !externalConfigFile.exists() && extract ) {
      createExternalConfig( externalConfigFile, embeddedConfig, embeddedConfigUrl );
    }
    URL externalConfigUrl = null;
    if( externalConfigFile.isFile() && externalConfigFile.canRead() ) {
      externalConfigUrl = externalConfigFile.toURI().toURL();
    }
    return externalConfigUrl;
  }

  private static Properties loadProperties( Properties properties, URL url ) throws IOException {
    if( url != null ) {
      try (InputStream stream = url.openStream()) {
        properties.load(stream);
      }
    }
    return properties;
  }

  static String fileName( String fullName ) {
    int i = fullName.lastIndexOf( '/' );
    if( i < 0 ) {
      i = fullName.lastIndexOf( '\\' );
    }
    return i < 0 ? fullName : fullName.substring( i + 1 );
  }

  static String baseName( String fullName ) {
    int i = fullName.lastIndexOf( '.' );
    return i < 0 ? fullName : fullName.substring( 0, i );
  }
}
