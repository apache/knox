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
package org.apache.knox.gateway.services.topology.monitor;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.knox.gateway.topology.simple.ProviderConfigurationParser;

public class SharedProviderConfigMonitor extends FileAlterationListenerAdaptor implements FileFilter {

  public static final List<String> SUPPORTED_EXTENSIONS = ProviderConfigurationParser.SUPPORTED_EXTENSIONS;

  private DescriptorsMonitor descriptorsMonitor;
  private File descriptorsDir;

  public SharedProviderConfigMonitor(DescriptorsMonitor descMonitor, File descriptorsDir) {
    this.descriptorsMonitor = descMonitor;
    this.descriptorsDir = descriptorsDir;
  }

  @Override
  public void onFileCreate(File file) {
    onFileChange(file);
  }

  @Override
  public void onFileDelete(File file) {
    onFileChange(file);
  }

  @Override
  public void onFileChange(File file) {
    // For shared provider configuration, we need to update any simple descriptors that reference it
    for (File descriptor : getReferencingDescriptors(file)) {
      descriptor.setLastModified(System.currentTimeMillis());
    }
  }

  private List<File> getReferencingDescriptors(File sharedProviderConfig) {
    final List<File> references = new ArrayList<>();

    for (File descriptor : FileUtils.listFiles(descriptorsDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE)) {
      if (DescriptorsMonitor.SUPPORTED_EXTENSIONS.contains(FilenameUtils.getExtension(descriptor.getName()))) {
        for (String reference : descriptorsMonitor.getReferencingDescriptors(FilenameUtils.normalize(sharedProviderConfig.getAbsolutePath()))) {
          references.add(new File(reference));
        }
      }
    }

    return references;
  }

  @Override
  public boolean accept(File file) {
    boolean accept = false;
    if (!file.isDirectory() && file.canRead()) {
      String extension = FilenameUtils.getExtension(file.getName());
      if (SUPPORTED_EXTENSIONS.contains(extension)) {
        accept = true;
      }
    }
    return accept;
  }
}
