package com.dksd.comms.server;

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
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Properties;

/**
 * Created by dylan on 05/04/15.
 */
public class FileServerSecure {

    private Properties properties;

    public FileServerSecure(Properties properties) throws IOException {
        this.properties = properties;
    }

    public void start() throws Exception {
        server = new Server(Integer.parseInt(properties.getProperty("http.port")));

        int securePort = -1;
        try {
            securePort = Integer.parseInt(properties.getProperty("https.port"));
        } catch (Exception ep) {
            ;//no https;
        }
        int timeout = 500000;
        int bufferSize = 32768;
        int httpPort = Integer.parseInt(properties.getProperty("http.port"));

        // HTTP Configuration
        // HttpConfiguration is a collection of configuration information appropriate for http and https. The default
        // scheme for http is <code>http</code> of course, as the default for secured http is <code>https</code> but
        // we show setting the scheme to show it can be done.  The port for secured communication is also set here.
        HttpConfiguration http_config = new HttpConfiguration();
        http_config.setSecureScheme("https");
        http_config.setSecurePort(securePort);
        http_config.setOutputBufferSize(bufferSize);

        // HTTP connector
        // The first server connector we create is the one for http, passing in the http configuration we configured
        // above so it can get things like the output buffer size, etc. We also set the port (8080) and configure an
        // idle timeout.
        ResourceHandler resource_handler;
        GzipHandler gzipHandlerRES;
        HandlerList handlers;
        try (ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(http_config))) {
            http.setPort(httpPort);
            http.setIdleTimeout(timeout);

            if (securePort != -1) {
                // SSL Context Factory for HTTPS and SPDY
                // SSL requires a certificate so we configure a factory for ssl contents with information pointing to what
                // keystore the ssl connection needs to know about. Much more configuration is available the ssl context,
                // including things like choosing the particular certificate out of a keystore to be used.
                SslContextFactory sslContextFactory = new SslContextFactory();

                final String STOREPASSWORD = properties.getProperty("cert.keystore.password");
                final KeyStore ks = KeyStore.getInstance("JKS");
                File kf = new File(properties.getProperty("cert.keystore.path"));
                ks.load(new FileInputStream(kf), STOREPASSWORD.toCharArray());

                final CertificateFactory cf = CertificateFactory.getInstance("X.509");
                try (InputStream caInput = new BufferedInputStream(
                        // this files is shipped with the application
                        new FileInputStream(properties.getProperty("cert.keystore.dstrootcax3")))) {
                    final Certificate crt = cf.generateCertificate(caInput);
                    System.out.println("Added Cert for " + ((X509Certificate) crt)
                            .getSubjectDN());
                    ks.setCertificateEntry("DSTRootCAX3", crt);
                }

                sslContextFactory.setKeyStorePath(properties.getProperty("cert.keystore.path"));
                sslContextFactory.setKeyStorePassword(properties.getProperty("cert.keystore.password"));
                sslContextFactory.setKeyManagerPassword(properties.getProperty("cert.keystore.password"));

                // HTTPS Configuration
                // A new HttpConfiguration object is needed for the next connector and you can pass the old one as an
                // argument to effectively clone the contents. On this HttpConfiguration object we add a
                // SecureRequestCustomizer which is how a new connector is able to resolve the https connection before
                // handing control over to the Jetty Server.
                HttpConfiguration https_config = new HttpConfiguration(http_config);
                https_config.addCustomizer(new SecureRequestCustomizer());

                // HTTPS connector
                // We create a second ServerConnector, passing in the http configuration we just made along with the
                // previously created ssl context factory. Next we set the port and a longer idle timeout.
                try (ServerConnector https = new ServerConnector(server,
                        new SslConnectionFactory(sslContextFactory, "http/1.1"),
                        new HttpConnectionFactory(https_config))) {
                    https.setPort(securePort);
                    https.setIdleTimeout(timeout);
                    server.setConnectors(new Connector[]{http, https});
                }
            } else {
                server.setConnectors(new Connector[]{http});
            }
        }

        resource_handler = new ResourceHandler();
        resource_handler.setWelcomeFiles(new String[]{ properties.getProperty("welcome.html.page") });
        //TODO externalize this to point elsewhere, aka not in the code FFS
        resource_handler.setResourceBase(properties.getProperty("resource.base"));
        resource_handler.setCacheControl("max-age=86400");
        resource_handler.setEtags(true);

        gzipHandlerRES = new GzipHandler();
        gzipHandlerRES.setIncludedMimeTypes("text/html,text/plain,text/xml,text/css,application/javascript,text/javascript");
        gzipHandlerRES.setHandler(resource_handler);

        handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{gzipHandlerRES, new DefaultHandler()});
        server.setHandler(handlers);

        //server.setHandler(new HelloHandler());
        // Start the server
        server.start();
        server.join();
    }

}
