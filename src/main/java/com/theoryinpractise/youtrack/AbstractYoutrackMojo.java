package com.theoryinpractise.youtrack;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;

/**
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Aug 18, 2010
 * Time: 8:15:24 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AbstractYoutrackMojo extends AbstractMojo {
    /**
     * The current build session instance. This is used for
     * toolchain manager API calls.
     *
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    protected MavenSession session;
    /**
     * @parameter
     * @required
     */
    protected String server;
    /**
     * @parameter
     * @required
     */
    protected String project;
    /**
     * @parameter
     */
    protected Integer iterationLength;
}
