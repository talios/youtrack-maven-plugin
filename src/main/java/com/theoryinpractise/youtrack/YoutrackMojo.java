package com.theoryinpractise.youtrack;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.ning.http.client.AsyncCompletionHandlerBase;
import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import sun.misc.BASE64Encoder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Goal which touches a timestamp file.
 *
 * @goal create-version
 */
public class YoutrackMojo extends AbstractMojo {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BASIC = "Basic";

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

    /**
     * @parameter expression="${project.version}"
     */
    private String version;

    /**
     * @parameter expression="${project.groupId}"
     */
    private String groupId;

    /**
     * @parameter expression="${project.artifactId}"
     */
    private String artifactId;

    /**
     * @parameter expression="${project.description}"
     */
    private String description;

    public void execute() throws MojoExecutionException {

        try {

            Server mavenServer = session.getSettings().getServer(server);
            String logon = mavenServer.getUsername() + ":" + mavenServer.getPassword();

            System.out.println("config-class: " + mavenServer.getConfiguration().getClass().getName());
            System.out.println("config: " + mavenServer.getConfiguration());

            Xpp3Dom mavenServerConfiguration = (Xpp3Dom) mavenServer.getConfiguration();


            final String url = mavenServerConfiguration.getChild("url").getValue();

            System.out.println("config url is " + url);

            final String encodedLogon = new BASE64Encoder().encode(logon.getBytes());

            final String baseVersion = version.replace("-SNAPSHOT", "");
            final String newVersionUrl = String.format("%s/rest/admin/project/%s/version/%s-%s",
                    url, project, artifactId, baseVersion);


            final Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, iterationLength != null ? iterationLength : 14);

            final AsyncHttpClient client = new AsyncHttpClient();

            Future<Response> newVersion = client.prepareGet(newVersionUrl)
                    .addHeader(AUTHORIZATION, BASIC + " " + encodedLogon)
                    .execute(
                    new AsyncCompletionHandlerBase() {
                        @Override
                        public Response onCompleted(Response response) throws Exception {

                            // First check the new version doesn't exist
                            if (response.getStatusCode() == 404) {

                                final String versionDescription = description.equals(artifactId)
                                        ? String.format("Release %s of %s/%s", baseVersion, groupId, artifactId)
                                        : String.format("Release %s of %s/%s - %s", baseVersion, groupId, artifactId, description);

                                final String versionReleaseDate = String.valueOf(cal.getTime().getTime());

                                String newUrlWithParams = String.format("%s?description=%s&releaseDate=%s",
                                        newVersionUrl, versionDescription, versionReleaseDate);

                                // If not - create it
                                Future<Response> newVersion = client
                                        .preparePut(newUrlWithParams)
                                        .addHeader(AUTHORIZATION, BASIC + " " + encodedLogon)
                                        .execute();

                                return newVersion.get();

                            }

                            return response;

                        }
                    }
            );


            Response r = newVersion.get();

            if (r.getStatusCode() >= 300) {
                throw new MojoExecutionException(String.format("Unable to create Youtrack project at %s: %s", url, r.getStatusText()));
            }


        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (InterruptedException e) {
            throw new MojoExecutionException(e.getMessage());
        } catch (ExecutionException e) {
            throw new MojoExecutionException(e.getMessage());
        }


    }
}
