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

import java.io.File;
import java.net.URL;
import java.util.Collection;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.surefire.report.ReporterManager;
import org.codehaus.mojo.gwt.test.MavenTestRunner;
import org.codehaus.mojo.gwt.test.TestTemplate;
import org.codehaus.plexus.util.StringUtils;

/**
 * Mimic surefire to run GWTTestCases during integration-test phase, until SUREFIRE-508 is fixed
 *
 * @author <a href="mailto:nicolas@apache.org">Nicolas De Loof</a>
 * @see http://code.google.com/intl/fr/webtoolkit/doc/latest/DevGuideTesting.html
 * @version $Id: TestMojo.java 9466 2009-04-16 12:03:15Z ndeloof $
 */
@Mojo(
        name = "test",
        defaultPhase = LifecyclePhase.INTEGRATION_TEST,
        requiresDependencyResolution = ResolutionScope.TEST,
        threadSafe = true)
public class TestMojo extends AbstractGwtShellMojo {

    /**
     * Set this to 'true' to skip running tests, but still compile them. Its use is NOT RECOMMENDED,
     * but quite convenient on occasion.
     */
    @Parameter(property = "skipTests")
    private boolean skipTests;

    /**
     * DEPRECATED This old parameter is just like skipTests, but bound to the old property
     * maven.test.skip.exec. Use -DskipTests instead; it's shorter.
     */
    @Deprecated
    @Parameter(property = "maven.test.skip.exec")
    private boolean skipExec;

    /**
     * Set this to 'true' to bypass unit tests entirely. Its use is NOT RECOMMENDED, especially if
     * you enable it using the "maven.test.skip" property, because maven.test.skip disables both
     * running the tests and compiling the tests. Consider using the skipTests parameter instead.
     */
    @Parameter(property = "maven.test.skip")
    private boolean skip;

    /**
     * Set this to true to ignore a failure during testing. Its use is NOT RECOMMENDED, but quite
     * convenient on occasion.
     */
    @Parameter(property = "maven.test.failure.ignore")
    private boolean testFailureIgnore;

    /**
     * output directory for code generated by GWT for tests
     */
    @Parameter(defaultValue = "${project.build.directory}/www-test")
    private String out;

    /**
     * run tests using web mode rather than developer (a.k.a. hosted) mode
     *
     * @deprecated Use productionMode instead.
     */
    @Deprecated
    @Parameter(defaultValue = "false", property = "gwt.test.web")
    private boolean webMode;

    /**
     * run tests using production mode rather than development (a.k.a. hosted) mode.
     *
     * @see http://code.google.com/intl/fr-FR/webtoolkit/doc/latest/DevGuideCompilingAndDebugging.html#DevGuideProdMode
     */
    @Parameter(defaultValue = "false", property = "gwt.test.prod")
    private boolean productionMode;

    /**
     * Configure test mode. Can be set to "manual", "htmlunit" or "selenium". If set
     * to any other value, that value will be passed as the <code>-runStyle</code> argument,
     * allowing you to use an arbitrary RunStyle when running tests.
     */
    @Parameter(defaultValue = "htmlunit", property = "gwt.test.mode")
    private String mode;

    /**
     * Configure options to run tests with HTMLUnit. The value must descrivbe the browser emulation
     * to be used, FF17, IE8, IE9 or Chrome (possible multiple values separated by comas).
     *
     * @see http://code.google.com/intl/fr/webtoolkit/doc/latest/DevGuideTestingHtmlUnit.html
     */
    @Parameter(defaultValue = "FF17", property = "gwt.test.htmlunit")
    private String htmlunit;

    /**
     * Configure options to run tests with Selenium. The value must describe the Selenium Remote
     * Control target
     *
     * @see http://code.google.com/intl/fr/webtoolkit/doc/latest/DevGuideTestingRemoteTesting.html#Selenium
     */
    @Parameter(property = "gwt.test.selenium")
    private String selenium;

    /**
     * Time out (in seconds) for test execution in dedicated JVM
     */
    @Parameter(defaultValue = "60")
    @SuppressWarnings("unused")
    private int testTimeOut;

    /**
     * Comma separated list of ant-style inclusion patterns for GWT integration tests. For example,
     * can be set to <code>**\/*GwtTest.java</code> to match all test class following this naming
     * convention. Surefire plugin may then ne configured to exclude such tests.
     * <p>
     * It is recommended to use a TestSuite to run GwtTests, as they require some huge setup and are
     * very slow. Running inside a suite allow to execute the setup only once. The default value is
     * defined with this best practice in mind.
     */
    @Parameter(defaultValue = "**/GwtTest*.java,**/Gwt*Suite.java")
    protected String includes;

    /**
     * Comma separated list of ant-style exclusion patterns for GWT integration tests
     */
    @Parameter
    protected String excludes;

