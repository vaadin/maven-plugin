package org.codehaus.mojo.gwt;

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

import static org.apache.maven.artifact.Artifact.SCOPE_COMPILE;
import static org.apache.maven.artifact.Artifact.SCOPE_RUNTIME;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.metadata.ResolutionGroup;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.MavenMetadataSource;
import org.codehaus.plexus.util.StringUtils;

/**
 * Abstract Support class for all GWT-related operations.
 * <p>
 * Provide methods to build classpath for GWT SDK tools.
 *
 * @author <a href="mailto:nicolas@apache.org">Nicolas De Loof</a>
 * @version $Id$
 */
public abstract class AbstractGwtMojo extends AbstractMojo {
    private static final String GWT_USER = "com.google.gwt:gwt-user";

    private static final String GWT_DEV = "com.google.gwt:gwt-dev";

    /** GWT artifacts groupId */
    public static final String GWT_GROUP_ID = "com.google.gwt";
    public static final String VAADIN_GROUP_ID = "com.vaadin";

    // --- Some Maven tools ----------------------------------------------------

    @Parameter(defaultValue = "${plugin.artifactMap}", required = true, readonly = true)
    private Map<String, Artifact> pluginArtifactMap;

    @Component
    private MavenProjectBuilder projectBuilder;

    @Component
    protected ArtifactResolver resolver;

    @Component
    protected ArtifactFactory artifactFactory;

    @Component
    protected ClasspathBuilder classpathBuilder;

    // --- Some MavenSession related structures --------------------------------

    @Parameter(defaultValue = "${localRepository}", required = true, readonly = true)
    protected ArtifactRepository localRepository;

    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", required = true, readonly = true)
    protected List<ArtifactRepository> remoteRepositories;

    @Component
    protected ArtifactMetadataSource artifactMetadataSource;

    /**
     * The maven project descriptor
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    // --- Plugin parameters ---------------------------------------------------

    /**
     * Folder where generated-source will be created (automatically added to
     * compile classpath).
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/gwt", required = true)
    private File generateDirectory;

    /**
     * Location on filesystem where GWT will write output files (-out option to
     * GWTCompiler).
     */
    @Parameter(property = "gwt.war", defaultValue = "${project.build.outputDirectory}/VAADIN/widgetsets", alias = "outputDirectory")
    private File webappDirectory;

    /**
     * Prefix to prepend to module names inside {@code webappDirectory} or in
     * URLs in DevMode.
     * <p>
     * Could also be seen as a suffix to {@code webappDirectory}.
     */
    @Parameter(property = "gwt.modulePathPrefix")
    protected String modulePathPrefix;

    /**
     * Location of the web application static resources (same as
     * maven-war-plugin parameter)
     */
    @Parameter(defaultValue = "${basedir}/src/main/webapp")
    protected File warSourceDirectory;

    /**
     * Select the place where GWT application is built. In <code>inplace</code>
     * mode, the warSourceDirectory is used to match the same use case of the
     * {@link war:inplace
     * http://maven.apache.org/plugins/maven-war-plugin/inplace-mojo.html} goal.
     */
    @Parameter(defaultValue = "false", property = "gwt.inplace")
    private boolean inplace;

    /**
     * The forked command line will use gwt sdk jars first in classpath. see
     * issue http://code.google.com/p/google-web-toolkit/issues/detail?id=5290
     *
     * @since 2.1.0-1
     * @deprecated tweak your dependencies and/or split your project with a
     *             client-only module
     */
    @Deprecated
    @Parameter(defaultValue = "false", property = "gwt.gwtSdkFirstInClasspath")
    protected boolean gwtSdkFirstInClasspath;

    /**
     * List of requested artifacts for which there was no version information
     * available. This is used to prevent duplicate messages about the same
     * artifacts
     */
    private Set<String> artifactsWithoutVersion = new HashSet<String>();

    public File getOutputDirectory() {
        File out = inplace ? warSourceDirectory : webappDirectory;
        if (!StringUtils.isBlank(modulePathPrefix)) {
            out = new File(out, modulePathPrefix);
        }
        return out;
    }

