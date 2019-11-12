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
package org.apache.knox.gateway.i18n.messages;

import org.apache.knox.gateway.i18n.resources.ResourcesInvoker;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Locale;

public class MessagesInvoker extends ResourcesInvoker implements InvocationHandler {

  private String codes;
  private MessageLogger logger;
  private String bundle;

  public MessagesInvoker( Class<?> clazz, MessageLoggerFactory loggers ) {
    super( clazz );
    Messages anno = clazz.getAnnotation( Messages.class );
    codes = calcCodePattern( clazz, anno );
    bundle = calcBundleName( clazz, anno );
    logger = getLogger( clazz, anno, loggers );
  }

  @Override
  public Object invoke( final Object proxy, final Method method, final Object[] args ) throws Throwable {
    String message = null;
    MessageLevel level = getLevel( method );
    if( logger.isLoggable( level ) ) {
      message = getText( method, args );
      String code = getCode( method );
      Throwable throwable = findLoggableThrowable( logger, method, args );
      // Supposedly this Throwable way is faster than the Thread way.
      // From looking at the JRE code it looks this is probably the case.
      // The second version ends up calling the first version after getting the current thread
      // and then checking that it is being called from the current thread.
      //TODO: Should probably only do this if the logger is at debug or finer to make sure there isn't a performance impact during production usage.
      StackTraceElement caller = new Throwable().getStackTrace()[2];
      // StackTraceElement caller = Thread.currentThread().getStackTrace()[3];
      logger.log( caller, level, code, message, throwable );
    }
    return message;
  }

  private String getCode( final Method method ) {
    String code = null;
    Message anno = method.getAnnotation( Message.class );
    if( anno != null ) {
      int num = anno.code();
      if( Message.DEFAULT_CODE != num ) {
        MessageFormat messageFormat = new MessageFormat(codes, Locale.ROOT );
        code = messageFormat.format(new Object[]{num});
      }
    }
    return code;
  }

  private static StackTrace getStackTraceAnno( final Method method, final int param ) {
    final Annotation[] annos = method.getParameterAnnotations()[ param ];
    for( Annotation anno: annos ) {
      if( anno instanceof StackTrace ) {
        return (StackTrace)anno;
      }
    }
    return null;
  }

  private static Throwable findLoggableThrowable( final MessageLogger logger, final Method method, final Object[] args ) {
    Throwable throwable = null;
    if( args != null ) {
      for( int i=0; i<args.length; i++ ) {
        Object arg = args[i];
        if( arg instanceof Throwable ) {
          StackTrace anno = getStackTraceAnno( method, i );
          if( anno != null && logger.isLoggable( anno.level() ) ) {
            throwable = (Throwable)arg;
            break;
          }
        }
      }
    }
    return throwable;
  }

  @Override
  protected String getAnnotationPattern(final Method method ) {
    String pattern = null;
    Message anno = method.getAnnotation( Message.class );
    if( anno != null ) {
      pattern = anno.text();
    }
    return pattern;
  }

  private static MessageLevel getLevel( final Method method ) {
    MessageLevel level;
    Message anno = method.getAnnotation( Message.class );
    if( anno == null ) {
      level = MessageLevel.INFO;
    } else {
      level = anno.level();
    }
    return level;
  }

  private static String calcCodePattern( final Class<?> clazz, final Messages anno ) {
    String pattern = anno.codes();
    if( Messages.DEFAULT_CODES.equals( pattern ) ) {
      pattern = clazz.getCanonicalName().replace( '.', '/' );
    }
    return pattern;
  }

  private static String calcBundleName( Class<?> clazz, Messages anno ) {
    String bundle = null;
    if( anno != null ) {
      bundle = anno.bundle();
      if( Messages.DEFAULT_BUNDLE.equals( bundle ) ) {
        bundle = null;
      }
    }
    if( bundle == null ) {
      bundle = clazz.getCanonicalName().replace( '.', '/' );
    }
    return bundle;
  }

  private static String calcLoggerName( final Class<?> clazz, final Messages anno ) {
    String logger = null;
    if( anno != null ) {
      logger = anno.logger();
      if( Messages.DEFAULT_LOGGER.equals( logger ) ) {
        logger = null;
      }
    }
    if( logger == null ) {
      logger = clazz.getCanonicalName();
    }
    return logger;
  }

  @Override
  protected String getBundleName() {
    return bundle;
  }

  private static MessageLogger getLogger( final Class<?> clazz, final Messages anno, final MessageLoggerFactory loggers ) {
    return loggers.getLogger( calcLoggerName( clazz, anno ) );
  }

  @Override
  public String toString() {
    return "MessageInvoker["+bundle+"]";
  }

}
