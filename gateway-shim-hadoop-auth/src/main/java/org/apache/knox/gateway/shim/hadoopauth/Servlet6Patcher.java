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
package org.apache.knox.gateway.shim.hadoopauth;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Rewrites invocations of {@code jakarta.servlet.http.HttpServletResponse.setStatus(int, String)}
 * inside a shaded jar into {@code setStatus(int)}. The 2-arg form was removed in Servlet 6;
 * hadoop-auth 3.4.1 still calls it in {@code AuthenticationFilter} on the auth-failure path,
 * so the shaded classes would throw {@code NoSuchMethodError} at runtime otherwise.
 *
 * The rewrite drops the reason-phrase argument (the top-of-stack {@code String}) with a {@code POP}
 * and rewrites the {@code INVOKEINTERFACE} descriptor from {@code (ILjava/lang/String;)V} to
 * {@code (I)V}. The reason phrase is not carried over HTTP/2 anyway.
 *
 * Usage: {@code Servlet6Patcher path/to/shaded.jar}. The jar is rewritten in place.
 */
public final class Servlet6Patcher {

  private static final String HTTP_SERVLET_RESPONSE = "jakarta/servlet/http/HttpServletResponse";
  private static final String SET_STATUS = "setStatus";
  private static final String OLD_DESCRIPTOR = "(ILjava/lang/String;)V";
  private static final String NEW_DESCRIPTOR = "(I)V";

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.err.println("Usage: Servlet6Patcher <jar>");
      System.exit(2);
    }
    Path jar = Paths.get(args[0]);
    Path tmp = Files.createTempFile("shim-", ".jar");
    int patchedInvocations = 0;
    int patchedClasses = 0;
    try (JarFile src = new JarFile(jar.toFile());
         OutputStream out = Files.newOutputStream(tmp);
         JarOutputStream dst = new JarOutputStream(out)) {
      Enumeration<JarEntry> entries = src.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        JarEntry copy = new JarEntry(entry.getName());
        copy.setTime(entry.getTime());
        dst.putNextEntry(copy);
        if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
          try (InputStream in = src.getInputStream(entry)) {
            in.transferTo(dst);
          }
          dst.closeEntry();
          continue;
        }
        byte[] original;
        try (InputStream in = src.getInputStream(entry)) {
          original = in.readAllBytes();
        }
        PatchingVisitor visitor = new PatchingVisitor();
        byte[] rewritten = visitor.rewrite(original);
        if (visitor.hits > 0) {
          patchedInvocations += visitor.hits;
          patchedClasses += 1;
          dst.write(rewritten);
        } else {
          dst.write(original);
        }
        dst.closeEntry();
      }
    }
    Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
    System.out.println("Servlet6Patcher: rewrote " + patchedInvocations
        + " setStatus(int, String) call(s) across " + patchedClasses + " class(es)");
    if (patchedInvocations == 0) {
      // Defensive: if a future hadoop-auth upgrade already removed this call, the patch is a
      // no-op and safe to keep. If it's ever found to be missing when we expected it, the shim
      // was still built correctly; log-only, don't fail the build.
      System.out.println("Servlet6Patcher: nothing to patch (upstream may have already dropped "
          + "setStatus(int, String)).");
    }
  }

  /**
   * Visits every method in a class and rewrites {@code setStatus(int, String)} into
   * {@code setStatus(int)} preceded by a {@code POP} of the reason-phrase argument.
   */
  static final class PatchingVisitor extends ClassVisitor {
    int hits;

    PatchingVisitor() {
      super(Opcodes.ASM9);
    }

    byte[] rewrite(byte[] classBytes) {
      ClassReader reader = new ClassReader(classBytes);
      ClassWriter writer = new ClassWriter(reader, 0);
      this.cv = writer;
      reader.accept(this, 0);
      return writer.toByteArray();
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                     String signature, String[] exceptions) {
      MethodVisitor next = super.visitMethod(access, name, descriptor, signature, exceptions);
      return new PatchingMethodVisitor(next);
    }

    private final class PatchingMethodVisitor extends MethodVisitor {
      PatchingMethodVisitor(MethodVisitor delegate) {
        super(Opcodes.ASM9, delegate);
      }

      @Override
      public void visitMethodInsn(int opcode, String owner, String mName, String mDescriptor,
                                  boolean isInterface) {
        if ((opcode == Opcodes.INVOKEINTERFACE || opcode == Opcodes.INVOKEVIRTUAL)
            && HTTP_SERVLET_RESPONSE.equals(owner)
            && SET_STATUS.equals(mName)
            && OLD_DESCRIPTOR.equals(mDescriptor)) {
          // Stack before: ..., response, statusCode, reasonPhrase
          // We want:      ..., response, statusCode        then INVOKE... setStatus(I)V
          super.visitInsn(Opcodes.POP);
          super.visitMethodInsn(opcode, owner, mName, NEW_DESCRIPTOR, isInterface);
          hits++;
          return;
        }
        super.visitMethodInsn(opcode, owner, mName, mDescriptor, isInterface);
      }
    }
  }

  private Servlet6Patcher() {
    // utility
  }
}
