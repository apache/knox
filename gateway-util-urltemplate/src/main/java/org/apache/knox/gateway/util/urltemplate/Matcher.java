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
package org.apache.knox.gateway.util.urltemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
   Path
     Match
       {path} => {path=*}
       {path=*} // Match single path level. (ie wildcard)
       {path=**} // Match multiple path levels. (ie glob)
       {path=*.ext} // Match single level with simplified regex pattern.
     Expand
       {path} => {path=**} // Note: Default cardinality changes between match and expand.
   Query
     Match
       {queryParam} => {queryParam=*:queryParam}
       {queryParam=*} => {queryParam=*:queryParam} // Match single queryParam value.
       {queryParam=**} => {queryParam=**:queryParam} // Match multiple queryParam values.
       {queryParam=*suffix:other-queryParam}
     Expand
       {queryParam} -> {queryParam=**:queryParam} // Note: Default cardinality changes between match and expand.
       {queryParam=*} -> {queryParam=*:queryParam}
       {queryParam=**} -> {queryParam=**:queryParam}
       {queryParam=other-parm} -> {queryParam=**:otherparam} // Note: Default cardinality changes between match and expand.
       {queryParam=:other-parm} -> {queryParam=**:otherparam} // Note: Default cardinality changes between match and expand.
       {queryParam=*:other-parm} -> {queryParam=*:otherparam}
       {queryParam=**:other-parm} -> {queryParam=**:otherparam}
 */
public class Matcher<V> {

  private Map<Template,V> map;
  private PathNode root;

  public Matcher() {
    map = new LinkedHashMap<Template,V>();
    root = new PathNode( null, null );
  }

  public Matcher( Template template, V value ) {
    this();
    add( template, value );
  }

  public V get( Template template ) {
    return map.get( template );
  }

  public void add( Template template, V value ) {
    map.put( template, value );
    PathNode node = root;

    // Add the scheme segment to the tree (if any) while descending.
    node = add( node, template.getScheme() );

    // Add the authority segments (if any) while descending.
    node = add( node, template.getUsername() );
    node = add( node, template.getPassword() );
    node = add( node, template.getHost() );
    node = add( node, template.getPort() );

    // Add the path segments while descending.
    for( Path segment : template.getPath() ) {
      // If the root already contains a matching segment then descend to that pathNode.
      // Otherwise create a child pathNode, addValue it to the root and descend to it.
      // If this new pathNode is a leaf pathNode then set the value.
      node = add( node, segment );
    }

    // Add the fragment (if any) segments while descending.
    // Note: Doing it this way puts the fragment above the query parameters in the match order.
    node = add( node, template.getFragment() );

    if( template.getQuery().isEmpty() && template.getExtra() == null ) {
      // The first template with a value at this node wins.
      if( node.value == null ) {
        node.template = template;
        node.value = value;
      }
    } else {
      // Insert a query pathNode into the tree.
      node.addQuery( template, value );
    }
  }

  private PathNode add( PathNode parent, Segment segment ) {
    PathNode child = parent;
    if( segment != null ) {
      if( ( parent.children != null ) && ( parent.children.containsKey( segment ) ) ) {
        child = parent.children.get( segment );
      } else {
        child = parent.addPath( segment );
      }
    }
    return child;
  }

  public Match match( Template input ) {
    Status status = new Status();
    status.candidates.add( new MatchSegment( null, root, null, null ) );
    boolean matches = true;
    // Separate &= statements for debugability.
    matches &= matchScheme( input, status );
    matches &= matchAuthority( input, status );
    matches &= matchPath( input, status );
    matches &= matchFragment( input, status );
    Match winner;
    if( matches ) {
      winner = pickBestMatch( input, status );
    } else {
      winner = null;
    }
    return winner;
  }

  private boolean matchScheme( Template input, Status status ) {
    pickMatchingChildren( input.getScheme(), status );
    return status.hasCandidates();
  }

  private boolean matchAuthority( Template input, Status status ) {
    pickMatchingChildren( input.getUsername(), status );
    pickMatchingChildren( input.getPassword(), status );
    pickMatchingChildren( input.getHost(), status );
    // port does not makes sense without host
    if(input.getHost() != null) {
      // port is optional, since default ports do not need to present in URL
      pickMatchingOptionalSegment(input.getPort(), status);
    }
    return status.hasCandidates();
  }

  private boolean matchPath( Template input, Status status ) {
    Path segment;
    Iterator<Path> segments = input.getPath().iterator();
    while( segments.hasNext() && status.hasCandidates() ) {
      segment = segments.next();
      pickMatchingChildren( segment, status );
    }
    return status.hasCandidates();
  }

  private boolean matchFragment( Template input, Status status ) {
    pickMatchingChildren( input.getFragment(), status );
    return status.hasCandidates();
  }

