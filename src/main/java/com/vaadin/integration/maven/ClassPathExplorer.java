/*
 * Copyright 2000-2014 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.integration.maven;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Utility class to collect widgetset related information from classpath.
 * Utility will seek all directories from classpaths, and jar files having
 * "Vaadin-Widgetsets" key in their manifest file.
 * <p>
 * Used by WidgetMapGenerator and ide tools to implement some monkey coding for
 * you.
 * <p>
 * Developer notice: If you end up reading this comment, I guess you have faced
 * a sluggish performance of widget compilation or unreliable detection of
 * components in your classpaths. The thing you might be able to do is to use
 * annotation processing tool like apt to generate the needed information. Then
 * either use that information in WidgetMapGenerator or create the appropriate
 * monkey code for gwt directly in annotation processor and get rid of
 * WidgetMapGenerator. Using annotation processor might be a good idea when
 * dropping Java 1.5 support (integrated to javac in 6).
 *
 */
public class ClassPathExplorer {

    /**
     * Contains information about widgetsets and themes found on the classpath
     *
     * @since 7.1
     */
    public static class LocationInfo {

        private final Map<String, URL> widgetsets;

        private final Map<String, URL> addonStyles;

        public LocationInfo(Map<String, URL> widgetsets, Map<String, URL> themes) {
            this.widgetsets = widgetsets;
            addonStyles = themes;
        }

        public Map<String, URL> getWidgetsets() {
            return widgetsets;
        }

        public Map<String, URL> getAddonStyles() {
            return addonStyles;
        }

    }

    private static final boolean debug = true;

    /**
     * No instantiation from outside, callable methods are static.
     */
    private ClassPathExplorer() {
    }

    /**
     * Finds the names and locations of widgetsets available on the class path.
     *
     * @param classpathLocations
     * @return map from widgetset classname to widgetset location URL
     */
    public static Map<String, URL> getAvailableWidgetSets(
            Map<String, URL> classpathLocations) {
        return getAvailableWidgetSetsAndStylesheets(classpathLocations).
                getWidgetsets();
    }

    /**
     * Finds the names and locations of widgetsets and themes available on the
     * class path.
     *
     * @param classpathLocations
     * @return
     */
    public static LocationInfo getAvailableWidgetSetsAndStylesheets(
            Map<String, URL> classpathLocations) {
        long start = System.currentTimeMillis();
        Map<String, URL> widgetsets = new HashMap<String, URL>();
        Map<String, URL> themes = new HashMap<String, URL>();
        Set<String> keySet = classpathLocations.keySet();
        for (String location : keySet) {
            searchForWidgetSetsAndAddonStyles(location, classpathLocations,
                    widgetsets, themes);
        }
        long end = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder();
        sb.append("Widgetsets found from classpath:\n");
        for (String ws : widgetsets.keySet()) {
            sb.append("\t");
            sb.append(ws);
            sb.append(" in ");
            sb.append(widgetsets.get(ws));
            sb.append("\n");
        }

        log(sb.toString());
        log("Search took " + (end - start) + "ms");
        return new LocationInfo(widgetsets, themes);
    }

