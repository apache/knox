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
package org.apache.knox.gateway.plang;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.StringReader;

public class Parser {

    public AbstractSyntaxTree parse(String str) {
        if (str == null || str.trim().equals("")) {
            return null;
        }
        try (PushbackReader reader = new PushbackReader(new StringReader(str))) {
            AbstractSyntaxTree ast = parse(reader);
            String rest = peek(reader);
            if (rest != null) {
                throw new SyntaxException("Unexpected closing " + rest);
            }
            return ast;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private AbstractSyntaxTree parse(PushbackReader reader) throws IOException {
        String token = nextToken(reader);
        if ("(".equals(token)) {
            AbstractSyntaxTree children = new AbstractSyntaxTree(token);
            while (!")".equals(peek(reader))) {
                children.addChild(parse(reader));
            }
            nextToken(reader); // skip )
            return children;
        } else if (")".equals(token)) {
            throw new SyntaxException("Unexpected closing )");
        } else if ("".equals(token)) {
            throw new SyntaxException("Missing closing )");
        } else {
            return new AbstractSyntaxTree(token);
        }
    }

    private String nextToken(PushbackReader reader) throws IOException {
        String chr = peek(reader);
        if ("'".equals(chr)) {
            return parseString(reader);
        }
        if ("(".equals(chr) || ")".equals(chr)) {
            return String.valueOf((char) reader.read());
        }
        return parseAtom(reader);
    }

    private String parseAtom(PushbackReader reader) throws IOException {
        StringBuilder buffer = new StringBuilder();
        int chr = reader.read();
        while (chr != -1 && !Character.isWhitespace(chr) && ')' != chr) {
            buffer.append((char)chr);
            chr = reader.read();
        }
        if (chr == ')') {
            reader.unread(')');
        }
        return buffer.toString();
    }

    private String parseString(PushbackReader reader) throws IOException {
        StringBuilder str = new StringBuilder();
        str.append((char)reader.read());
        int chr = reader.read();
        while (chr != -1 && '\'' != chr) {
            str.append((char)chr);
            chr = reader.read();
        }
        if (chr == -1) {
            throw new SyntaxException("Unterminated string");
        }
        return str.append("'").toString();
    }

    private String peek(PushbackReader reader) throws IOException {
        int chr = reader.read();
        while (chr != -1 && Character.isWhitespace(chr)) {
            chr = reader.read();
        }
        if (chr == -1) {
            return null;
        }
        reader.unread(chr);
        return String.valueOf((char) chr);
    }
}
