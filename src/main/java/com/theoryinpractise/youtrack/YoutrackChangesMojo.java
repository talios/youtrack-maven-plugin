package com.theoryinpractise.youtrack;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Goal which touches a timestamp file.
 *
 * @goal merge-changes
 */
public class YoutrackChangesMojo extends AbstractYoutrackMojo {

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


                mergeMavenChanges(client, relVersion);

            } else {
                // Ad-hoc usage - just create the new/current version if it's not already created
                final String newVersion = String.format("%s-%s", currentProject.getArtifactId(),
                        currentProject.getVersion().replace("-SNAPSHOT", ""));

//                client.createVersion(newVersion, buildYoutrackVersionDescription(currentProject), getNextReleaseDate());
                mergeMavenChanges(client, newVersion);
            }

        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (InterruptedException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (ExecutionException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (TimeoutException e) {
            throw new MojoExecutionException(e.getMessage());
        }


    }

    private void mergeMavenChanges(YoutrackClient client, String relVersion) throws IOException, ExecutionException, InterruptedException, MojoExecutionException {

        File changesXmlFile = new File("src/changes/changes.xml");
        Document changesDoc;
        Element bodyElement;

        if (changesXmlFile.exists()) {
            SAXBuilder builder = new SAXBuilder();
            try {
                changesDoc = builder.build(new FileReader(changesXmlFile));
            } catch (JDOMException e) {
                throw new MojoExecutionException("Unable to read changes.xml: " + e.getMessage(), e);
            }

            bodyElement = changesDoc.getRootElement().getChild("body");

        } else {
            changesXmlFile.getParentFile().mkdirs();
            changesXmlFile.createNewFile();
            Element documentElement = new Element("document");

            bodyElement = new Element("body");
            documentElement.addContent(bodyElement);

            changesDoc = new Document(documentElement);
        }

        final Element releaseElement = findOrCreateReleaseElement(bodyElement, relVersion);
        releaseElement.removeChildren("action");

        if (!bodyElement.getChildren().contains(releaseElement)) {
            bodyElement.addContent(releaseElement);
        }

        client.withIssues(String.format("fix for: %s state: Resolved", relVersion), new Function<Element, String>() {
            public String apply(Element element) {

                String id = element.getAttributeValue("id");
                String summary = element.getAttributeValue("summary");
                String type = new TypeMap().get(element.getAttributeValue("type"), "fix");

                Element actionElement = new Element("action");
                actionElement.setAttribute("type", type);
                actionElement.addContent(String.format("%s %s", id, summary));

                releaseElement.addContent(actionElement);

                return String.format("Added %s to changes.xml", id);

            }
        });

        XMLOutputter xml = new XMLOutputter();
        xml.setFormat(Format.getPrettyFormat());
        xml.output(changesDoc, new FileWriter(changesXmlFile));

    }

    private Element findOrCreateReleaseElement(Element bodyElement, String relVersion) {
        List<Element> releases = bodyElement.getChildren("release");
        for (Element element : releases) {
            if (relVersion.equals(element.getAttributeValue("version"))) {
                return element;
            }
        }

        Element releaseElement = new Element("release");
        releaseElement.setAttribute("version", relVersion);
        return releaseElement;

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

    private class TypeMap extends HashMap<String, String> {

        private TypeMap() {
            put("Improvement", "update");
            put("Feature", "add");
            put("Bug", "fix");
        }

        public String get(String key, String defaultValue) {
            return containsKey(key) ? get(key) : defaultValue;
        }

    }

}
