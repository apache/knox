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
package org.apache.hadoop.gateway.util.uritemplate;

import java.util.*;

public class Matcher<V> {

  private Map<Template,V> map;
  private PathNode root;

  public Matcher() {
    map = new HashMap<Template,V>();
    root = new PathNode( null, null );
  }

  public void add( Template template, V value ) {
    map.put( template, value );
    PathNode node = root;
    // Add the path segments while descending into the root.
    for( PathSegment segment : template.getPath() ) {
      // If the root already contains a matching segment then descend to that node.
      // Otherwise create a child node, addValue it to the root and descend to it.
      // If this new node is a leaf node then set the value.
      if( ( node.children != null ) &&
          ( node.children.containsKey( segment ) ) ) {
        node = node.children.get( segment );
      } else {
        node = node.addPath( segment );
      }
    }
    if( template.getQuery().isEmpty() ) {
      // This might overwrite the template/value of an existing node.  Last in wins.
      node.template = template;
      node.value = value;
    } else {
      // Insert a query node into the tree.
      node.addQuery( template, value );
    }
  }

  public Match<V> match( Template path ) {
    List<PathNode> candidates = new ArrayList<PathNode>();
    List<PathNode> matches = new ArrayList<PathNode>();
    List<PathNode> temp;
    candidates.add( root );
    PathSegment segment;
    Iterator<PathSegment> segments = path.getPath().iterator();
    while( segments.hasNext() && !candidates.isEmpty() ) {
      segment = segments.next();
      matches.clear();
      pickMatchingChildren( segment, candidates, matches );
      // Swap the lists to avoid object allocation.
      temp = candidates; candidates = matches; matches = temp;
    }
    // The candidates list is used (not matches) because of the swap above.
    Match<V> winner = pickBestMatch( path, candidates );
    return winner;
  }

  private void pickMatchingChildren( Segment segment, List<PathNode> parents, List<PathNode> matches ) {
    for( PathNode parent : parents ) {
      if( parent.isGlob() ) {
        matches.add( parent );
      }
      if( parent.children != null ) {
        pickMatchingPathNodes( segment, parent.children.values(), matches );
      }
    }
  }

  private void pickMatchingPathNodes( Segment segment, Collection<PathNode> nodes, List<PathNode> matches ) {
    for( PathNode node : nodes ) {
      if( node.matches( segment ) ) {
        matches.add( node );
      }
    }
  }

  private Match<V> pickBestMatch( Template input, List<PathNode> candidates ) {
    Match<V> bestMatch = new Match<V>( null, null );
    PathNode bestPath = null;
    for( PathNode pathNode: candidates ) {
      if( ( bestPath == null ) || // If we don't have anything at all pick the node.
          ( pathNode.depth > bestPath.depth ) || // If the node is deeper than the best node, pick it.
          // If the node is the same depth as current best but is static and the best isn't then pick it.
          ( ( pathNode.depth == bestPath.depth ) && ( pathNode.isStatic() && !bestPath.isStatic() ) ) ) {
        if( !pathNode.hasQueries() ) {
          if( pathNode.template != null ) {
            bestPath = pathNode;
            bestMatch.template = pathNode.template;
            bestMatch.value = pathNode.value;
          }
        } else {
          QueryNode bestQuery = pickBestQueryMatch( input, pathNode );
          if( bestQuery != null && bestQuery.template != null ) {
            bestPath = pathNode;
            bestMatch.template = bestQuery.template;
            bestMatch.value = bestQuery.value;
          }
        }
      }
    }
    if( bestMatch.template == null ) {
      bestMatch = null;
    }
    return bestMatch;
  }

  private QueryNode pickBestQueryMatch( Template input, PathNode pathNode ) {
    QueryNode bestNode = null;
    int bestMatchCount = 0;
    for( QueryNode node : pathNode.queries ) {
      int nodeMatchCount = calcQueryMatchCount( node, input );
      if( nodeMatchCount > bestMatchCount ) {
        bestMatchCount = nodeMatchCount;
        bestNode = node;
      }
    }
    return bestNode;
  }

  private int calcQueryMatchCount( QueryNode node, Template input ) {
    int matchCount = 0;
    Map<String,QuerySegment> inputQuery = input.getQuery();
    Map<String,QuerySegment> templateQuery = node.template.getQuery();
    for( QuerySegment templateSegment : templateQuery.values() ) {
      QuerySegment inputSegment = inputQuery.get( templateSegment.getQueryName() );
      if( inputSegment != null && templateSegment.matches( inputSegment ) ) {
        matchCount++ ;
      } else {
        matchCount = 0;
        break;
      }
    }
    return matchCount;
  }

  public static class Match<V> {

    private Template template;
    private V value;

    private Match( Template template, V value ) {
      this.template = template;
      this.value = value;
    }

    public Template getTemplate() {
      return template;
    }

    public V getValue() {
      return value;
    }
  }

  private class PathNode extends Node {

    int depth; // Zero based depth of the node for "best node" calculation.
    Segment segment;
    Map<PathSegment,PathNode> children;
    Set<QueryNode> queries;

    private PathNode( PathNode parent, Segment segment ) {
      super( null, null );
      this.depth = ( parent == null ) ? 0 : parent.depth+1;
      this.segment = segment;
      this.children = null;
      this.queries = null;
    }

    private PathNode addPath( PathSegment path ) {
      if( children == null ) {
        children = new HashMap<PathSegment,PathNode>();
      }
      PathNode child = new PathNode( this, path );
      children.put( path, child );
      return child;
    }

    private QueryNode addQuery( Template template, V value ) {
      if( queries == null ) {
        queries = new HashSet<QueryNode>();
      }
      QueryNode query = new QueryNode( template, value );
      queries.add( query );
      return query;
    }

    private boolean isStatic() {
      return( segment.getType() == Segment.STATIC );
    }

    private boolean isGlob() {
      return( ( segment != null ) &&
              ( segment.getType() == Segment.WILDCARD ) &&
              ( segment.getMaxAllowed() > 1 ) );
    }

    private boolean isLeaf() {
      return( children == null || children.size() == 0 );
    }

    private boolean hasQueries() {
      return( queries != null && queries.size() > 0 );
    }

    private boolean matches( Segment segment ) {
      return this.segment.matches( segment );
    }

  }

  private class QueryNode extends Node {

    private QueryNode( Template template, V value ) {
      super( template, value );
    }

  }

  private class Node {

    Template template;
    V value;

    private Node( Template template, V value ) {
      this.template = template;
      this.value = value;
    }
  }

}
