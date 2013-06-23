package com.github.patching.maven.warsync;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

/**
 * generate profile for eclipse fileSync plugin
 * 
 * @goal eclipse
 * @phase generate-resources
 * @requiresDependencyResolution runtime
 */
public class WarSyncMojo extends AbstractMojo {

    public static final String        FILE_SYNC_CONFIG_FILE = ".settings/de.loskutov.FileSync.prefs";
    private static final List<String> SCOPES                = new ArrayList<String>();

    static {
        SCOPES.add(Artifact.SCOPE_COMPILE);
        SCOPES.add(Artifact.SCOPE_RUNTIME);
    }

    /**
     * skip the goal
     * 
     * @parameter default-value="false" expression="${warsync.skip}"
     */
    private boolean                   skip;

    /**
     * COPY or REF
     * 
     * @parameter default-value="COPY" expression="${warsync.libMode}"
     */
    private LibMode                   libMode;

    /**
     * project
     * 
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject              project;

    /**
     * projectReferences
     * 
     * @parameter default-value="${project.projectReferences}"
     * @required
     * @readonly
     */
    private Map<String, MavenProject> projectReferences;

    /**
     * artifacts
     * 
     * @parameter default-value="${project.artifacts}"
     * @required
     * @readonly
     */
    private Set<Artifact>             artifacts;

    /**
     * target war directory
     * 
     * @parameter default-value="${project.build.directory}/warsync/${project.build.finalName}.war"
     * expression="${warsync.dir}"
     */
    private File                      warDir;

    /**
     * Single directory for extra files to include in the WAR. This is where you place your JSP files.
     * 
     * @parameter default-value="${basedir}/src/main/webapp"
     * @required
     */
    private File                      warSrcDir;

    /**
     * @parameter
     */
    private Map<String, String>       projectFilters        = new HashMap<String, String>();

    public void execute() throws MojoExecutionException {
        if (skip) {
            return;
        }
        if (!"war".equals(project.getPackaging())) {
            throw new MojoExecutionException("maven-warsync-plugin only accept project with war packaging");
        }
        try {
            if (libMode == LibMode.COPY) {
                copyLibJars(getClearLibDir());
            } else {
                createClasspathJarFile(getClearLibDir());
            }
            createFileSyncConfigs();
        } catch (Exception e) {
            throw new MojoExecutionException("error execute maven-warsync-plugin:eclipse", e);
        }
    }

    private void copyLibJars(File lib) throws Exception {
        if (artifacts != null && !artifacts.isEmpty()) {
            for (Artifact artifact : artifacts) {
                if (isLib(artifact)) {
                    FileChannel in = null;
                    FileChannel out = null;
                    try {
                        File srcFile = artifact.getFile();
                        File destFile = new File(lib, srcFile.getName());
                        if (destFile.exists()) {
                            destFile = new File(lib, artifact.getGroupId() + "-" + srcFile.getName());
                        }
                        destFile.createNewFile();
                        in = new FileInputStream(srcFile).getChannel();
                        out = new FileOutputStream(destFile).getChannel();
                        in.transferTo(0, in.size(), out);
                    } finally {
                        if (in != null) {
                            in.close();
                        }
                        if (out != null) {
                            out.close();
                        }
                    }
                }
            }
        }
    }

    private void createFileSyncConfigs() throws Exception {
        createFileSyncConfig(project);
        for (MavenProject prj : projectReferences.values()) {
            createFileSyncConfig(prj);
        }
    }

    private void createFileSyncConfig(MavenProject prj) throws Exception {
        URI BaseURI = prj.getBasedir().toURI();
        File configFile = new File(prj.getBasedir(), FILE_SYNC_CONFIG_FILE);
        configFile.getParentFile().mkdirs();
        String filterFile = projectFilters.get(getProjectReferenceId(prj.getArtifact()));
        if (filterFile == null && !prj.getFilters().isEmpty()) {
            filterFile = (String) prj.getFilters().get(0);
            if (filterFile != null) {
                filterFile = BaseURI.relativize(new File(filterFile).toURI()).getPath();
            }
        }
        PrintWriter writer = null;
        try {
            int index = 0;
            writer = new PrintWriter(configFile);
            writer.println("defaultDestination=" + FileSyncMap.formatPath(warDir.getAbsolutePath()));
            writer.println("eclipse.preferences.version=1");
            writer.println("includeTeamPrivateFiles=false");
            writer.println("useCurrentDateForDestinationFiles=false");
            if (prj == project) {
                FileSyncMap webRootMap = new FileSyncMap(index++);
                webRootMap.setSourceFolder(BaseURI.relativize(warSrcDir.toURI()).getPath());
                webRootMap.setExcludePatterns("WEB-INF/");
                writer.println(webRootMap.render());

                FileSyncMap webInfoMap = new FileSyncMap(index++);
                webInfoMap.setSourceFolder(BaseURI.relativize(new File(warSrcDir, "WEB-INF").toURI()).getPath());
                webInfoMap.setDestFolder(new File(warDir, "WEB-INF").getAbsolutePath());
                webInfoMap.setFilterFile(filterFile);
                writer.println(webInfoMap.render());
            }

            FileSyncMap classMap = new FileSyncMap(index++);
            classMap.setSourceFolder(BaseURI.relativize(new File(prj.getBuild().getOutputDirectory()).toURI()).getPath());
            classMap.setDestFolder(new File(warDir, "WEB-INF/classes").getAbsolutePath());
            classMap.setFilterFile(filterFile);
            writer.println(classMap.render());
        } finally {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
        }
    }

    private void createClasspathJarFile(File lib) throws Exception {
        List<String> jarFiles = new ArrayList<String>();
        if (artifacts != null && !artifacts.isEmpty()) {
            for (Artifact artifact : artifacts) {
                if (isLib(artifact)) {
                    jarFiles.add(artifact.getFile().toURI().getPath());
                }
            }
        }
        JarArchiver jar = new JarArchiver();
        jar.setDestFile(new File(lib, "warsync-classpath.jar"));
        Manifest manifest = Manifest.getDefaultManifest();
        manifest.addConfiguredAttribute(new Manifest.Attribute("Created-By", "maven-warsync-plugin"));
        manifest.addConfiguredAttribute(new Manifest.Attribute("Built-By", System.getProperty("user.name")));
        manifest.addConfiguredAttribute(new Manifest.Attribute("Build-Jdk", System.getProperty("java.version")));
        if (!jarFiles.isEmpty()) {
            manifest.addConfiguredAttribute(new Manifest.Attribute("Class-Path", StringUtils.join(jarFiles.iterator(), " ")));
        }
        jar.addConfiguredManifest(manifest);
        jar.createArchive();
    }

    private File getClearLibDir() throws Exception {
        File lib = new File(warDir, "WEB-INF/lib/");
        lib.mkdirs();
        FileUtils.cleanDirectory(lib);
        return lib;
    }

    private String getProjectReferenceId(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getBaseVersion();
    }

    private boolean isLib(Artifact artifact) {
        return artifact.isResolved() && SCOPES.contains(artifact.getScope()) && "jar".equals(artifact.getType())
               && projectReferences.get(getProjectReferenceId(artifact)) == null;
    }
}
