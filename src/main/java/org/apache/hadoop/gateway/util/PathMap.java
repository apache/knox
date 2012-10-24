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
package org.apache.hadoop.gateway.util;

import java.util.*;
import java.util.regex.Pattern;

/**
 *
 */
public class PathMap<V> {

  Map<String,V> map;
  Node root;

  public PathMap() {
    map = new HashMap<String,V>();
    root = new Node( null, "" );
  }

  public void put( String path, V value ) {
    StringTokenizer parser = new StringTokenizer( path, "/" );
    Node node = root;
    while( parser.hasMoreTokens() ) {
      String segment = parser.nextToken();
      // If the tree already contains a matching segment then descend to that node.
      // Otherwise create a child node, addValue it to the tree and descend to it.
      // If this new node is a leaf node then set the value.
      if( node.children.containsKey( segment ) ) {
        node = node.children.get( segment );
      } else {
        Node child = new Node( node, segment );
        node.children.put( segment, child );
        node = child;
      }
    }
    // This might set the value of an existing node.  Possibly overwriting an existing value.
    node.value = value;
    map.put( path, value );
  }

  public V get( String pathSpec ) {
    return map.get( pathSpec );
  }

  public V pick( String path ) {
    StringTokenizer parser = new StringTokenizer( path, "/" );
    List<Node> candidates = new ArrayList<Node>();
    List<Node> matches = new ArrayList<Node>();
    List<Node> temp;
    candidates.add( root );
    String segment;
    while( parser.hasMoreTokens() && !candidates.isEmpty() ) {
      segment = parser.nextToken();
      matches.clear();
      pickMatchingChildren( segment, candidates, matches );
      temp = candidates; candidates = matches; matches = temp; // Swap the lists to avoid object allocation.
    }
    Node winner = pickBestMatch( candidates ); // The candidates list is used (not picks) because of the swap above.
    return winner == null ? null : winner.value;
  }

  private void pickMatchingNodes( String segment, Collection<Node> nodes, List<Node> matches ) {
    for( Node node : nodes ) {
      if( node.matches( segment ) ) {
        matches.add( node );
      }
    }
  }

  private void pickMatchingChildren( String segment, List<Node> parents, List<Node> matches ) {
    for( Node parent : parents ) {
      if( parent.glob ) {
        matches.add( parent );
      }
      pickMatchingNodes( segment, parent.children.values(), matches );
    }
  }

  // Return the first deepest node with a value.
  private Node pickBestMatch( List<Node> nodes ) {
    Node best = null;
    for( Node node: nodes ) {
      if( ( node.value != null ) && // The node must have a value to be picked.
          ( ( best == null ) || // If we don't have anything at all pick the node.
            ( node.depth > best.depth ) || // If the node is deeper than the best node, pick it.
            // If the node is the same depth as best but isn't a wildcard or isn't a glob then pick it.
            ( ( node.depth == best.depth ) &&
              ( ( ( best.wildcard && !node.wildcard ) ||
                  ( best.glob && !node.glob ) ) ) ) ) ) {
        best = node;
      }
    }
    return best;
  }

  private static Pattern createPattern( String segment ) {
    segment = segment.replaceAll( "\\(", "\\\\(" ); // Turn '(' into '\('.
    segment = segment.replaceAll( "\\)", "\\\\)" ); // Turn '(' into '\)'.
    segment = segment.replaceAll( "\\.", "\\\\." ); // Turn '.' into '\.'.
    segment = segment.replaceAll( "\\*\\*", "|" ); // Temporarily turn '**' into '|' because segments can't contain '|'.
    segment = segment.replaceAll( "\\*", "(.*)" ); // Turn '*' into '/*'.
    segment = segment.replaceAll( "\\|", "(.*)" ); // Turn '/' back into '.*'.
    return Pattern.compile( segment );
  }

  private class Node {

    int depth; // Zero based depth of the node for "best node" calculation.
    String segment;
    Pattern pattern;
    boolean wildcard; // True if segment contains *
    boolean glob; // True if segment equals **
    V value;
    Map<String,Node> children;

    private Node( Node parent, String segment ) {
      this.value = null;
      this.segment = segment;
      this.pattern = createPattern( segment );
      this.wildcard = segment.contains( "*" );
      this.glob = segment.equals( "**" );
      this.depth = ( parent == null ) ? 0 : parent.depth+1;
      this.children = new LinkedHashMap<String,Node>();
    }

    private boolean matches( String segment ) {
      return pattern.matcher( segment ).matches();
    }

    private boolean isLeaf() {
      return children.isEmpty() && value != null;
    }

  }

}
