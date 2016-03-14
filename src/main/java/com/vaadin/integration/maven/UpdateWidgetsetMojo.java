package com.vaadin.integration.maven;

import java.io.File;
import java.util.Collection;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.mojo.gwt.shell.AbstractGwtShellMojo;
import org.codehaus.mojo.gwt.shell.CompileMojo;
import org.codehaus.mojo.gwt.shell.JavaCommand;
import org.codehaus.mojo.gwt.shell.JavaCommandException;

/**
 * Updates Vaadin widgetsets based on other widgetset packages on the classpath.
 * It is assumed that the project does not directly contain other GWT modules.
 * In part adapted from gwt-maven-plugin {@link CompileMojo}.
 */
@Mojo(name = "update-widgetset", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class UpdateWidgetsetMojo extends AbstractGwtShellMojo {
    public static final String WIDGETSET_BUILDER_CLASS = "com.vaadin.server.widgetsetutils.WidgetSetBuilder";

    public static final String GWT_MODULE_EXTENSION = ".gwt.xml";
    
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

        // compile one widgetset at a time
        String[] modules = getModules();
        if (modules.length > 0) {
            for (String module : modules) {
                updateWidgetset(module);
            }
        } else {
            // ask the user to explicitly indicate the widgetset to create
            getLog().info("No widgetsets to update.");
            getLog().info(
                    "To create a widgetset, define a non-existing module in your pom.xml .");
        }

    }

    private void updateWidgetset(String module) throws MojoExecutionException {
        // class path order has "compile" sources first as it should

        getLog().info("Updating widgetset " + module);

        JavaCommand cmd = new JavaCommand();
        cmd.setMainClass(WIDGETSET_BUILDER_CLASS);
        cmd.setLog(getLog());

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

}