    /**
     * Finds all GWT modules / Vaadin widgetsets and Addon styles in a valid
     * location.
     *
     * If the location is a directory, all GWT modules (files with the
     * ".gwt.xml" extension) are added to widgetsets.
     *
     * If the location is a JAR file, the comma-separated values of the
     * "Vaadin-Widgetsets" attribute in its manifest are added to widgetsets.
     *
     * @param locationString an entry in {@link #classpathLocations}
     * @param widgetsets a map from widgetset name (including package, with dots
     * as separators) to a URL (see {@link #classpathLocations}) - new entries
     * are added to this map
     */
    private static void searchForWidgetSetsAndAddonStyles(
            String locationString, Map<String, URL> inClasspathLocations,
            Map<String, URL> widgetsets,
            Map<String, URL> addonStyles) {

        URL location = inClasspathLocations.get(locationString);
        File directory = new File(location.getFile());

        if (directory.exists() && directory.isDirectory() && !directory.
                isHidden()) {
            // Get the list of the files contained in the directory
            String[] files = directory.list();
            if (files != null) {
                for (String file : files) {
                    // we are only interested in .gwt.xml files
                    if (!file.endsWith(".gwt.xml")) {
                        continue;
                    }
                    // remove the .gwt.xml extension
                    String classname = file.substring(0, file.length() - 8);
                    String packageName = locationString.substring(locationString
                            .lastIndexOf("/") + 1);
                    classname = packageName + "." + classname;
                    if (!isWidgetset(classname)) {
                        // Only return widgetsets and not GWT modules to avoid
                        // comparing modules and widgetsets
                        continue;
                    }
                    if (!widgetsets.containsKey(classname)) {
                        String packagePath = packageName.replaceAll("\\.", "/");
                        String basePath = location.getFile().replaceAll(
                                "/" + packagePath + "$", "");
                        try {
                            URL url = new URL(location.getProtocol(),
                                    location.getHost(), location.getPort(),
                                    basePath);
                            widgetsets.put(classname, url);
                        } catch (MalformedURLException e) {
                            // should never happen as based on an existing URL,
                            // only changing end of file name/path part
                            error("Error locating the widgetset " + classname, e);
                        }
                    }
                }
            }
        } else {

            try {
                // check files in jar file, entries will list all directories
                // and files in jar
                URLConnection openConnection = location.openConnection();

                JarFile jarFile;
                if (openConnection instanceof JarURLConnection) {
                    JarURLConnection conn = (JarURLConnection) openConnection;
                    jarFile = conn.getJarFile();
                } else {
                    jarFile = new JarFile(location.getFile());
                }

                Manifest manifest = jarFile.getManifest();
                if (manifest == null) {
                    // No manifest so this is not a Vaadin Add-on
                    return;
                }

                // Check for widgetset attribute
                String value = manifest.getMainAttributes().getValue(
                        "Vaadin-Widgetsets");
                if (value != null) {
                    String[] widgetsetNames = value.split(",");
                    for (int i = 0; i < widgetsetNames.length; i++) {
                        String widgetsetname = widgetsetNames[i].trim();
                        if (!widgetsetname.equals("")) {
                            widgetsets.put(widgetsetname, location);
                        }
                    }
                }

                // Check for theme attribute
                value = manifest.getMainAttributes().getValue(
                        "Vaadin-Stylesheets");
                if (value != null) {
                    String[] stylesheets = value.split(",");
                    for (int i = 0; i < stylesheets.length; i++) {
                        String stylesheet = stylesheets[i].trim();
                        if (!stylesheet.equals("")) {
                            addonStyles.put(stylesheet, location);
                        }
                    }
                }
            } catch (IOException e) {
                log("Error parsing jar file: " + location);
            }
        }
    }

    /**
     * Find and return the default source directory where to create new
     * widgetsets.
     *
     * Return the first directory (not a JAR file etc.) on the classpath by
     * default.
     *
     * TODO this could be done better...
     *
     * @param classpathEntries
     * @return URL
     */
    public static URL getDefaultSourceDirectory(List<String> classpathEntries) {

        if (debug) {
            debug("classpathLocations values:");
            for (String location : classpathEntries) {
                debug(location);
            }
        }

        Iterator<String> it = classpathEntries.iterator();
        while (it.hasNext()) {
            String entry = it.next();

            File directory = new File(entry);
            if (directory.exists() && !directory.isHidden()
                    && directory.isDirectory()) {
                try {
                    return new URL("file://" + directory.getCanonicalPath());
                } catch (MalformedURLException e) {
                    // ignore: continue to the next classpath entry
                    if (debug) {
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    // ignore: continue to the next classpath entry
                    if (debug) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return null;
    }

    private static void log(String message) {
        System.out.println(message);
    }

    private static void error(String message, Exception e) {
        System.err.println(message);
        e.printStackTrace();
    }

    private static void debug(String message) {
        if (debug) {
            System.out.println(message);
        }
    }

    static boolean isWidgetset(String gwtModuleName) {
        return gwtModuleName.toLowerCase().contains("widgetset");
    }

    static final int computeMajorVersion(String productVersion) {
        return productVersion == null || productVersion.isEmpty() ? 0
                : Integer.parseInt(productVersion.replaceFirst("[^\\d]+.*$", ""));
    }
    
    static String getErrorMessage(String key, Object... pars) {
        Locale loc = Locale.getDefault();
        ResourceBundle res = ResourceBundle.getBundle(
                "CvalChecker", loc);
        String msg = res.getString(key);
        return new MessageFormat(msg, loc).format(pars);
    }


}
