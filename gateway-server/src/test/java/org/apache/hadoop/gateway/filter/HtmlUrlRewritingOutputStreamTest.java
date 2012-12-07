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
package org.apache.hadoop.gateway.filter;

import org.apache.hadoop.test.category.FastTests;
import org.apache.hadoop.test.category.UnitTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

/**
 *
 */
@Category( { UnitTests.class, FastTests.class } )
public class HtmlUrlRewritingOutputStreamTest {

  private void assertMatch( Pattern pattern, String source, String expect ) {
    Matcher matcher = pattern.matcher( source );
    assertThat( matcher.find(), equalTo(true) );
    String tag = matcher.group( 1 );
    assertThat( tag, equalTo( expect ) );
  }

  @Test
  public void testTagExtractPattern() {

    Pattern pattern = HtmlUrlRewritingOutputStream.tagNamePattern;

    assertMatch( pattern, "<tag>", "tag" );
    assertMatch( pattern, "< tag>", "tag" );
    assertMatch( pattern, "<tag >", "tag" );
    assertMatch( pattern, "< tag >", "tag" );

    assertMatch( pattern, "<tag/>", "tag" );
    assertMatch( pattern, "< tag/>", "tag" );
    assertMatch( pattern, "<tag />", "tag" );
    assertMatch( pattern, "< tag />", "tag" );

    assertMatch( pattern, "<tag attr", "tag" );
    assertMatch( pattern, "< tag attr", "tag" );

    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"0;url=dfshealth.jsp\"/>", "meta" );
    assertMatch( pattern, "<Meta HTTP-EQUIV=\"REFRESH\" content=\"0;url=dfshealth.jsp\"/>", "Meta" );
    assertMatch( pattern, "<META HTTP-EQUIV=\"REFRESH\" content=\"0;url=dfshealth.jsp\"/>", "META" );

  }

  @Test
  public void testMetaUrlExtraction() {
    Pattern pattern = HtmlUrlRewritingOutputStream.tagUrlPatterns.get( "meta" );

    //Pattern pattern = Pattern.compile( ".*[\\s;]url=(.*?).*", Pattern.CASE_INSENSITIVE | Pattern.CANON_EQ );
    //Pattern pattern = Pattern.compile( ".*url\\s*=\\s*['\"]?(.*?)[;\\s'\"\\/>].*", Pattern.CASE_INSENSITIVE | Pattern.CANON_EQ );

    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"0;url=dfshealth.jsp\"/>", "dfshealth.jsp" );
    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"0; url=dfshealth.jsp\"/>", "dfshealth.jsp" );
    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"0;url =dfshealth.jsp\"/>", "dfshealth.jsp" );
    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"0;url= dfshealth.jsp\"/>", "dfshealth.jsp" );
    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"0;url=dfshealth.jsp \"/>", "dfshealth.jsp" );

    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"url=dfshealth.jsp\"/>", "dfshealth.jsp" );
    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\" url=dfshealth.jsp\"/>", "dfshealth.jsp" );
    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"url =dfshealth.jsp\"/>", "dfshealth.jsp" );
    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"url= dfshealth.jsp\"/>", "dfshealth.jsp" );
    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"url=dfshealth.jsp \"/>", "dfshealth.jsp" );

    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"url=dfshealth.jsp;0\"/>", "dfshealth.jsp" );
    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\" url=dfshealth.jsp;0\"/>", "dfshealth.jsp" );
    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"url =dfshealth.jsp;0\"/>", "dfshealth.jsp" );
    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"url= dfshealth.jsp;0\"/>", "dfshealth.jsp" );
    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"url=dfshealth.jsp ;0\"/>", "dfshealth.jsp" );

    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"0;url='dfshealth.jsp'\"/>", "dfshealth.jsp" );
    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"0; url='dfshealth.jsp'\"/>", "dfshealth.jsp" );
    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"0;url ='dfshealth.jsp'\"/>", "dfshealth.jsp" );
    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"0;url= 'dfshealth.jsp'\"/>", "dfshealth.jsp" );
    assertMatch( pattern, "<meta HTTP-EQUIV=\"REFRESH\" content=\"0;url='dfshealth.jsp' \"/>", "dfshealth.jsp" );

  }

  @Test
  public void testAbsoluteUrlPattern() {
    Pattern pattern = HtmlUrlRewritingOutputStream.absoluteUrlPattern;

    assertThat( pattern.matcher( "/" ).matches(), equalTo( true ) );
    assertThat( pattern.matcher( "/x" ).matches(), equalTo( true ) );
    assertThat( pattern.matcher( "/x/y" ).matches(), equalTo( true ) );
    assertThat( pattern.matcher( "/x/y.jsp" ).matches(), equalTo( true ) );

    assertThat( pattern.matcher( "" ).matches(), equalTo( false ) );
    assertThat( pattern.matcher( "x" ).matches(), equalTo( false ) );
    assertThat( pattern.matcher( "x/y" ).matches(), equalTo( false ) );
    assertThat( pattern.matcher( "x/y.jsp" ).matches(), equalTo( false ) );
  }

  @Test
  public void testLinkRewrite() {
    String markup = "<link href=\"/static/org.apache.hadoop.css\" rel=\"stylesheet\" type=\"text/css\" >";

    //Pattern pattern = Pattern.compile( ".*href\\s*=\\s*['\"](.*?)['\"\\>].*", Pattern.CASE_INSENSITIVE | Pattern.CANON_EQ );
    Pattern pattern = HtmlUrlRewritingOutputStream.tagUrlPatterns.get( "link" );

    assertMatch( pattern, markup, "/static/org.apache.hadoop.css" );

  }

  @Test
  public void testAnchorRewrite() {
    //Pattern pattern = Pattern.compile( ".*href\\s*=\\s*['\"]?(.*?)['\"\\>].*", Pattern.CASE_INSENSITIVE | Pattern.CANON_EQ );
    Pattern pattern = HtmlUrlRewritingOutputStream.tagUrlPatterns.get( "a" );

    String markup = "<a href=\"dfsnodelist.jsp?whatNodes=DECOMMISSIONING\">";
    assertMatch( pattern, markup, "dfsnodelist.jsp?whatNodes=DECOMMISSIONING" );

    markup = "<a href=/dfshealth.jsp>";
    assertMatch( pattern, markup, "/dfshealth.jsp" );
  }

  @Test
  public void testOnClick() {
    Pattern pattern = Pattern.compile( ".*window.document.location\\s*=\\s*['\"]?(.*?)['\"\\>].*", Pattern.CASE_INSENSITIVE | Pattern.CANON_EQ );
    String markup = "th class=headerASC onClick=\"window.document.location='/dfsnodelist.jsp?whatNodes=LIVE&sorter/field=name&sorter/order=DSC'\" title=\"sort on this column\">";
    assertMatch( pattern, markup, "/dfsnodelist.jsp?whatNodes=LIVE&sorter/field=name&sorter/order=DSC" );
  }

  @Test
  public void testCommentRewrite() {

    String markup = "<!--\n" +
        "body \n" +
        "  {\n" +
        "  font-face:sanserif;\n" +
        "  }\n" +
        "-->";
    Pattern tagNamePattern = HtmlUrlRewritingOutputStream.tagNamePattern;
    //Pattern tagNamePattern = Pattern.compile( "<\\s*([^>\\s/!]*).*", Pattern.CASE_INSENSITIVE | Pattern.CANON_EQ | Pattern.DOTALL );
    //Pattern tagNamePattern = Pattern.compile( ".*", Pattern.CASE_INSENSITIVE | Pattern.CANON_EQ | Pattern.DOTALL );

    Matcher matcher = tagNamePattern.matcher( markup );
    assertThat( matcher.matches(), equalTo( true ) );
    assertThat( matcher.group( 1 ), equalTo( "" ) );

  }

}
