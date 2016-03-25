package com.vaadin.integration.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
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

import com.vaadin.wscdn.client.AddonInfo;
import com.vaadin.wscdn.client.Connection;
import com.vaadin.wscdn.client.PublishState;
import com.vaadin.wscdn.client.WidgetSetRequest;
import com.vaadin.wscdn.client.WidgetSetResponse;

/**
 * Updates Vaadin widgetsets based on other widgetset packages on the classpath.
 * It is assumed that the project does not directly contain other GWT modules.
 * In part adapted from gwt-maven-plugin {@link CompileMojo}.
 */
@Mojo(name = "update-widgetset", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class UpdateWidgetsetMojo extends AbstractGwtShellMojo {
    private static final String WSCDN_WIDGETSET_CLASS_NAME = "WidgetSet";
    private static final String WSCDN_PACKAGE = "com.vaadin.wscdn";

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
     * Folder where generated widgetset WebListener class will be created (automatically added to sources).
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/wscdn", required = true)
    private File generatedSourceDirectory;

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

        if ("cdn".equals(widgetsetMode) || "fetch".equals(widgetsetMode)) {
            WidgetSetRequest wsReq = createWidgetsetRequest();

            getProject().addCompileSourceRoot(generatedSourceDirectory.getAbsolutePath());

            String packageName = WSCDN_PACKAGE;
            String className = WSCDN_WIDGETSET_CLASS_NAME;

            File outputDirectory = generatedSourceDirectory;
            File packageDirectory = new File(outputDirectory,
                    packageName.replace(".", "/"));
            packageDirectory.mkdirs();

            File outputFile = new File(packageDirectory, className + ".java");

            try {
                triggerCdnBuild(wsReq, outputFile, "fetch".equals(widgetsetMode));
            } catch (IOException e) {
                throw new MojoExecutionException("Could not create widgetset @WebListener class", e);
            }
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

    /**
     * Create an appropriate WebListener class and start the build of the
     * widgetset.
     * 
     * @param wsReq
     *            widgetset compilation request
     * @param outputFile
     *            file in which to write the WebListener widgetset class
     * @param fetch
     *            true to only trigger widgetset compilation for a later fetch,
     *            false to use the widgetset directly from the CDN
     * @throws IOException
     * @throws MojoExecutionException
     */
    protected void triggerCdnBuild(WidgetSetRequest wsReq, File outputFile, boolean fetch) throws IOException, MojoExecutionException {
        String wsName = null;
        String wsUrl = null;

        Connection conn = new Connection();
        WidgetSetResponse wsRes = conn.queryRemoteWidgetSet(wsReq, true);
        if (wsRes != null && (wsRes.getStatus() == PublishState.AVAILABLE // Compiled and published
                || wsRes.getStatus() == PublishState.COMPILED // Compiled succesfully, but not yet available
                || wsRes.getStatus() == PublishState.COMPILING)) // Currently compiling the widgetset)
        {
            wsName = wsRes.getWidgetSetName();
            wsUrl = fetch ? "local" : wsRes.getWidgetSetUrl();
        } else {
            throw new MojoExecutionException(
                    "Remote widgetset compilation failed: " + (wsRes != null ? wsRes.
                            getStatus() : " (no response)"));
        }

        PublishState status = fetch ? PublishState.AVAILABLE : wsRes.getStatus();

        createWebListenerClass(wsReq, outputFile, wsName, wsUrl, status);
    }

    private void createWebListenerClass(WidgetSetRequest wsReq, File outputFile, String wsName, String wsUrl, PublishState status)
	    throws IOException {
	String listener = IOUtil.toString(getClass().getResourceAsStream(
                "/weblistener.tmpl"));
        listener = listener.replace("__wsUrl", wsUrl);
        listener = listener.replace("__wsName", wsName);
	listener = listener.replace("__wsReady",
                status == PublishState.AVAILABLE ? "true" : "false");

        StringBuilder sb = new StringBuilder();
        if (wsReq.getAddons() != null) {
            for (AddonInfo a : wsReq.getAddons()) {
                String aid = a.getArtifactId();
                String gid = a.getGroupId();
                String v = a.getVersion();
                sb.append(" * ");
                sb.append(aid);
                sb.append(":");
                sb.append(gid);
                sb.append(":");
                sb.append(v);
                sb.append("\n");
            }
        }
        listener = listener.replace("__vaadin", " * " + wsReq.getVaadinVersion());
        listener = listener.replace("__style", " * " + wsReq.getCompileStyle());
        listener = listener.replace("__addons", sb.toString());

        FileUtils.writeStringToFile(outputFile, listener);

        // Print some info
        if (wsName != null && wsUrl != null) {
            getLog().info("Widgetset config created to " + outputFile.
                    getAbsolutePath() + ". Public URL: " + wsUrl);
        } else {
            getLog().info("Widget set created to " + outputFile.
                    getAbsolutePath() + ".");
        }
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