    /**
     * Directory for test reports, defaults to surefire one to match the surefire-report plugin
     */
    @Parameter(defaultValue = "${project.build.directory}/surefire-reports")
    private File reportsDirectory;

    /**
     * Specify the user agents to reduce the number of permutations in '-prod' mode;
     * e.g. ie8,safari,gecko1_8
     *
     * @since 2.5.0-rc1
     */
    @Parameter(property = "gwt.test.userAgents")
    private String userAgents;

    /**
     * Configure batch execution of tests.
     * <p>
     * Value must be one of 'none', 'class' or 'module'.
     * </p>
     *
     * @since 2.5.0-rc1
     */
    @Parameter(property = "gwt.test.batch")
    private String batch;

    /**
     * Causes the log window and browser windows to be displayed; useful for debugging.
     *
     * @since 2.6.0-rc1
     */
    @Parameter(defaultValue = "false", property = "gwt.test.showUi")
    private boolean showUi;

    /**
     * The compiler's working directory for internal use (must be writeable; defaults to a system temp dir)
     *
     * @since 2.6.0-rc1
     */
    @Parameter
    private File workDir;

    /**
     * Logs to a file in the given directory
     *
     * @since 2.6.0-rc1
     */
    @Parameter
    private File logDir;

    /**
     * Specifies Java source level.
     * <p>
     * The default value depends on the JVM used to launch Maven.
     *
     * @since 2.6.0-rc1
     */
    @Parameter(defaultValue = "auto", property = "gwt.compiler.source")
    private String sourceLevel;

    /**
     * Whether or not to enable assertions in generated scripts (-checkAssertions).
     *
     * @since 2.6.0-rc1
     */
    @Parameter(alias = "enableAssertions", defaultValue = "false")
    private boolean checkAssertions;

    /**
     * EXPERIMENTAL: Disables some java.lang.Class methods (e.g. getName()).
     * <p>
     * Can be set from command line using '-Dgwt.disableClassMetadata=true'.
     * </p>
     *
     * @since 2.6.0-rc1
     */
    @Parameter(defaultValue = "false", property = "gwt.disableClassMetadata")
    private boolean disableClassMetadata;

    /**
     * EXPERIMENTAL: Disables run-time checking of cast operations.
     * <p>
     * Can be set from command line using '-Dgwt.disableCastChecking=true'.
     * </p>
     *
     * @since 2.6.0-rc1
     */
    @Parameter(defaultValue = "false", property = "gwt.disableCastChecking")
    private boolean disableCastChecking;

    /**
     * EXPERIMENTAL: Disables code-splitting.
     * <p>
     * Can be set from command line using '-Dgwt.disableRunAsync=true'.
     * </p>
     *
     * @since 2.6.0-rc1
     */
    @Parameter(defaultValue = "false", property = "gwt.disableRunAsync")
    private boolean disableRunAsync;

    /**
     * Enable faster, but less-optimized, compilations.
     * <p>
     * Can be set from command line using '-Dgwt.draftCompile=true'.
     * </p>
     * <p>
     * This is equivalent to '-Dgwt.compiler.optimizationLevel=0 -Dgwt.compiler.disableAggressiveOptimization=true'.
     * </p>
     *
     * @since 2.6.0-rc1
     */
    @Parameter(defaultValue = "false", property = "gwt.draftCompiler")
    private boolean draftCompile;

    /**
     * EXPERIMENTAL: Cluster similar functions in the output to improve compression.
     *
     * @since 2.6.0-rc1
     */
    @Parameter(defaultValue = "true", property = "gwt.compiler.clusterFunctions")
    private boolean clusterFunctions;

    /**
     * EXPERIMENTAL: Inline literal parameters to shrink function declarations and
     * provide more deadcode elimination possibilities.
     *
     * @since 2.6.0-rc1
     */
    @Parameter(defaultValue = "true", property = "gwt.compiler.inlineLiteralParameters")
    private boolean inlineLiteralParameters;

    /**
     * EXPERIMENTAL: Analyze and optimize dataflow.
     *
     * since 2.6.0-rc1
     */
    @Parameter(defaultValue = "true", property = "gwt.compiler.optimizeDataflow")
    private boolean optimizeDataflow;

    /**
     * EXPERIMENTAL: Ordinalize enums to reduce some large strings.
     *
     * @since 2.6.0-rc1
     */
    @Parameter(defaultValue = "true", property = "gwt.compiler.ordinalizeEnums")
    private boolean ordinalizeEnums;

    /**
     * EXPERIMENTAL: Removing duplicate functions.
     * <p>
     * Will interfere with stacktrace deobfuscation and so is only honored when compiler.stackMode is set to strip.
     *
     * @since 2.6.0-rc1
     */
    @Parameter(defaultValue = "true", property = "gwt.compiler.removeDuplicateFunctions")
    private boolean removeDuplicateFunctions;

