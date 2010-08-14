package com.theoryinpractise.youtrack;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Calendar;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Goal which touches a timestamp file.
 *
 * @goal update-version
 */
public class YoutrackMojo extends AbstractMojo {

    /**
     * The current build session instance. This is used for
     * toolchain manager API calls.
     *
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    private MavenSession session;


    /**
     * @parameter
     * @required
     */
    private String server;

    /**
     * @parameter
     * @required
     */
    private String project;

    /**
     * @parameter
     */
    private Integer iterationLength;

    public void execute() throws MojoExecutionException {

        try {
            final Server mavenServer = session.getSettings().getServer(server);
            final MavenProject currentProject = session.getCurrentProject();

            if (mavenServer == null) {
                throw new MojoExecutionException("No server entry for '" + server + "', check your settings.xml file.");
            }

            Xpp3Dom mavenServerConfiguration = (Xpp3Dom) mavenServer.getConfiguration();
            final String url = mavenServerConfiguration.getChild("url").getValue();

            final YoutrackClient client = new YoutrackClient(url, project, mavenServer.getUsername(), mavenServer.getPassword(), getLog());

            File releaseFile = new File("release.properties");
            if (releaseFile.exists()) {
                // During release, create new version, release old version, migrate tickets
                Properties properties = new Properties();
                properties.load(new FileReader(releaseFile));

                String relKey = "project.rel." + currentProject.getGroupId() + ":" + currentProject.getArtifactId();
                String devKey = "project.dev." + currentProject.getGroupId() + ":" + currentProject.getArtifactId();

                String relVersion = String.format("%s-%s", currentProject.getArtifactId(), properties.getProperty(relKey));
                String devVersion = String.format("%s-%s", currentProject.getArtifactId(), properties.getProperty(devKey).replace("-SNAPSHOT", ""));

                client.releaseVersion(relVersion);
                client.createVersion(devVersion, buildYoutrackVersionDescription(currentProject, devVersion), getNextReleaseDate());
                client.moveOpenIssues(relVersion, devVersion);

            } else {
                // Ad-hoc usage - just create the new/current version if it's not already created
                final String newVersion = String.format("%s-%s", currentProject.getArtifactId(),
                        currentProject.getVersion().replace("-SNAPSHOT", ""));

                client.createVersion(newVersion, buildYoutrackVersionDescription(currentProject), getNextReleaseDate());
            }

        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (InterruptedException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (ExecutionException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (TimeoutException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }


    }

    private String getNextReleaseDate() {
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, iterationLength != null ? iterationLength : 14);
        return String.valueOf(cal.getTime().getTime());
    }

    private String buildYoutrackVersionDescription(MavenProject project) {
        return buildYoutrackVersionDescription(project, project.getVersion());
    }

    private String buildYoutrackVersionDescription(MavenProject project, final String version) {
        return String.format(
                "Release %s of %s/%s",
                version.replace("-SNAPSHOT", ""),
                project.getGroupId(),
                project.getArtifactId());
    }

}
