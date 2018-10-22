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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class JsonPath {

  public static class Match {

    private Match parent;
    private Segment segment;
    private JsonNode node;
    private String field;
    private int index;

    private Match( Match parent, Segment segment, JsonNode node, String field, int index ) {
      this.parent = parent;
      this.segment = segment;
      this.node = node;
      this.field = field;
      this.index = index;
    }

    private Match( Match parent, Segment segment, JsonNode node, String field ) {
      this( parent, segment, node, field, -1 );
    }

    private Match( Match parent, Segment segment, JsonNode node, int index ) {
      this( parent, segment, node, null, index );
    }

    public Match getParent() {
      return parent;
    }

    public Segment getSegment() {
      return segment;
    }

    public JsonNode getNode() {
      return node;
    }

    public String getField() {
      return field;
    }

    public int getIndex() {
      return index;
    }

    public String toString() {
      StringBuilder s = new StringBuilder();
      s.append( "JsonPath{" );
      s.append( "parent=" ); s.append( getParent() );
      s.append( ",segment=" ); s.append( getSegment() );
      s.append( ",node=" ); s.append( getNode() );
      s.append( ",field=" ); s.append( getField() );
      s.append( ",index=" ); s.append( getIndex() );
      s.append( "}" );
      return s.toString();
    }

  }

  public static class Expression {

    private List<Segment> segments;

    private Expression( String expression ) {
      if( expression == null || !expression.startsWith( "$" ) ) {
        throw new IllegalArgumentException( expression );
      }
      segments = parse( expression );
    }

    public Segment[] getSegments() {
      Segment[] array = null;
      if( segments != null ) {
        array = new Segment[ segments.size() ];
        segments.toArray( array );
      }
      return array;
    }

    private List<Segment> parse( String expression ) {
      boolean insideBrackets = false;
      boolean expectChild = false;
      boolean foundChild = false;
      List<Segment> list = null;
      Segment segment;
      StringTokenizer parser = new StringTokenizer( expression, "$.[]()@?:,", true );
      String currToken = null;
      String prevToken = null;
      while( parser.hasMoreTokens() ) {
        prevToken = currToken;
        if( insideBrackets ) {
          currToken = parser.nextToken( "$[]()@?:," ).trim();
        } else {
          currToken = parser.nextToken( "$.[]()@?:," ).trim();
        }
        char c = currToken.charAt( 0 );
        switch( c ) {
          case '$' :
            if( list != null ) {
              throw new IllegalArgumentException( expression );
            }
            list = new ArrayList<>();
            segment = new Segment( Segment.Type.ROOT );
            list.add( segment );
            break;
          case '.' :
            if( expectChild ) {
              if( ".".equals( prevToken ) ) {
                segment = new Segment( Segment.Type.GLOB );
                list.add( segment );
                expectChild = true;
                foundChild = false;
              } else {
                throw new IllegalArgumentException( expression );
              }
            } else {
              expectChild = true;
              foundChild = false;
            }
            break;
          case '[' :
            if( expectChild ) {
              throw new IllegalArgumentException( expression );
            }
            insideBrackets = true;
            expectChild = true;
            foundChild = false;
            break;
          case ']' :
            if( !foundChild ) {
              throw new IllegalArgumentException( expression );
            }
            insideBrackets = false;
            expectChild = false;
            foundChild = false;
            break;
          case '(':
          case ')':
          case '@':
          case '?':
          case ':':
          case ',':
            throw new IllegalArgumentException( expression );
          default:
            if( !expectChild ) {
              throw new IllegalArgumentException( expression );
            } else if( Character.isDigit( c ) ) {
              try {
                segment = new Segment( Integer.parseInt( currToken ) );
              } catch( NumberFormatException e ) {
                throw new IllegalArgumentException( expression, e );
              }
            } else {
              if( "*".equals( currToken ) ) {
                segment = new Segment( Segment.Type.WILD );
              } else if ( "**".equals( currToken ) ) {
                segment = new Segment( Segment.Type.GLOB );
              } else {
                if( currToken.startsWith( "'" ) ) {
                  currToken = currToken.substring( 1 );
                }
                if( currToken.endsWith( "'" ) ) {
                  currToken = currToken.substring( 0, currToken.length()-1 );
                }
                segment = new Segment( currToken );
              }
            }
            list.add( segment );
            expectChild = false;
            foundChild = true;
            break;
        }
      }
      if( expectChild && !foundChild ) {
        throw new IllegalArgumentException( expression );
      }
      return list;
    }

    public List<Match> evaluate( JsonNode root ) {
      JsonNode parent;
      JsonNode child;
      Match newMatch;
      List<Match> tempMatches;
      List<Match> oldMatches = new ArrayList<>();
      List<Match> newMatches = new ArrayList<>();
      if( root != null ) {
        for( Segment seg : segments ) {
          if( Segment.Type.ROOT == seg.getType() ) {
            oldMatches.add( new Match( null, segments.get( 0 ), root, null, -1 ) );
            continue;
          } else {
            for( Match oldMatch : oldMatches ) {
              parent = oldMatch.getNode();
              switch( seg.getType() ) {
                case FIELD:
                  if( JsonNodeType.OBJECT == oldMatch.getNode().getNodeType() ) {
                    child = oldMatch.getNode().get( seg.getField() );
                    if( child == null ) {
                      continue;
                    } else {
                      newMatches.add( new Match( oldMatch, seg, child, seg.getField() ) );
                    }
                  } else {
                    continue;
                  }
                  break;
                case INDEX:
                  if( JsonNodeType.ARRAY == oldMatch.getNode().getNodeType() ) {
                    child = oldMatch.getNode().get( seg.getIndex() );
                    if( child == null ) {
                      continue;
                    } else {
                      newMatches.add( new Match( oldMatch, seg, child, seg.getIndex() ) );
                    }
                  } else {
                    continue;
                  }
                  break;
                case GLOB:
                  newMatches.add( oldMatch );
                case WILD:
                  switch( parent.getNodeType() ) {
                    case OBJECT:
                      Iterator<Map.Entry<String,JsonNode>> fields = parent.fields();
                      while( fields.hasNext() ) {
                        Map.Entry<String,JsonNode> field = fields.next();
                        newMatch = new Match( oldMatch, seg, field.getValue(), field.getKey() );
                        newMatches.add( newMatch );
                        if( seg.getType() == Segment.Type.GLOB ) {
                          addAllChildren( oldMatch, newMatches, field.getValue() );
                        }
                      }
                      break;
                    case ARRAY:
                      for( int i=0, n=parent.size(); i<n; i++ ) {
                        newMatch = new Match( oldMatch, seg, parent.get( i ), i );
                        newMatches.add( newMatch );
                        if( seg.getType() == Segment.Type.GLOB ) {
                          addAllChildren( oldMatch, newMatches, newMatch.getNode() );
                        }
                      }
                      break;
                  }
                  break;
                default:
                  throw new IllegalStateException();
              }
            }
            if( newMatches.isEmpty() ) {
              return newMatches;
            } else {
              tempMatches = oldMatches;
              oldMatches = newMatches;
              newMatches = tempMatches;
              newMatches.clear();
            }
          }
        }
      }
      return oldMatches;
    }

    private static final void addAllChildren( Match parent, List<Match> matches, JsonNode node ) {
      Match match;
      switch( node.getNodeType() ) {
        case OBJECT:
          Iterator<Map.Entry<String,JsonNode>> fields = node.fields();
          while( fields.hasNext() ) {
            Map.Entry<String,JsonNode> field = fields.next();
            match = new Match( parent, parent.getSegment(), field.getValue(), field.getKey() );
            matches.add( match );
            addAllChildren( match, matches, match.getNode() );
          }
          break;
        case ARRAY:
          for( int i=0, n=node.size(); i<n; i++ ) {
            match = new Match( parent, parent.getSegment(), node.get( i ), i );
            matches.add( match );
            addAllChildren( match, matches, match.getNode() );
          }
          break;
      }
    }

    public String toString() {
      StringBuilder s = new StringBuilder();
      s.append( "JsonPath.Expression{" );
      for( int i=0, n=segments.size(); i<n; i++ ) {
        if( i > 0 ) {
          s.append( "," );
        }
        s.append( "segment[" );
        s.append( i );
        s.append( "]=" );
        s.append( segments.get( i ) );
      }
      s.append( "]" );
      return s.toString();
    }

  }

  public static class Segment {

    public enum Type { ROOT, FIELD, INDEX, WILD, GLOB }

    private Type type;
    private String field;
    private int index;

    private Segment( Type type ) {
      this.type = type;
      this.field = null;
      this.index = -1;
    }

    private Segment( String field ) {
      this.type = Type.FIELD;
      this.field = field;
      this.index = -1;
    }

    private Segment( int index ) {
      this.type = Type.INDEX;
      this.field = null;
      this.index = index;
    }

    public Type getType() {
      return type;
    }

    public String getField() {
      return field;
    }

    public int getIndex() {
      return index;
    }

    public String toString() {
      StringBuilder s = new StringBuilder();
      s.append( "JsonPath.Segment{" );
      s.append( "type=" ); s.append( getType() );
      s.append( ",field=" ); s.append( getField() );
      s.append( ",index=" ); s.append( getIndex() );
      s.append( "}" );
      return s.toString();
    }
  }

  public static Expression compile( String expression ) {
    return new Expression( expression );
  }

}
