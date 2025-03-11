package org.codehaus.mojo.gwt.shell;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import com.vaadin.integration.maven.ClassPathExplorer;
import com.vaadin.wscdn.client.WidgetSetRequest;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.mojo.gwt.AbstractGwtModuleMojo;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils;

/**
 * Support running GWT SDK Tools as forked JVM with classpath set according to
 * project source/resource directories and dependencies.
 *
 * @author ccollins
 * @author cooper
 * @author willpugh
 * @version $Id$
 */
public abstract class AbstractGwtShellMojo extends AbstractGwtModuleMojo {
    /**
     * Location on filesystem where GWT will write generated content for review
     * (-gen option to GWT Compiler).
     * <p>
     * Can be set from command line using '-Dgwt.gen=...'
     */
    @Parameter(defaultValue = "${project.build.directory}/.generated", property = "gwt.gen")
    private File gen;

    /**
     * Whether to add -gen parameter to the compiler command line
     * <p>
     * Can be set from command line using '-Dgwt.genParam=false'. Defaults to
     * 'true' for backwards compatibility.
     *
     * @since 2.5.0-rc1
     */
    @Parameter(defaultValue = "true", property = "gwt.genParam")
    private boolean genParam;

    /**
     * GWT logging level (-logLevel ERROR, WARN, INFO, TRACE, DEBUG, SPAM, or
     * ALL).
     * <p>
     * Can be set from command line using '-Dgwt.logLevel=...'
     */
    @Parameter(defaultValue = "INFO", property = "gwt.logLevel")
    private String logLevel;

    /**
     * GWT JavaScript compiler output style (-style OBF[USCATED], PRETTY, or
     * DETAILED).
     * <p>
     * Can be set from command line using '-Dgwt.style=...'
     */
    @Parameter(defaultValue = "OBF", property = "gwt.style")
    private String style;

    /**
     * The directory into which deployable but not servable output files will be
     * written (defaults to 'WEB-INF/deploy' under the webappDirectory
     * directory/jar, and may be the same as the extra directory/jar)
     *
     * @since 2.3.0-1
     */
    @Parameter
    private File deploy;

    /**
     * Extra JVM arguments that are passed to the GWT-Maven generated scripts
     * (for compiler, shell, etc - typically use -Xmx2G here, or
     * -XstartOnFirstThread, etc).
     * <p>
     * Can be set from command line using '-Dgwt.extraJvmArgs=...', defaults to
     * setting max Heap size to be large enough for most GWT use cases.
     * </p>
     */
    @Parameter(property = "gwt.extraJvmArgs", defaultValue = "-Xmx1G")
    private String extraJvmArgs;

    /**
     * Option to specify the jvm (or path to the java executable) to use with
     * the forking scripts. For the default, the jvm will be the same as the one
     * used to run Maven.
     *
     * @since 1.1
     */
    @Parameter(property = "gwt.jvm")
    private String jvm;

    /**
     * Forked process execution timeOut. Usefull to avoid maven to hang in
     * continuous integration server.
     */
    @Parameter
    private int timeOut;

    /**
     *
     * Artifacts to be included as source-jars in GWTCompiler Classpath. Removes
     * the restriction that source code must be bundled inside of the final JAR
     * when dealing with external utility libraries not designed exclusivelly
     * for GWT. The plugin will download the source.jar if necessary.
     *
     * This option is a workaround to avoid packaging sources inside the same
     * JAR when splitting and application into modules. A smaller JAR can then
     * be used on server classpath and distributed without sources (that may not
     * be desirable).
     */
    @Parameter
    private String[] compileSourcesArtifacts;

    /**
     * Whether to use the persistent unit cache or not.
     * <p>
     * Can be set from command line using '-Dgwt.persistentunitcache=...'
     *
     * @since 2.5.0-rc1
     */
    @Parameter(defaultValue = "false", property = "gwt.persistentunitcache")
    private Boolean persistentunitcache;

    /**
     * The directory where the persistent unit cache will be created if enabled.
     * <p>
     * Can be set from command line using '-Dgwt.persistentunitcachedir=...'
     *
     * @since 2.5.0-rc1
     */
    @Parameter(property = "gwt.persistentunitcachedir")
    private File persistentunitcachedir;

    /**
     * The widgetset compilation mode (local / fetch from CDN / CDN only). The
     * allowed values are "local", "fetch" and "cdn".
     * <p>
     * Can be set from command line using '-Dvaadin.widgetset.mode=...'
     */
    @Parameter(defaultValue = "local", property = "vaadin.widgetset.mode")
    protected String widgetsetMode;

    // methods

    /**
     * {@inheritDoc}
     *
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        doExecute();
    }

    public abstract void doExecute() throws MojoExecutionException, MojoFailureException;

    protected String getExtraJvmArgs() {
        return extraJvmArgs;
    }

    protected String getLogLevel() {
        return logLevel;
    }

    protected String getStyle() {
        return style;
    }

    protected String getJvm() {
        return jvm;
    }

    /**
     * hook to post-process the dependency-based classpath
     */
    protected void postProcessClassPath(Collection<File> classpath) {
        // Nothing to do in most case
    }

