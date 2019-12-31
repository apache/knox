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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class SQLDialog extends JDialog implements ActionListener {
  /**
   *
   */
  private static final long serialVersionUID = 1484424834637379872L;

  public SQLDialog(JFrame parent, String title, String message) {
    super(parent, title, true);
    if (parent != null) {
      Dimension parentSize = parent.getSize();
      Point p = parent.getLocation();
      setLocation(p.x + parentSize.width / 4, p.y + parentSize.height / 4);
    }
    JPanel sqlPane = new JPanel();
    sqlPane.add(new JLabel(message));
    JTextField sqlField = new JTextField(80);
    sqlPane.add(sqlField);
    getContentPane().add(sqlPane);
    JPanel buttonPane = new JPanel();
    JButton button = new JButton("Submit Query");
    buttonPane.add(button);
    button.addActionListener(this);
    getContentPane().add(buttonPane, BorderLayout.SOUTH);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    pack();
    setVisible(true);

//    JTextField textField = new JTextField(10);
//    JButton button = new JButton( "OK" );
//    JPanel panel = new JPanel();
//    panel.add( textField );
//    panel.add( button );
//    button.requestFocusInWindow();
//
//    JDialog dialog = new JDialog();
//    dialog.add( panel );
//    dialog.pack();
//    dialog.setVisible( true );
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    setVisible(false);
    dispose();
  }
}