    /**
     * Add classpath elements to a classpath URL set
     *
     * @param elements
     *            the initial URL set
     * @param urls
     *            the urls to add
     * @param startPosition
     *            the position to insert URLS
     * @return full classpath URL set
     * @throws MojoExecutionException
     *             some error occured
     */
    protected int addClasspathElements(Collection<?> elements, URL[] urls,
            int startPosition) throws MojoExecutionException {
        for (Object object : elements) {
            try {
                if (object instanceof Artifact) {
                    urls[startPosition] = ((Artifact) object).getFile().toURI()
                            .toURL();
                } else if (object instanceof Resource) {
                    urls[startPosition] = new File(
                            ((Resource) object).getDirectory()).toURI().toURL();
                } else {
                    urls[startPosition] = new File((String) object).toURI()
                            .toURL();
                }
            } catch (MalformedURLException e) {
                throw new MojoExecutionException(
                        "Failed to convert original classpath element " + object
                                + " to URL.",
                        e);
            }
            startPosition++;
        }
        return startPosition;
    }

    /**
     * Build the GWT classpath for the specified scope
     *
     * @param scope
     *            Artifact.SCOPE_COMPILE or Artifact.SCOPE_TEST
     * @return a collection of dependencies as Files for the specified scope.
     * @throws MojoExecutionException
     *             if classPath building failed
     */
    public Collection<File> getClasspath(String scope)
            throws MojoExecutionException {
        try {
            Collection<File> files = classpathBuilder.buildClasspathList(
                    getProject(), scope, getProjectArtifacts(), isGenerator());

            if (getLog().isDebugEnabled()) {
                getLog().debug("GWT SDK execution classpath :");
                for (File f : files) {
                    getLog().debug("   " + f.getAbsolutePath());
                }
            }
            return files;
        } catch (ClasspathBuilderException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /**
     * Whether to use processed resources and compiled classes ({@code false}),
     * or raw resources ({@code true }).
     */
    protected boolean isGenerator() {
        return false;
    }

    // FIXME move to GwtDevHelper stuff to avoid duplicates
    protected Collection<File> getGwtDevJar() throws MojoExecutionException {
        // TODO
        // checkGwtDevAsDependency();
        // checkGwtUserVersion();
        // return getArtifact( "com.google.gwt", "gwt-dev" ).getFile();

        // TODO use getJarFiles() if possible
        return getJarAndDependencies("vaadin-client-compiler");
        // return getJarFiles( GWT_DEV );
    }

    protected Collection<File> getGwtUserJar() throws MojoExecutionException {
        // TODO use getJarFiles() if possible
        return getJarAndDependencies("vaadin-client");
        // return getJarFiles( GWT_USER );
    }

    private Collection<File> getJarFiles(String artifactId)
            throws MojoExecutionException {
        // disabled for Vaadin: checkGwtUserVersion();
        Artifact rootArtifact = pluginArtifactMap.get(artifactId);
        ArtifactResolutionResult result;
        try {
            // Code shamelessly copied from exec-maven-plugin.
            MavenProject rootProject = projectBuilder.buildFromRepository(
                    rootArtifact, remoteRepositories, localRepository);
            List<Dependency> dependencies = rootProject.getDependencies();
            Set<Artifact> dependencyArtifacts = MavenMetadataSource
                    .createArtifacts(artifactFactory, dependencies, null, null,
                            null);
            dependencyArtifacts.add(rootProject.getArtifact());
            result = resolver.resolveTransitively(dependencyArtifacts,
                    rootArtifact, Collections.EMPTY_MAP, localRepository,
                    remoteRepositories, artifactMetadataSource, null,
                    Collections.EMPTY_LIST);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to resolve artifact", e);
        }
        @SuppressWarnings("unchecked")
        Collection<Artifact> resolved = result.getArtifacts();

        Collection<File> files = new ArrayList<File>(resolved.size() + 1);
        files.add(rootArtifact.getFile());
        for (Artifact artifact : resolved) {
            files.add(artifact.getFile());
        }

        return files;
    }

    // TODO replace with getJarFiles() if possible
    protected Collection<File> getJarAndDependencies(String artifactId)
            throws MojoExecutionException {

        Artifact rootArtifact = getArtifact(VAADIN_GROUP_ID, artifactId, null);

        ArtifactResolutionResult result = null;

        try {
            Set<Artifact> dependencyArtifacts = new HashSet<Artifact>();

            // TODO can these branches be unified/cleaned up?
            if (rootArtifact == null) {
                // only log implicit version checks once per artifact
                boolean logVersion = artifactsWithoutVersion.add(artifactId);

                if (logVersion) {
                    getLog().debug("Trying to resolve the version of "
                            + VAADIN_GROUP_ID + ":" + artifactId
                            + " based on the version of vaadin-shared in the project POM");
                }

                // assume that artifact is not in project - try to resolve with
                // version number from vaadin-shared
                Artifact vaadinSharedArtifact = getArtifact(VAADIN_GROUP_ID,
                        "vaadin-shared", null);
                if (vaadinSharedArtifact == null) {
                    // No vaadin-shared found, this is possibly when running
                    // clean and artifacts have not been resolved
                    // https://maven.apache.org/ref/3.2.3/apidocs/org/apache/maven/project/MavenProject.html#getArtifacts()
                    return Collections.emptyList();
                }

                rootArtifact = artifactFactory.createArtifact(VAADIN_GROUP_ID,
                        artifactId, vaadinSharedArtifact.getBaseVersion(),
                        "provided", "jar");
                resolver.resolveAlways(rootArtifact, remoteRepositories,
                        localRepository);

                if (logVersion) {
                    getLog().info("Using " + rootArtifact.getGroupId() + ":"
                            + rootArtifact.getArtifactId() + " version "
                            + rootArtifact.getVersion());
                }

                // metadata (POM) for rootArtifact not in memory in this case =>
                // need this to resolve transitive dependencies!
                ResolutionGroup resolutionGroup = artifactMetadataSource
                        .retrieve(rootArtifact, localRepository,
                                remoteRepositories);
                dependencyArtifacts.addAll(resolutionGroup.getArtifacts());
            } else {
                // Code shamelessly copied from exec-maven-plugin.
                MavenProject rootProject = projectBuilder.buildFromRepository(
                        rootArtifact, remoteRepositories, localRepository);
                List<Dependency> dependencies = rootProject.getDependencies();
                dependencyArtifacts = MavenMetadataSource.createArtifacts(
                        artifactFactory, dependencies, null, null, null);
                dependencyArtifacts.add(rootProject.getArtifact());
            }

            result = resolver.resolveTransitively(dependencyArtifacts,
                    rootArtifact, Collections.EMPTY_MAP, localRepository,
                    remoteRepositories, artifactMetadataSource, null,
                    Collections.EMPTY_LIST);

            // result = resolver
            // .resolveTransitively(dependencyArtifacts,
            // rootArtifact,
            // remoteRepositories, localRepository,
            // artifactMetadataSource);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(
                    "Failed to resolve artifact " + artifactId, e);
        } catch (ArtifactNotFoundException e) {
            throw new MojoExecutionException(
                    "Failed to resolve artifact " + artifactId, e);
        } catch (ArtifactMetadataRetrievalException e) {
            throw new MojoExecutionException(
                    "Failed to retrieve metadata for artifact " + artifactId,
                    e);
        } catch (ProjectBuildingException e) {
            throw new MojoExecutionException(
                    "Failed to resolve artifact " + artifactId, e);
        }

        @SuppressWarnings("unchecked")
        Collection<Artifact> resolved = result.getArtifacts();

        Collection<File> files = new ArrayList<File>(resolved.size() + 1);
        files.add(rootArtifact.getFile());
        for (Artifact artifact : resolved) {
            files.add(artifact.getFile());
        }

        return files;
    }

    protected Artifact getArtifact(String groupId, String artifactId,
            String classifier) {
        for (Artifact artifact : getProjectArtifacts()) {
            if (groupId.equals(artifact.getGroupId())
                    && artifactId.equals(artifact.getArtifactId())) {
                if (classifier != null
                        && classifier.equals(artifact.getClassifier())) {
                    return artifact;
                }
                if (classifier == null && artifact.getClassifier() == null) {
                    return artifact;
                }
            }
        }
        return null;
    }

    /**
     * Check gwt-user dependency matches plugin version
     */
    private void checkGwtUserVersion() throws MojoExecutionException {
        InputStream inputStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(
                        "org/codehaus/mojo/gwt/mojoGwtVersion.properties");
        Properties properties = new Properties();
        try {
            properties.load(inputStream);

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to load plugin properties",
                    e);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }

        Artifact gwtUser = project.getArtifactMap().get(GWT_USER);
        if (gwtUser != null) {
            String mojoGwtVersion = properties.getProperty("gwt.version");
            // ComparableVersion with an up2date maven version
            ArtifactVersion mojoGwtArtifactVersion = new DefaultArtifactVersion(
                    mojoGwtVersion);
            ArtifactVersion userGwtArtifactVersion = new DefaultArtifactVersion(
                    gwtUser.getVersion());
            if (userGwtArtifactVersion.compareTo(mojoGwtArtifactVersion) < 0) {
                getLog().warn("Your project declares dependency on gwt-user "
                        + gwtUser.getVersion()
                        + ". This plugin is designed for at least gwt version "
                        + mojoGwtVersion);
            }
        }
    }