  private void pickMatchingChildren( Segment segment, Status status ) {
    if( segment != null ) {
      for( MatchSegment parent : status.candidates ) {
        if( parent.pathNode.hasGlob() ) {
          status.matches.add( new MatchSegment( parent, parent.pathNode, parent.pathNode.segment, segment ) );
        }
        if( parent.pathNode.children != null ) {
          for( PathNode node : parent.pathNode.children.values() ) {
            if( node.matches( segment ) ) {
              status.matches.add( new MatchSegment( parent, node, node.segment, segment ) );
            }
          }
        }
      }
      status.swapMatchesToCandidates();
    }
  }

  /**
   * optional segment, if it does not present (it is null) it is accepted
   */
  private void pickMatchingOptionalSegment( Segment segment, Status status ) {
    for( MatchSegment parent : status.candidates ) {
      if( parent.pathNode.children != null ) {
        for( PathNode node : parent.pathNode.children.values() ) {
          if( segment != null ) {
            if( node.matches( segment ) ) {
              status.matches.add( new MatchSegment( parent, node, node.segment, segment ) );
            }
          } else {
            status.matches.add( new MatchSegment( parent, node, node.segment, segment ) );
          }
        }
      }
    }
    status.swapMatchesToCandidates();
  }

  private Match pickBestMatch( Template input, Status status ) {
    Match bestMatch = new Match( null, null );
    PathNode bestPath = null;
    QueryNode bestQuery = null;
    MatchSegment bestMatchSegment = null;
    for( MatchSegment matchSegment: status.candidates ) {
      PathNode pathNode = matchSegment.pathNode;
      if( ( bestPath == null ) || // If we don't have anything at all pick the pathNode.
          ( pathNode.depth > bestPath.depth ) || // If the pathNode is deeper than the best pathNode, pick it.
          // If the pathNode is the same depth as current best but is static and the best isn't then pick it.
          ( ( pathNode.depth == bestPath.depth ) && ( pathNode.getType() < bestPath.getType() ) ) ) {
        // If the path node has a template then assume we will pick the path node.
        if( pathNode.template != null ) {
          bestPath = pathNode;
          bestQuery = null;
          bestMatch.template = pathNode.template;
          bestMatch.value = pathNode.value;
          bestMatchSegment = matchSegment;
        }
        // If the path node has queries see if one is better match than the path node itself.
        if( pathNode.hasQueries() ) {
          bestQuery = pickBestQueryMatch( input, pathNode );
          if( bestQuery != null && bestQuery.template != null ) {
            bestPath = pathNode;
            bestMatch.template = bestQuery.template;
            bestMatch.value = bestQuery.value;
            bestMatchSegment = matchSegment;
          }
        }
      }
    }
    Match match = createMatch( bestMatchSegment, bestPath, bestQuery, input );
    return match;
  }

  private QueryNode pickBestQueryMatch( Template input, PathNode pathNode ) {
    QueryNode bestNode = null;
    int bestMatchCount = 0;
    for( QueryNode node : pathNode.queries ) {
      Query extra = node.template.getExtra();
      int nodeQuerySize = node.template.getQuery().size();
      int queryMatchCount = calcQueryMatchCount( node, input );
      boolean matchesNamedQueries = queryMatchCount >= nodeQuerySize;
      boolean matchesExtraQuery =
          ( ( extra == null ) ||
            ( Segment.GLOB_PATTERN.equals( extra.getQueryName() ) ) ||
            ( input.getQuery().size() > nodeQuerySize ) );
      if( ( bestNode == null || queryMatchCount > bestMatchCount ) && ( matchesNamedQueries && matchesExtraQuery ) ) {
        bestMatchCount = queryMatchCount;
        bestNode = node;
      }
    }
    return bestNode;
  }

  private int calcQueryMatchCount( QueryNode node, Template input ) {
    int matchCount = 0;
    Map<String,Query> inputQuery = input.getQuery();
    Map<String,Query> templateQuery = node.template.getQuery();
    for( Query templateSegment : templateQuery.values() ) {
      Query inputSegment = inputQuery.get( templateSegment.getQueryName() );
      if( inputSegment != null && templateSegment.matches( inputSegment ) ) {
        matchCount++ ;
      } else {
        matchCount = 0;
        break;
      }
    }
    return matchCount;
  }

