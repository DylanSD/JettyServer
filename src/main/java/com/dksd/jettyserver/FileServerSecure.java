package com.dksd.jettyserver;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.*;
import java.security.KeyStore;
import java.util.Properties;

/**
 * Created by dylan on 05/04/15.
 */
public class FileServerSecure {

    private static final String MAX_AGE_86400 = "max-age=86400";
    private Server server;
    private Properties properties;

    private int httpPort;
    private int httpsPort;
    private int timeout = 500000;
    private int bufferSize = 32768;
    private String keystorePassword;
    private String keystorePath;
    private String webappPath;
    private String certificatePath;
    private String welcomePage;
    private String domain;

    public FileServerSecure(Properties properties) {
        this.properties = properties;
        setProperties(properties);
    }

    private void setProperties(Properties properties) {
        httpPort = Integer.parseInt(properties.getProperty("http.port"));
        httpsPort = Integer.parseInt(properties.getProperty("https.port"));
        keystorePath = properties.getProperty("cert.keystore.path");
        keystorePassword = properties.getProperty("cert.keystore.password");
        webappPath = properties.getProperty("webapp.path");
        welcomePage = properties.getProperty("welcome.html.page");
    }

    public void start() throws Exception {
        server = new Server(httpPort);

        // HTTP Configuration
        // HttpConfiguration is a collection of configuration information appropriate for http and https. The default
        // scheme for http is <code>http</code> of course, as the default for secured http is <code>https</code> but
        // we show setting the scheme to show it can be done.  The port for secured communication is also set here.
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSecureScheme("https");
        httpConfig.setSecurePort(httpsPort);
        httpConfig.setOutputBufferSize(bufferSize);

        // HTTP connector
        // The first server connector we create is the one for http, passing in the http configuration we configured
        // above so it can get things like the output buffer size, etc. We also set the port (8080) and configure an
        // idle timeout.
        ResourceHandler resourceHandler;
        GzipHandler gzipHandlerRES;
        HandlerList handlers;
        try (ServerConnector http = new ServerConnector(server,
                new HttpConnectionFactory(httpConfig))) {
            http.setPort(httpPort);
            http.setIdleTimeout(timeout);

            KeyStore.getInstance("JKS").load(new FileInputStream(new File(keystorePath)), keystorePassword.toCharArray());
            // SSL Context Factory for HTTPS and SPDY
            // SSL requires a certificate so we configure a factory for ssl contents with information pointing to what
            // keystore the ssl connection needs to know about. Much more configuration is available the ssl context,
            // including things like choosing the particular certificate out of a keystore to be used.
            SslContextFactory sslContextFactory = new SslContextFactory();

            /* I am not sure we need the DST root now with CertBot
            import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

            final CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (InputStream caInput = new BufferedInputStream(
                    // this files is shipped with the application
                    new FileInputStream(properties.getProperty("cert.keystore.dstrootcax3")))) {
                final Certificate crt = cf.generateCertificate(caInput);
                System.out.println("Added Cert for " + ((X509Certificate) crt)
                        .getSubjectDN());
                ks.setCertificateEntry("DSTRootCAX3", crt);
            }*/

            sslContextFactory.setKeyStorePath(keystorePath);
            sslContextFactory.setKeyStorePassword(keystorePassword);
            sslContextFactory.setKeyManagerPassword(keystorePassword);

            // HTTPS Configuration
            // A new HttpConfiguration object is needed for the next connector and you can pass the old one as an
            // argument to effectively clone the contents. On this HttpConfiguration object we add a
            // SecureRequestCustomizer which is how a new connector is able to resolve the https connection before
            // handing control over to the Jetty Server.
            HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
            httpsConfig.addCustomizer(new SecureRequestCustomizer());

            // HTTPS connector
            // We create a second ServerConnector, passing in the http configuration we just made along with the
            // previously created ssl context factory. Next we set the port and a longer idle timeout.
            try (ServerConnector https = new ServerConnector(server,
                    new SslConnectionFactory(sslContextFactory, "http/1.1"),
                    new HttpConnectionFactory(httpsConfig))) {
                https.setPort(httpsPort);
                https.setIdleTimeout(timeout);
                server.setConnectors(new Connector[]{http, https});
            }
        }

        resourceHandler = new ResourceHandler();
        resourceHandler.setWelcomeFiles(new String[]{welcomePage});
        resourceHandler.setResourceBase(webappPath);
        resourceHandler.setCacheControl(MAX_AGE_86400);
        resourceHandler.setEtags(true);

        gzipHandlerRES = new GzipHandler();
        gzipHandlerRES.setIncludedMimeTypes("text/html,text/plain,text/xml,text/css,application/javascript,text/javascript");
        gzipHandlerRES.setHandler(resourceHandler);

        handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{gzipHandlerRES, new DefaultHandler()});
        server.setHandler(handlers);

        server.start();
        server.join();
    }

}
