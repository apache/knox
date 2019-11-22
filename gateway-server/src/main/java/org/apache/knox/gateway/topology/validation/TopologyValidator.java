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
package org.apache.knox.gateway.topology.validation;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.knox.gateway.topology.Topology;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class TopologyValidator {

  private Collection<String> errors;
  private final String filePath;

  public TopologyValidator(Topology t){
    filePath = t.getUri().getPath();
  }

  public TopologyValidator(String path){
    this.filePath = path;
  }

  public TopologyValidator(URL file){
    filePath = file.getPath();
  }

  public boolean validateTopology() {
    errors = new LinkedList<>();
    try {
      SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      URL schemaUrl = getClass().getResource( "/conf/topology-v1.xsd" );
      Schema s = schemaFactory.newSchema( schemaUrl );
      Validator validator = s.newValidator();
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      final List<SAXParseException> exceptions = new LinkedList<>();
      validator.setErrorHandler(new ErrorHandler() {
        @Override
        public void warning(SAXParseException exception) {
          exceptions.add(exception);
        }

        @Override
        public void fatalError(SAXParseException exception) {
          exceptions.add(exception);
        }

        @Override
        public void error(SAXParseException exception) {
          exceptions.add(exception);
        }
      });

      File xml = new File(filePath);
      validator.validate(new StreamSource(xml));
      if(!exceptions.isEmpty()) {
        for (SAXParseException e : exceptions) {
          errors.add("Line: " + e.getLineNumber() + " -- " + e.getMessage());
        }
        return false;
      } else {
        return true;
      }

    } catch (IOException e) {
      errors.add("Error reading topology file");
      errors.add(e.getMessage());
      return false;
    } catch (SAXException e) {
      errors.add("There was a fatal error in parsing the xml file.");
      errors.add(e.getMessage());
      return false;
    } catch (NullPointerException n) {
      errors.add("Error retrieving schema from ClassLoader");
      return false;
    }
  }

  public Collection<String> getTopologyErrors(){
    if(errors != null){
      return errors;
    }else{
      validateTopology();
      return errors;
    }
  }

  public String getErrorString(){
    StringBuilder out = new StringBuilder();
    for(String s : getTopologyErrors()){
      out.append(s).append('\n');
    }
    return out.toString();
  }
}
