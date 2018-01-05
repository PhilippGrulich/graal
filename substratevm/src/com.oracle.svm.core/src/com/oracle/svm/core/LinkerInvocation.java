/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface LinkerInvocation {

    List<String> getInputFiles();

    void addInputFile(String filename);

    void addInputFile(int index, String filename);

    Map<String, String> getSymbolAliases();

    void addSymbolAlias(String alias, String definition);

    List<String> getLibPaths();

    void addLibPath(String libPath);

    void addLibPath(int index, String libPath);

    List<String> getRPaths();

    void addRPath(String rPath);

    void addRPath(int index, String rPath);

    Path getOutputFile();

    void setOutputFile(Path out);

    List<String> getLinkedLibraries();

    void addLinkedLibrary(String libname);

    void addLinkedLibrary(int index, String libname);

    String getCompilerCommand();

    void setCompilerCommand(String command);

    List<String> getCommand();

    void addAdditionalPreOption(String option);
}