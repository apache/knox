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
package org.apache.knox.gateway.shell.commands;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import org.apache.knox.gateway.shell.CredentialCollectionException;
import org.apache.knox.gateway.shell.CredentialCollector;

public class KnoxLoginDialog implements CredentialCollector {
  public static final String COLLECTOR_TYPE = "LoginDialog";

  public char[] pass;
  public String username;
  String name;
  public boolean ok;

  @Override
  public void collect() throws CredentialCollectionException {
    JLabel jl = new JLabel("Enter Your username: ");
    JTextField juf = new JTextField(24);
    JLabel jl2 = new JLabel("Enter Your password:  ");
    JPasswordField jpf = new JPasswordField(24);
    Box box1 = Box.createHorizontalBox();
    box1.add(jl);
    box1.add(juf);
    Box box2 = Box.createHorizontalBox();
    box2.add(jl2);
    box2.add(jpf);
    Box box = Box.createVerticalBox();
    box.add(box1);
    box.add(box2);

    // JDK-5018574 : Unable to set focus to another component in JOptionPane
    SwingUtils.workAroundFocusIssue(juf);

    int x = JOptionPane.showConfirmDialog(null, box,
        "KnoxShell Login", JOptionPane.OK_CANCEL_OPTION);

    if (x == JOptionPane.OK_OPTION) {
      ok = true;
      username = juf.getText();
      pass = jpf.getPassword();
    }
  }

  @Override
  public String string() {
    return new String(pass);
  }

  @Override
  public char[] chars() {
    return pass;
  }

  @Override
  public byte[] bytes() {
    return null;
  }

  @Override
  public String type() {
    return "dialog";
  }

  @Override
  public String name() {
    return username;
  }

  @Override
  public void setPrompt(String prompt) {
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  public static void main(String[] args) {
    KnoxLoginDialog dlg = new KnoxLoginDialog();
    try {
      dlg.collect();
      if (dlg.ok) {
        System.out.println("username: " + dlg.username);
        System.out.println("password: " + new String(dlg.pass));
      }
    } catch (CredentialCollectionException e) {
      e.printStackTrace();
    }
  }
}
