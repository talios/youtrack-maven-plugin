package com.theoryinpractise.youtrack;

import com.google.common.base.Function;
import com.ning.http.client.AsyncCompletionHandlerBase;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Response;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import sun.misc.BASE64Encoder;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Aug 14, 2010
 * Time: 2:51:48 PM
 * To change this template use File | Settings | File Templates.
 */
public class YoutrackClient {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BASIC = "Basic";

    private String url;
    private String project;
    private String username;
    private String authorization;
    private final Log log;

    public YoutrackClient(String url, String project, String username, String password, Log log) {
        this.url = url;
        this.project = project;
        this.username = username;
        this.authorization = new BASE64Encoder().encode((username + ":" + password).getBytes());
        this.log = log;

    }

    private AsyncHttpClient getHttpClient() {
        return new AsyncHttpClient(new AsyncHttpClientConfig.Builder()
                .setKeepAlive(false)
                .build());
    }

    public void createVersion(final String newVersion, final String versionDescription, final String versionReleaseDate) throws IOException, ExecutionException, InterruptedException, MojoExecutionException, TimeoutException {

        final String newVersionUrl = String.format("%s/rest/admin/project/%s/version/%s",
                url, project, newVersion);


        Future<Response> newVersionResponse = getHttpClient().prepareGet(newVersionUrl)
                .addHeader(AUTHORIZATION, BASIC + " " + authorization)
                .execute(
                        new AsyncCompletionHandlerBase() {
                            @Override
                            public Response onCompleted(Response response) throws Exception {

                                // First check the new version doesn't exist
                                if (response.getStatusCode() == 404) {

                                    log.info(String.format("Creating version %s on %s", newVersion, url));

                                    String newUrlWithParams = String.format("%s?description=%s&releaseDate=%s",
                                            newVersionUrl, versionDescription, versionReleaseDate);

                                    // If not - create it
                                    getHttpClient().preparePut(newUrlWithParams)
                                            .addHeader(AUTHORIZATION, BASIC + " " + authorization)
                                            .execute()
                                            .get();

                                    return response;

                                } else {
                                    log.info(String.format("Version %s already exists on %s", newVersion, url));
                                }

                                return response;

                            }
                        }
                );


        Response r = newVersionResponse.get(10, TimeUnit.MINUTES);

        if (r.getStatusCode() >= 300) {
            throw new MojoExecutionException(String.format("Unable to create Youtrack project at %s: %s", url, r.getStatusText()));
        }

    }

    public void releaseVersion(final String version) throws IOException, ExecutionException, InterruptedException, MojoExecutionException, TimeoutException {
        final String versionUrl = String.format("%s/rest/admin/project/%s/version/%s", this.url, project, version);

        Future<Response> newVersionResponse = getHttpClient().prepareGet(versionUrl)
                .addHeader(AUTHORIZATION, BASIC + " " + authorization)
                .execute(
                        new AsyncCompletionHandlerBase() {
                            @Override
                            public Response onCompleted(Response response) throws Exception {

                                new Exception().printStackTrace();

                                // First check the version exists
                                if (response.getStatusCode() == 200) {

                                    log.info(String.format("Releasing version %s on %s", version, YoutrackClient.this.url));

                                    String newUrlWithParams = String.format("%s?isReleased=true&releaseDate=%s",
                                            versionUrl, String.valueOf(new Date().getTime()));

                                    try {
                                        getHttpClient().preparePost(newUrlWithParams)
                                                .addHeader(AUTHORIZATION, BASIC + " " + authorization)
                                                .execute()
                                                .get();
                                    } catch (Exception e) {
                                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                                    }

                                    return response;
                                } else {
                                    throw new MojoExecutionException(String.format("Version %s doesn't exist on %s", version, YoutrackClient.this.url));
                                }
                            }
                        });

        Response r = newVersionResponse.get(10, TimeUnit.MINUTES);

        if (r.getStatusCode() >= 300) {
            throw new MojoExecutionException(String.format("Unable to release Youtrack project at %s: %s", url, r.getStatusText()));
        }

    }

    public void moveOpenIssues(final String originalVersion, final String newVersion) throws IOException, ExecutionException, InterruptedException {

        log.info(String.format("Moving open issues from version %s to %s on %s", originalVersion, newVersion, url));

        withIssues(String.format("fix for: %s state: Unresolved", originalVersion), new Function<Element, String>() {
            public String apply(Element element) {

                String id = element.getAttributeValue("id");

                try {
                    final String command = String.format("fixed in %s", newVersion);
                    final String comment = String.format("Unfinished issue moved issue from %s to %s", originalVersion, newVersion);

                    final String issueUrl = String.format("%s/rest/issue/%s/execute", url, id);

                    getHttpClient().preparePost(issueUrl)
                            .addHeader(AUTHORIZATION, BASIC + " " + authorization)
                            .addParameter("command", command)
                            .addParameter("comment", comment)
                            .execute()
                            .get();

                } catch (InterruptedException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                } catch (ExecutionException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                } catch (IOException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }

                return String.format("Movied issue %s from version %s to %s", id, originalVersion, newVersion);
            }
        });

    }


    public void withIssues(String filter, final Function<Element, String> issueFunction) throws IOException, ExecutionException, InterruptedException {

        // "http://youtrack.smx.co.nz/rest/project/issues/SMX3?filter=fix%20for:%20smx3.partyresource-2.6.2%20state:%20Open"

        String issueUrl = String.format("%s/rest/project/issues/%s?filter=%s", url, project, URLEncoder.encode(filter, "UTF-8"));

        log.debug("Loading issues from " + issueUrl);

        getHttpClient().prepareGet(issueUrl)
                .addHeader(AUTHORIZATION, BASIC + " " + authorization)
                .execute(
                        new AsyncCompletionHandlerBase() {
                            @Override
                            public Response onCompleted(Response response) throws Exception {
                                // First check the version exists
                                if (response.getStatusCode() == 200) {

                                    SAXBuilder builder = new SAXBuilder();
                                    Document doc = builder.build(response.getResponseBodyAsStream());

                                    List<Element> issues = doc.getRootElement().getChildren("issue");
                                    for (Element issue : issues) {
                                        String logLine = issueFunction.apply(issue);
                                        log.info(logLine);
                                    }

                                }

                                return response;
                            }
                        }).get();
    }

}
