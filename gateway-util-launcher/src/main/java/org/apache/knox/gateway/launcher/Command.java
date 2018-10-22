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
import java.io.FilenameFilter;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

class Command {

  private static final String MAIN_CLASS = "main.class";
  private static final String MAIN_METHOD = "main.method";
  private static final String MAIN_ARGS = "main.args";
  private static final String CLASS_PATH = "class.path";
  private static final String CLASS_PATH_DELIM = ",;";
  private static final String FORK = "fork";
  private static final String REDIRECT = "redirect";
  private static final String RESTREAM = "restream";
  private static final String ENV_PREFIX = "env.";
  private static final int ENV_PREFIX_LENGTH = ENV_PREFIX.length();

  File base;
  String mainClass;
  String mainMethod = "main";
  String[] mainArgs;
  List<URL> classPath;
  Properties props;
  Map<String,String> vars;
  Boolean fork = Boolean.FALSE;
  Boolean redirect = Boolean.FALSE; // Controls redirecting stderr to stdout if forking.
  Boolean restream = Boolean.TRUE; // Controls creation of threads to read/write stdin, stdout, stderr of child if forking.

  Command( File base, Properties config, String[] args ) throws MalformedURLException {
    this.base = base;
    this.mainArgs = args ;
    consumeConfig( config );
  }

  void consumeConfig( Properties config ) throws MalformedURLException {
    mainClass = config.getProperty( MAIN_CLASS );
    config.remove( MAIN_CLASS );
    mainMethod = config.getProperty( MAIN_METHOD, mainMethod );
    config.remove( MAIN_METHOD );
    mainArgs = loadMainArgs( mainArgs, config.getProperty( MAIN_ARGS ) );
    config.remove( MAIN_ARGS );
    classPath = loadClassPath( base, config.getProperty( CLASS_PATH ) );
    config.remove( CLASS_PATH );
    fork = Boolean.valueOf( config.getProperty( FORK, fork.toString() ) );
    config.remove( FORK );
    redirect = Boolean.valueOf( config.getProperty( REDIRECT, redirect.toString() ) );
    config.remove( REDIRECT );
    restream = Boolean.valueOf( config.getProperty( RESTREAM, restream.toString() ) );
    config.remove( RESTREAM );
    consumeSysPropsAndEnvVars( config );
  }

  void consumeSysPropsAndEnvVars( Properties config ) {
    props = new Properties();
    vars = new HashMap<>();
    for( String name : config.stringPropertyNames() ) {
      String value = config.getProperty( name );
      if( name.startsWith( ENV_PREFIX ) ) {
        vars.put( name.substring( ENV_PREFIX_LENGTH ), value );
      } else {
        props.setProperty( name, value );
      }
    }
  }

  public void run() throws InvocationTargetException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException {
//TODO    ((URLClassLoader)this.getClass().getClassLoader()).getURLs();
    if( fork ) {
      Forker.fork( this );
    } else {
      Invoker.invoke( this );
    }
  }

  private static String[] loadMainArgs( String[] current, String override ) {
    String[] args = current;
    if( args == null || args.length == 0 ) {
      if( override != null ) {
        args = override.split( " " );
      }
    }
    return args;
  }

  private static List<URL> loadClassPath( File base, String classPath ) throws MalformedURLException {
    List<URL> urls = new ArrayList<>();
    StringTokenizer parser = new StringTokenizer( classPath, CLASS_PATH_DELIM, false );
    while( parser.hasMoreTokens() ) {
      String libPath = parser.nextToken().trim();
      File libFile = new File( base, libPath );
      if( libFile.canRead() && ( libFile.isFile() || libFile.isDirectory() ) ) {
        urls.add( libFile.toURI().toURL() );
      } else if( libPath.endsWith( "*" ) || libPath.endsWith( "*.jar" ) ) {
        File libDir = libFile.getParentFile();
        File[] libFiles = libDir.listFiles( new WildcardFilenameFilter( libFile.getName() ) );
        if( libFiles != null ) {
          for( File file : libFiles ) {
            urls.add( file.toURI().toURL() );
          }
        }
      }
    }
//    for( URL url : urls ) {
//      System.out.println( url );
//    }
    return urls;
  }

  private static class WildcardFilenameFilter implements FilenameFilter {

    private static String SAFE = new String( new char[]{ (char)0 } );

    private Pattern pattern;

    private WildcardFilenameFilter( String filter ) {
      filter = filter.replace( ".", SAFE );
      filter = filter.replace( "*", ".*" );
      filter = filter.replace( SAFE, "\\." );
      pattern = Pattern.compile( filter );
    }

    @Override
    public boolean accept( File dir, String name ) {
      return pattern.matcher( name ).matches();
    }
  }

}
