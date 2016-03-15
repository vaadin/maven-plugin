package com.vaadin.integration.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.mojo.gwt.shell.AbstractGwtShellMojo;
import org.codehaus.mojo.gwt.shell.CompileMojo;
import org.codehaus.mojo.gwt.shell.JavaCommand;
import org.codehaus.mojo.gwt.shell.JavaCommandException;
import org.codehaus.plexus.util.IOUtil;

/**
 * Updates Vaadin widgetsets based on other widgetset packages on the classpath.
 * It is assumed that the project does not directly contain other GWT modules.
 * In part adapted from gwt-maven-plugin {@link CompileMojo}.
 */
@Mojo(name = "update-widgetset", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class UpdateWidgetsetMojo extends AbstractGwtShellMojo {
    public static final String WIDGETSET_BUILDER_CLASS = "com.vaadin.server.widgetsetutils.WidgetSetBuilder";

    public static final String GWT_MODULE_EXTENSION = ".gwt.xml";

    private static final String APP_WIDGETSET_MODULE = "AppWidgetset";
    private static final String APP_WIDGETSET_FILE = APP_WIDGETSET_MODULE + GWT_MODULE_EXTENSION;

    /**
     * Folder where generated AppWidgetset will be created (automatically added to resources).
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-resources/gwt", required = true)
    private File generatedWidgetsetDirectory;

    /**
     * {@inheritDoc}
     *
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public final void doExecute() throws MojoExecutionException,
    MojoFailureException {
        if ("pom".equals(getProject().getPackaging())) {
            getLog().info("GWT compilation is skipped");
            return;
        }

        if ("cdn".equals(cdnMode)) {
            serveFromCDN();
        } else if ("fetch".equals(cdnMode)) {
            fetchWidgetset();
        } else {
            updateLocalWidgetset();
        }
    }

    private final void updateLocalWidgetset() throws MojoExecutionException,
        MojoFailureException {

        File appwsFile = new File(generatedWidgetsetDirectory, APP_WIDGETSET_FILE);

        // compile one widgetset at a time
        String[] modules = getModules();
        if (modules.length == 1 && APP_WIDGETSET_MODULE.equals(modules[0]) && appwsFile.exists()) {
            // this branch is needed to avoid a second call to update-widgetset in the package phase
            // from creating an extra AppWidgetset file in the source directory
            updateWidgetset(modules[0], true);
        } else if (modules.length > 0) {
            for (String module : modules) {
                updateWidgetset(module, false);
            }
        } else {
            setupGeneratedWidgetsetDirectory();

            // auto-generate a widgetset and update it
            getLog().info("No widgetsets found - generating AppWidgetset if necessary.");
            try {
                String template = IOUtil.toString(getClass().getResourceAsStream("/AppWidgetset.tmpl"));
                if (!appwsFile.exists()) {
                    OutputStream out = new FileOutputStream(appwsFile);
                    IOUtil.copy(template, out);
                }
                updateWidgetset(APP_WIDGETSET_MODULE, true);

                // if there was nothing relevant on the widgetset path, remove the generated widgetset
                // TODO this is an ugly workaround as the corresponding class in Vaadin framework does
                // not have sufficient API to only generate the widgetset when it is needed
                String generatedws = IOUtil.toString(new FileInputStream(appwsFile));
                if (template.replaceAll("\\s", "").equals(generatedws.replaceAll("\\s", ""))) {
                    appwsFile.delete();
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to create AppWidgetset", e);
            }

        }

    }

    private void serveFromCDN() {
        getLog().error("CDN not yet supported");
        // TODO generate WebListener class
    }

    private void fetchWidgetset() {
        getLog().error("Fetching widgetsets from CDN not yet supported");
        // TODO generate WebListener class
    }

    private void updateWidgetset(String module, boolean generated) throws MojoExecutionException {
        // class path order has "compile" sources first as it should

        getLog().info("Updating widgetset " + module);

        JavaCommand cmd = new JavaCommand();
        cmd.setMainClass(WIDGETSET_BUILDER_CLASS);
        cmd.setLog(getLog());

        // if using an auto-generated AppWidgetset, the generated source directory must be first on the classpath
        if (generated) {
            cmd.addToClasspath(generatedWidgetsetDirectory);
        }

        // make sure source paths are first on the classpath to update the .gwt.xml there, not in target
        Collection<String> sourcePaths = getProject().getCompileSourceRoots();
        if (null != sourcePaths) {
            for (String sourcePath : sourcePaths) {
                File sourceDirectory = new File(sourcePath);
                if ( sourceDirectory.exists() ) {
                    cmd.addToClasspath(sourceDirectory);
                }
            }
        }

        // also add resource paths early on the classpath to update the .gwt.xml there, not in target
        Collection<?> resources = getProject().getResources();
        if (null != resources) {
            for (Object resObj : resources) {
                Resource res = (Resource) resObj;
                File resourceDirectory = new File(res.getDirectory());
                if (resourceDirectory.exists()) {
                    getLog().info(
                            "Adding resource directory to command classpath: "
                                    + resourceDirectory);
                    cmd.addToClasspath(resourceDirectory);
                } else {
                    getLog().warn(
                            "Ignoring missing resource directory: "
                                    + resourceDirectory);
                }
            }
        }

        cmd.addToClasspath(getClasspath( Artifact.SCOPE_COMPILE ));
        
        cmd.addToClasspath(getGwtUserJar()).addToClasspath(getGwtDevJar());

        cmd.arg(module);
        try {
            cmd.execute();
        } catch (JavaCommandException e) {
            throw new MojoExecutionException("Failed to update widgetset", e);
        }
    }

    protected File setupGeneratedWidgetsetDirectory() {
        if ( !generatedWidgetsetDirectory.exists() )
        {
            getLog().debug( "Creating target directory " + generatedWidgetsetDirectory.getAbsolutePath() );
            generatedWidgetsetDirectory.mkdirs();
        }

        getLog().debug( "Add resource directory " + generatedWidgetsetDirectory.getAbsolutePath() );
        Resource resource = new Resource();
        resource.setDirectory(generatedWidgetsetDirectory.getAbsolutePath());
        getProject().addResource(resource);

        return generatedWidgetsetDirectory;
    }

}