    protected Artifact resolve(String groupId, String artifactId,
            String version, String type, String classifier)
            throws MojoExecutionException {
        // return project.getArtifactMap().get( groupId + ":" + artifactId );

        Artifact artifact = artifactFactory.createArtifactWithClassifier(
                groupId, artifactId, version, type, classifier);
        try {
            resolver.resolve(artifact, remoteRepositories, localRepository);
        } catch (ArtifactNotFoundException e) {
            throw new MojoExecutionException(
                    "artifact not found - " + e.getMessage(), e);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(
                    "artifact resolver problem - " + e.getMessage(), e);
        }
        return artifact;
    }

    /**
     * @param path
     *            file to add to the project compile directories
     */
    protected void addCompileSourceRoot(File path) {
        getProject().addCompileSourceRoot(path.getAbsolutePath());
    }

    /**
     * @return the project
     */
    public MavenProject getProject() {
        return project;
    }

    public ArtifactRepository getLocalRepository() {
        return localRepository;
    }

    public List<ArtifactRepository> getRemoteRepositories() {
        return remoteRepositories;
    }

    protected File setupGenerateDirectory() {
        if (!generateDirectory.exists()) {
            getLog().debug("Creating target directory "
                    + generateDirectory.getAbsolutePath());
            generateDirectory.mkdirs();
        }
        getLog().debug("Add compile source root "
                + generateDirectory.getAbsolutePath());
        addCompileSourceRoot(generateDirectory);
        return generateDirectory;
    }

    public File getGenerateDirectory() {
        if (!generateDirectory.exists()) {
            getLog().debug("Creating target directory "
                    + generateDirectory.getAbsolutePath());
            generateDirectory.mkdirs();
        }
        return generateDirectory;
    }

    public Set<Artifact> getProjectArtifacts() {
        return project.getArtifacts();
    }

    public Set<Artifact> getProjectRuntimeArtifacts() {
        Set<Artifact> artifacts = new HashSet<Artifact>();
        for (Artifact projectArtifact : project.getArtifacts()) {
            String scope = projectArtifact.getScope();
            if (SCOPE_RUNTIME.equals(scope) || SCOPE_COMPILE.equals(scope)) {
                artifacts.add(projectArtifact);
            }

        }
        return artifacts;
    }

}