    /**
     * Sets the optimization level used by the compiler.  0=none 9=maximum.
     * <p>
     * -1 uses the default level of the compiler.
     * </p>
     * <p>
     * Can be set from command line using '-Dgwt.compiler.optimizationLevel=n'.
     * </p>
     *
     * @since 2.6.0-rc1
     */
    @Parameter(defaultValue = "-1", property = "gwt.compiler.optimizationLevel")
    private int optimizationLevel;

    /**
     * Set the test method timeout, in minutes
     *
     * @since 2.6.0-rc1
     */
    @Parameter(defaultValue = "5", property = "gwt.testMethodTimeout")
    private int testMethodTimeout;

    /**
     * Set the test begin timeout (time for clients to contact server), in minutes
     *
     * @since 2.6.0-rc1
     */
    @Parameter(defaultValue = "1", property = "gwt.testBeginTimeout")
    private int testBeginTimeout;

    /**
     * Precompile modules as tests are running (speeds up remote tests but requires more memory)
     * <p>
     * The value is one of <tt>simple</tt>, <tt>all</tt>, or <tt>parallel</tt>.
     *
     * @since 2.6.0-rc1
     */
    @Parameter(defaultValue = "simple", property = "gwt.test.precompile")
    private String precompile;

    /**
     * EXPERIMENTAL: Sets the maximum number of attempts for running each test method
     *
     * @since 2.6.0-rc1
     */
    @Parameter(defaultValue = "1", property = "gwt.test.tries")
    private int tries;

    /**
     * Puts most JavaScript globals into namespaces.
     * <p>
     * Value is one of PACKAGE or NONE.
     * <p>
     * Default: PACKAGE for -draftCompile, otherwise NONE
     *
     * @since 2.7.0-rc1
     */
    @Parameter
    private String namespace;

    /**
     * Compiles faster by reusing data from the previous compile.
     *
     * @since 2.7.0-rc1
     */
    @Parameter(alias = "compilePerFile", defaultValue = "false", property = "gwt.compiler.incremental")
    private boolean incremental;

    /**
     * EXPERIMENTAL: Specifies JsInterop mode, either NONE, JS, or CLOSURE.
     *
     * @since 2.7.0-rc1
     */
    @Parameter(defaultValue = "NONE")
    private String jsInteropMode;

    /** failures counter */
    private int failures;

    @Override
    public void doExecute() throws MojoExecutionException, MojoFailureException {
        if (skip || skipTests || skipExec) {
            return;
        }
        new TestTemplate(getProject(), includes, excludes, new TestTemplate.CallBack() {
            @Override
            public void doWithTest(File sourceDir, String test) throws MojoExecutionException {
                forkToRunTest(test);
            }
        });

        if (failures > 0) {
            if (testFailureIgnore) {
                getLog().error("There are test failures.\n\nPlease refer to " + reportsDirectory
                        + " for the individual test results.");
            } else {
                throw new MojoExecutionException("There was test failures.");
            }
        }
    }