    private List<String> getJvmArgs() {
        List<String> extra = new ArrayList<String>();
        String userExtraJvmArgs = getExtraJvmArgs();
        if (userExtraJvmArgs != null) {
            try {
                return new ArrayList<String>(Arrays.asList(CommandLineUtils.translateCommandline(
                        StringUtils.removeDuplicateWhitespace(userExtraJvmArgs))));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // Pass through any license info for the compiler
        Properties props = System.getProperties();
        for (Object o : props.keySet()) {
            String key = (String) o;
            if (key != null && key.endsWith(".developer.license")) {
                extra.add("-D" + key + "=" + System.getProperty(key));
            }
        }

        return extra;
    }

    /**
     * @param timeOut
     *            the timeOut to set
     */
    public void setTimeOut(int timeOut) {
        this.timeOut = timeOut;
    }

    protected JavaCommand createJavaCommand() {
        return new JavaCommand()
                .setLog(getLog())
                .setJvm(getJvm())
                .setJvmArgs(getJvmArgs())
                .setTimeOut(timeOut)
                .addClassPathProcessors(new ClassPathProcessor() {
                    @Override
                    public void postProcessClassPath(List<File> files) {
                        AbstractGwtShellMojo.this.postProcessClassPath(files);
                    }
                });
    }

    /**
     * Add sources.jar artifacts for project dependencies listed as
     * compileSourcesArtifacts. This is a GWT hack to avoid packaging java
     * source files into JAR when sharing code between server and client.
     * Typically, some domain model classes or business rules may be packaged as
     * a separate Maven module. With GWT packaging this requires to distribute
     * such classes with code, that may not be desirable.
     * <p>
     * The hack can also be used to include utility code from external
     * librariries that may not have been designed for GWT.
     */
    protected void addCompileSourceArtifacts(JavaCommand cmd) throws MojoExecutionException {
        if (compileSourcesArtifacts == null) {
            return;
        }
        for (String include : compileSourcesArtifacts) {
            List<String> parts = new ArrayList<String>();
            parts.addAll(Arrays.asList(include.split(":")));
            if (parts.size() == 2) {
                // type is optional as it will mostly be "jar"
                parts.add("jar");
            }
            String dependencyId = StringUtils.join(parts.iterator(), ":");
            boolean found = false;

            for (Artifact artifact : getProjectArtifacts()) {
                getLog().debug("compare " + dependencyId + " with " + artifact.getDependencyConflictId());
                if (artifact.getDependencyConflictId().equals(dependencyId)) {
                    getLog().debug("Add " + dependencyId + " sources.jar artifact to compile classpath");
                    Artifact sources = resolve(
                            artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), "jar", "sources");
                    cmd.addToClasspath(sources.getFile());
                    found = true;
                    break;
                }
            }
            if (!found) {
                getLog().warn("Declared compileSourcesArtifact was not found in project dependencies " + dependencyId);
            }
        }
    }

    protected void addArgumentDeploy(JavaCommand cmd) {
        if (deploy != null) {
            cmd.arg("-deploy").arg(String.valueOf(deploy));
        }
    }

    protected void addArgumentGen(JavaCommand cmd) {
        if (genParam) {
            if (!gen.exists()) {
                gen.mkdirs();
            }
            cmd.arg("-gen", gen.getAbsolutePath());
        }
    }

    protected void addPersistentUnitCache(JavaCommand cmd) {
        if (persistentunitcache != null) {
            cmd.systemProperty("gwt.persistentunitcache", String.valueOf(persistentunitcache.booleanValue()));
        }
        if (persistentunitcachedir != null) {
            cmd.systemProperty("gwt.persistentunitcachedir", persistentunitcachedir.getAbsolutePath());
        }
    }

    /**
     * Create a CDN widgetset compilation request.
     *
     * @return created request
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    protected WidgetSetRequest createWidgetsetRequest() throws MojoExecutionException, MojoFailureException {
        String vaadinVersion = null;

        Set<Artifact> artifacts = getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            // Store the vaadin version
            if (artifact.getArtifactId().equals("vaadin-server")) {
                vaadinVersion = artifact.getVersion();
            }
        }

        List<String> cp;
        try {
            cp = getProject().getCompileClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Failed to resolve compile classpath elements", e);
        }

        Map<String, URL> urls = new HashMap<String, URL>();
        for (Object object : cp) {
            String path = (String) object;
            try {
                urls.put(path, new File(path).toURI().toURL());
            } catch (MalformedURLException e) {
                throw new MojoExecutionException("Failed to parse URL of a classpath element", e);
            }
        }
        Map<String, URL> availableWidgetSets;
        availableWidgetSets = ClassPathExplorer.getAvailableWidgetSets(urls);
        Set<Artifact> uniqueArtifacts = new HashSet<Artifact>();

        for (String name : availableWidgetSets.keySet()) {
            URL url = availableWidgetSets.get(name);
            for (Artifact a : artifacts) {
                String u = url.toExternalForm();
                if (u.contains(a.getArtifactId()) && u.contains(a.getBaseVersion()) && !u.contains("vaadin-client")) {
                    uniqueArtifacts.add(a);
                }
            }
        }

        WidgetSetRequest wsReq = new WidgetSetRequest();
        for (Artifact a : uniqueArtifacts) {
            wsReq.addon(a.getGroupId(), a.getArtifactId(), a.getBaseVersion());
        }
        getLog().info((wsReq.getAddons() != null ? wsReq.getAddons().size() : 0) + " addons found.");

        wsReq.setCompileStyle(getStyle());
        wsReq.setVaadinVersion(vaadinVersion);
        return wsReq;
    }
}