  private Match createMatch( MatchSegment bestMatchSegment, PathNode bestPath, QueryNode bestQuery, Template input ) {
    Match match = null;

    if( bestPath != null ) { //&& ( bestQuery != null || !bestPath.hasQueries() ) ) {

      if( bestQuery != null ) {
        match = new Match( bestQuery.template, bestQuery.value );
      } else {
        match = new Match( bestPath.template, bestPath.value );
      }

      MatchParams matchParams = new MatchParams();

      // Add the matching query segments to the end of the list.
      if( bestQuery != null ) {
        Map<String,Query> inputQuery = input.getQuery();
        for( Query templateSegment : bestQuery.template.getQuery().values() ) {
          Query inputSegment = inputQuery.get( templateSegment.getQueryName() );
          if( inputSegment != null && templateSegment.matches( inputSegment ) ) {
            extractSegmentParams( templateSegment, inputSegment, matchParams );
          }
        }
      }

      // If the template has the "extra" query queryParam then collect query params that were
      // not already matched.
      if( bestQuery != null ) {
        Query extra = bestQuery.template.getExtra();
        if( extra != null ) {
          String paramName = extra.getParamName();
          if( paramName != null && paramName.length() > 0 ) {
            for( Query query: input.getQuery().values() ) {
              String queryName = query.getQueryName();
              if( matchParams.resolve( queryName ) == null ) {
                for( Segment.Value value: query.getValues() ) {
                  matchParams.addValue( queryName, value.getEffectivePattern() );
                }
              }
            }
          }
        }
      }

      // Walk back up the matching segment tree.
      MatchSegment matchSegment = bestMatchSegment;
      while( matchSegment != null && matchSegment.pathNode.depth > 0 ) {
        extractSegmentParams( matchSegment.templateSegment, matchSegment.inputSegment, matchParams );
        matchSegment = matchSegment.parentMatch;
      }
      match.params = matchParams;
    }
    return match;
  }

  private void extractSegmentParams( Segment extractSegment, Segment inputSegment, MatchParams params ) {
    if( extractSegment != null && inputSegment != null ) {
      String paramName = extractSegment.getParamName();
      if( paramName.length() > 0 ) {
        for( Segment.Value value: inputSegment.getValues() ) {
          params.insertValue( paramName, value.getEffectivePattern() );
        }
      }
    }
  }

  private class Status {

    List<MatchSegment> candidates = new ArrayList<>();
    List<MatchSegment> matches = new ArrayList<>();
    List<MatchSegment> temp;

    private void swapMatchesToCandidates() {
      temp = candidates; candidates = matches; matches = temp;
      matches.clear();
    }

    private boolean hasCandidates() {
      return !candidates.isEmpty();
    }
  }

  private class MatchSegment {
    private MatchSegment parentMatch;
    private PathNode pathNode;
    private Segment templateSegment;
    private Segment inputSegment;

    private MatchSegment( MatchSegment parent, PathNode node, Segment templateSegment, Segment inputSegment ) {
      this.parentMatch = parent;
      this.pathNode = node;
      this.templateSegment = templateSegment;
      this.inputSegment = inputSegment;
    }
  }

  private class MatchParams implements Params {

    private Map<String,List<String>> map = new HashMap<>();

    public Set<String> getNames() {
      return map.keySet();
    }

    private List<String> getOrAddValues( String name ) {
      List<String> values = resolve( name );
      if( values == null ) {
        values = new ArrayList<>( 1 );
        map.put( name, values );
      }
      return values;
    }

    public void addValue( String name, String value ) {
      List<String> values = getOrAddValues( name );
      values.add( value );
    }

    public void insertValue( String name, String value ) {
      List<String> values = getOrAddValues( name );
      values.add( 0, value );
    }

    @Override
    public List<String> resolve( String name ) {
      return map.get( name );
    }

  }

  public class Match {
    private Template template;
    private V value;
    private Params params;
    //TODO private Params extra;

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

    public Params getParams() {
      return params;
    }
  }

  private class PathNode extends Node {

    int depth; // Zero based depth of the pathNode for "best pathNode" calculation.
    Segment segment;
    Map<Segment,PathNode> children;
    Set<QueryNode> queries;

    private PathNode( PathNode parent, Segment segment ) {
      super( null, null );
      this.depth = ( parent == null ) ? 0 : parent.depth+1;
      this.segment = segment;
      this.children = null;
      this.queries = null;
    }

    private PathNode addPath( Segment path ) {
      if( children == null ) {
        children = new LinkedHashMap<Segment,PathNode>();
      }
      PathNode child = new PathNode( this, path );
      children.put( path, child );
      return child;
    }

    private QueryNode addQuery( Template template, V value ) {
      if( queries == null ) {
        queries = new LinkedHashSet<>();
      }
      QueryNode query = new QueryNode( template, value );
      queries.add( query );
      return query;
    }

    private int getType() {
      int type = Segment.UNKNOWN;
      if( segment != null ) {
        for( Segment.Value value: segment.getValues() ) {
          int vType = value.getType();
          type = type < vType ? type : vType;
          if( type == Segment.STATIC ) {
            break;
          }
        }
      }
      return type;
    }

    private boolean hasGlob() {
      boolean is = false;
      if( segment != null ) {
        for( Segment.Value value: segment.getValues() ) {
          if( Segment.GLOB == value.getType() ) {
            is = true;
          }
        }
      }
      return is;
    }

    private boolean hasQueries() {
      return( queries != null && !queries.isEmpty() );
    }

    private boolean matches( Segment segment ) {
      return( this.segment.matches( segment ) );
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
