/*
 * Copyright (c) 2002-2012 Gargoyle Software Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gargoylesoftware.htmlunit.source;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import com.gargoylesoftware.htmlunit.BrowserRunner.Browser;

/**
 * Extracts the needed expectation from the real browsers output.
 *
 * In IE8 raw file, test outputs should be manually separated in a new line.
 *
 * @version $Revision$
 * @author Ahmed Ashour
 */
public final class JQuery173Extractor {

    private JQuery173Extractor() {
    }

    /**
     * Transforms the raw expectation, to the needed one by HtmlUnit.
     * This methods puts only the main line of the test output, without the details.
     *
     * @param input the raw file of real browser, with header and footer removed
     * @param output the expectation
     * @throws IOException if an error occurs
     */
    public static void extractExpectations(final File input, final File output) throws IOException {
        final BufferedReader reader = new BufferedReader(new FileReader(input));
        final BufferedWriter writer = new BufferedWriter(new FileWriter(output));
        int testNumber = 1;
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("" + testNumber + '.') && line.endsWith("Rerun")) {
                line = line.substring(0, line.length() - 5);
                writer.write(line + "\n");
                testNumber++;
            }
        }
        System.out.println("Last output #" + (testNumber - 1));
        reader.close();
        writer.close();
    }

    /**
     * Generates the java code of the test cases.
     * @param dir the directory which holds the expectations
     * @throws IOException if an error occurs.
     */
    public static void generateTestCases(final File dir) throws IOException {
        final Browser[] browsers = Browser.values();
        // main browsers regardless of version e.g. FF
        final List<String> mainNames = new ArrayList<String>();
        for (final Browser b : browsers) {
            final String name = b.name();
            if (!"NONE".equals(name) && Character.isLetter(name.charAt(name.length() - 1))) {
                mainNames.add(name);
            }
        }
        final Map<String, List<String>> browserVersions = new HashMap<String, List<String>>();
        for (final Browser b : browsers) {
            final String name = b.name();
            for (final String mainName : mainNames) {
                if (!name.equals(mainName) && mainName.startsWith(name)) {
                    List<String> list = browserVersions.get(mainName);
                    if (list == null) {
                        list = new ArrayList<String>();
                        browserVersions.put(mainName, list);
                    }
                    list.add(name);
                }
            }
        }
        final Map<String, List<String>> browserExpectations = new HashMap<String, List<String>>();
        for (final File file : dir.listFiles()) {
            if (file.isFile() && file.getName().endsWith(".txt")) {
                for (final Browser b : browsers) {
                    if (file.getName().equals(b.name().replace('_', '.') + ".txt")) {
                        browserExpectations.put(b.name(), FileUtils.readLines(file));
                    }
                }
            }
        }
        int testNumber = 0;
        while (true) {
            final Map<String, String> testExpectation = new HashMap<String, String>();
            for (final Browser b : browsers) {
                final String name = b.name();
                if (browserExpectations.containsKey(name) && testNumber < browserExpectations.get(name).size()) {
                    String expectation = browserExpectations.get(name).get(testNumber);
                    if (expectation != null) {
                        expectation = expectation.substring(expectation.indexOf('.') + 1);
                        if (expectation.charAt(0) == ' ') {
                            expectation = expectation.substring(1);
                        }
                        testExpectation.put(name,
                                expectation.replaceAll("\\\\", "\\\\\\\\").replaceAll("\\\"", "\\\\\""));
                    }
                }
            }
            if (testExpectation.isEmpty()) {
                break;
            }
            System.out.println("    /**");
            System.out.println("     * @throws Exception if an error occurs");
            System.out.println("     */");
            System.out.println("    @Test");
            System.out.print("    @Alerts(");
            boolean allSame = true;
            String lastExpect = null;
            for (final String e : testExpectation.values()) {
                if (lastExpect == null) {
                    lastExpect = e;
                }
                else if (!e.equals(lastExpect)) {
                    allSame = false;
                    break;
                }
            }
            if (allSame) {
                final String first = testExpectation.keySet().iterator().next();
                String expectation = testExpectation.get(first);
                if (expectation.length() > 100) {
                    expectation = expectation.substring(0, 50) + "\"\n            + \"" + expectation.substring(50);
                }
                System.out.print("\"" + expectation + '"');
            }
            else {
                boolean first = true;
                boolean longLine = false;
                for (final String browser : testExpectation.keySet()) {
                    if (!first) {
                        System.out.print(",");
                        if (longLine) {
                            System.out.print("\n        ");
                        }
                        else {
                            System.out.print(' ');
                        }
                    }
                    String expectation = testExpectation.get(browser);
                    if (expectation.length() > 40) {
                        expectation = expectation.substring(0, 40) + "\"\n            + \"" + expectation.substring(40);
                        longLine = true;
                    }
                    else {
                        longLine = false;
                    }
                    System.out.print(browser + " = \"" + expectation + '"');
                    if (first) {
                        first = false;
                    }
                }
            }
            System.out.println(")");
            if (browsers.length - 1 > testExpectation.size()) {
                //there are @NYI
                System.out.print("    @NotYetImplemented({ ");
                boolean first = true;
                for (final Browser b : browsers) {
                    final String name = b.name();
                    if (!mainNames.contains(name) && !"NONE".equals(name) && !testExpectation.containsKey(name)) {
                        if (!first) {
                            System.out.print(", ");
                        }
                        System.out.print(name);
                        if (first) {
                            first = false;
                        }
                    }
                }
                System.out.println(" })");
            }
            System.out.println("    public void test_" + (testNumber + 1) + "() throws Exception {");
            System.out.println("        runTest(" + (testNumber + 1) + ");");
            System.out.println("    }");
            System.out.println();
            testNumber++;
        }
    }
}
