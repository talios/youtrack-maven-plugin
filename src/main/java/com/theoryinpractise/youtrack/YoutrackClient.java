package com.theoryinpractise.youtrack;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Realm;
import com.ning.http.client.Response;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Aug 14, 2010
 * Time: 2:51:48 PM
 * To change this template use File | Settings | File Templates.
 */
public class YoutrackClient {

    private String url;
    private String project;
    private final Log log;
    private Realm realm;

    public YoutrackClient(String url, String project, String username, String password, Log log) {
        this.url = url;
        this.project = project;
        this.realm = new Realm.RealmBuilder()
                .setPrincipal(username)
                .setPassword(password)
                .setUsePreemptiveAuth(true)
                .setScheme(Realm.AuthScheme.BASIC)
                .build();
        this.log = log;
    }

    private AsyncHttpClient getHttpClient() {
        return new AsyncHttpClient();
    }

    private boolean doesVersionExist(final String version) {

        try {
            final String newVersionUrl = String.format("%s/rest/admin/project/%s/version/%s", url, project, version);
            Response response = getHttpClient().prepareGet(newVersionUrl)
                    .setRealm(realm)
                    .execute()
                    .get();

            return response.getStatusCode() == 200;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public void createVersion(final String newVersion, final String versionDescription, final String versionReleaseDate) throws IOException, ExecutionException, InterruptedException, MojoExecutionException, TimeoutException {

        // First check the new version doesn't exist
        if (!doesVersionExist(newVersion)) {

            final String newVersionUrl = String.format("%s/rest/admin/project/%s/version/%s", url, project, newVersion);

            log.info(String.format("Creating version %s on %s", newVersion, url));

            String newUrlWithParams = String.format("%s?description=%s&releaseDate=%s",
                    newVersionUrl, URLEncoder.encode(versionDescription, Charsets.UTF_8.name()), versionReleaseDate);

            // If not - create it
            getHttpClient().preparePut(newUrlWithParams)
                    .setRealm(realm)
                    .execute()
                    .get();

        } else {
            log.info(String.format("Version %s already exists on %s", newVersion, url));
        }

    }

    public void releaseVersion(final String version) throws IOException, ExecutionException, InterruptedException, MojoExecutionException, TimeoutException {

        // First check the version exists
        if (doesVersionExist(version)) {

            log.info(String.format("Releasing version %s on %s", version, YoutrackClient.this.url));

            final String versionUrl = String.format("%s/rest/admin/project/%s/version/%s", this.url, project, version);
            String newUrlWithParams = String.format("%s?isReleased=true&releaseDate=%s",
                    versionUrl, String.valueOf(new Date().getTime()));

            getHttpClient().preparePost(newUrlWithParams)
                    .setRealm(realm)
                    .execute()
                    .get();

        } else {
            throw new MojoExecutionException(String.format("Version %s doesn't exist on %s", version, YoutrackClient.this.url));
        }
    }

    public void moveOpenIssues(final String originalVersion, final String newVersion) throws IOException, ExecutionException, InterruptedException, MojoExecutionException {

        log.info(String.format("Moving open issues from version %s to %s on %s", originalVersion, newVersion, url));

        withIssues(String.format("fix for: %s state: Unresolved", originalVersion), new Function<Element, String>() {
            public String apply(Element element) {

                String id = element.getAttributeValue("id");

                try {
                    final String command = String.format("fixed in %s", newVersion);
                    final String comment = String.format("Unfinished issue moved issue from %s to %s", originalVersion, newVersion);
                    final String issueUrl = String.format("%s/rest/issue/%s/execute", url, id);

                    getHttpClient().preparePost(issueUrl)
                            .setRealm(realm)
                            .addParameter("command", command)
                            .addParameter("comment", comment)
                            .execute()
                            .get();

                    return String.format("Movied issue %s from version %s to %s", id, originalVersion, newVersion);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            }
        });

    }


    public void withIssues(String filter, final Function<Element, String> issueFunction) throws IOException, ExecutionException, InterruptedException, MojoExecutionException {

        String issueUrl = String.format("%s/rest/project/issues/%s?max=100&filter=%s", url, project, URLEncoder.encode(filter, "UTF-8"));

        log.debug("Loading issues from " + issueUrl);

        Response response = getHttpClient().prepareGet(issueUrl)
                .setRealm(realm)
                .execute()
                .get();

        // First check the version exists
        if (response.getStatusCode() == 200) {

            SAXBuilder builder = new SAXBuilder();
            try {
                Document doc = builder.build(response.getResponseBodyAsStream());

                List<Element> issues = doc.getRootElement().getChildren("issue");

                for (Element issue : issues) {
                    String logLine = issueFunction.apply(issue);
                    log.info(logLine);
                }

            } catch (JDOMException e) {
                throw new MojoExecutionException(e.getMessage());
            }

        }

    }

}