    /**
     * @param classpath the test execution classpath
     * @param jvm the JVM process command
     * @param test the test to run
     * @throws MojoExecutionException some error occured
     */
    private void forkToRunTest(String test) throws MojoExecutionException {
        test = test.substring(0, test.length() - 5);
        test = StringUtils.replace(test, File.separator, ".");
        try {
            File outFile = new File(out);
            if (outFile.isAbsolute()) {
                outFile.mkdirs();
            } else {
                new File(getProject().getBasedir(), out).mkdirs();
            }
            try {
                JavaCommand cmd = createJavaCommand().setMainClass(MavenTestRunner.class.getName());
                if (gwtSdkFirstInClasspath) {
                    cmd.addToClasspath(getGwtUserJar()).addToClasspath(getGwtDevJar());
                }
                cmd.addToClasspath(getClasspath(Artifact.SCOPE_TEST));
                if (!gwtSdkFirstInClasspath) {
                    cmd.addToClasspath(getGwtUserJar()).addToClasspath(getGwtDevJar());
                }

                addCompileSourceArtifacts(cmd);

                cmd.arg(test);
                cmd.systemProperty("surefire.reports", reportsDirectory.getAbsolutePath());
                cmd.systemProperty("gwt.args", getGwtArgs());

                cmd.execute();
            } catch (ForkedProcessExecutionException e) {
                failures++;
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to run GWT tests", e);
        }
    }

    protected String getGwtArgs() {
        StringBuilder sb = new StringBuilder();
        sb.append("-war ").append(quote(out));
        sb.append(" -logLevel ").append(quote(getLogLevel()));
        sb.append((webMode || productionMode) ? " -nodevMode" : " -devMode");
        sb.append(checkAssertions ? " -checkAssertions" : " -nocheckAssertions");
        sb.append(clusterFunctions ? " -XclusterFunctions" : " -XnoclusterFunctions");
        sb.append(disableCastChecking ? " -XnocheckCasts" : " -XcheckCasts");
        sb.append(disableClassMetadata ? " -XnoclassMetadata" : " -XclassMetadata");
        sb.append(disableRunAsync ? " -XnocodeSplitting" : " -XcodeSplitting");
        sb.append(draftCompile ? " -draftCompile" : " -nodraftCompile");
        sb.append(inlineLiteralParameters ? " -XinlineLiteralParameters" : " -XnoinlineLiteralParameters");
        sb.append(optimizeDataflow ? " -XoptimizeDataflow" : " -XnooptimizeDataflow");
        sb.append(ordinalizeEnums ? " -XordinalizeEnums" : " -XnoordinalizeEnums");
        sb.append(removeDuplicateFunctions ? " -XremoveDuplicateFunctions" : " -XnoremoveDuplicateFunctions");
        sb.append(showUi ? " -showUi" : " -noshowUi");
        sb.append(" -sourceLevel ").append(quote(sourceLevel));
        sb.append(" -testBeginTimeout ").append(testBeginTimeout);
        sb.append(" -testMethodTimeout ").append(testMethodTimeout);
        sb.append(" -Xtries ").append(tries);
        sb.append(incremental ? " -incremental" : " -noincremental");

        if (optimizationLevel >= 0) {
            sb.append(" -optimize ").append(optimizationLevel);
        }
        if (precompile != null && !precompile.trim().isEmpty()) {
            sb.append(" -precompile ").append(quote(precompile));
        }
        if (logDir != null) {
            sb.append(" -logdir ").append(quote(logDir.getAbsolutePath()));
        }
        if (workDir != null) {
            sb.append(" -workDir ").append(quote(workDir.getAbsolutePath()));
        }

        if (namespace != null && !namespace.trim().isEmpty()) {
            sb.append(" -Xnamespace ").append(quote(namespace));
        }
        if (jsInteropMode != null && jsInteropMode.length() > 0 && !jsInteropMode.equals("NONE")) {
            sb.append(" -XjsInteropMode ").append(quote(jsInteropMode));
        }

        if (mode.equalsIgnoreCase("manual")) {
            sb.append(" -runStyle Manual:1");
        } else if (mode.equalsIgnoreCase("htmlunit")) {
            sb.append(" -runStyle ").append(quote("HtmlUnit:" + htmlunit));
        } else if (mode.equalsIgnoreCase("selenium")) {
            sb.append(" -runStyle ").append(quote("Selenium:" + selenium));
        } else if (!mode.trim().isEmpty()) {
            sb.append(" -runStyle ").append(quote(mode));
        }
        if (userAgents != null && !userAgents.trim().isEmpty()) {
            sb.append(" -userAgents ").append(quote(userAgents));
        }
        if (batch != null && !batch.trim().isEmpty()) {
            sb.append(" -batch ").append(quote(batch));
        }
        // TODO Is addArgumentDeploy(cmd) also needed to get readable test stacktraces with an alternative deploy dir?

        return sb.toString();
    }

    private String quote(String arg) {
        return StringUtils.quoteAndEscape(arg, '"', new char[] {'"', ' ', '\t', '\r', '\n'});
    }

    @Override
    protected void postProcessClassPath(Collection<File> classpath) {
        classpath.add(getClassPathElementFor(TestMojo.class));
        classpath.add(getClassPathElementFor(ReporterManager.class));
        try {
            for (File f : getGwtDevJar()) {
                classpath.add(f);
            }
        } catch (MojoExecutionException e) {
            getLog().error("Processing classpath for test mojo failed", e);
        }
    }

    /**
     * @param clazz class to check for classpath resolution
     * @return The classpath element this class was loaded from
     */
    private File getClassPathElementFor(Class<?> clazz) {
        String classFile = clazz.getName().replace('.', '/') + ".class";
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = getClass().getClassLoader();
        }
        URL url = cl.getResource(classFile);
        getLog().debug("getClassPathElementFor " + clazz.getName() + " file " + url.toString());
        String path = url.toString();

        if (path.startsWith("jar:")) {
            path = path.substring(4, path.indexOf("!"));
        } else {
            path = path.substring(0, path.length() - classFile.length());
        }
        if (path.startsWith("file:")) {
            path = path.substring(5);
            // windauze hack with maven 3 we get those !
            path = path.replace("%20", " ");
        }
        File file = new File(path);
        getLog().debug("getClassPathElementFor " + clazz.getName() + " file " + file.getPath());
        return file;
    }

    /**
     * @param testTimeOut the testTimeOut to set
     */
    public void setTestTimeOut(int testTimeOut) {
        setTimeOut(testTimeOut);
    }
}
