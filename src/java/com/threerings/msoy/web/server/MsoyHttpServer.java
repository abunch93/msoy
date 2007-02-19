//
// $Id$

package com.threerings.msoy.web.server;

import java.io.File;
import java.util.Enumeration;

import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpServer;
import org.mortbay.http.handler.ResourceHandler;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;
import com.threerings.msoy.server.ServerConfig;

/**
 * Handles HTTP requests made of the Msoy server by the AJAX client and other
 * entities.
 */
public class MsoyHttpServer extends HttpServer
{
    /**
     * Creates and prepares our HTTP server for operation but does not yet
     * start listening on the HTTP port.
     */
    public MsoyHttpServer ()
    {
        // wire up serving of static content (for testing)
        HttpContext context = getContext("/");
        context.setResourceBase(
            new File(ServerConfig.serverRoot, "pages").getPath());
        context.addHandler(new ResourceHandler());

        // wire up our GWT servlets
        ServletHandler handler= new ServletHandler();
        for (int ii = 0; ii < SERVLETS.length; ii += 2) {
            String name = SERVLETS[ii];
            handler.addServlet(name, "/msoy/" + name, SERVLETS[ii+1]);
            handler.addServlet(name, "/" + name, SERVLETS[ii+1]);
        }

        context.addHandler(handler);

        // tone down the default verbose logging; unfortunately some creates a
        // new logger and logs verbosely to it before we get a chance to shut
        // it the fuck up, but it's mostly minimal
        LogManager logmgr = LogManager.getLogManager();
        for (Enumeration<String> iter = logmgr.getLoggerNames();
             iter.hasMoreElements(); ) {
            String name = iter.nextElement();
            if (name.startsWith("org.mortbay")) {
                Logger logger = logmgr.getLogger(name);
                logger.setLevel(Level.WARNING);
            }
        }
    }

    /**
     * Initializes our HTTP server and begins listening for connections.
     */
    public void init ()
        throws Exception
    {
        // listen for connections on our preferred port
        addListener(":" + ServerConfig.getHttpPort());
        start();
    }

    /** GWT Servlets */
    protected static final String[] SERVLETS = {
        "usersvc", WebUserServlet.class.getName(),
        "itemsvc", ItemServlet.class.getName(),
        "catalogsvc", CatalogServlet.class.getName(),
        "profilesvc", ProfileServlet.class.getName(),
        "membersvc", MemberServlet.class.getName(),
        "groupsvc", GroupServlet.class.getName(),
        "mailsvc", MailServlet.class.getName(),
        "uploadsvc", UploadServlet.class.getName(),
        "gamesvc", GameServlet.class.getName(),
        "swiftlysvc", SwiftlyServlet.class.getName()
    };
}
