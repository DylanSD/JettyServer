package com.dksd.jettyserver;

import org.apache.log4j.PropertyConfigurator;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Properties;

/**
 * Created by dylan on 23/04/15.
 */
public class FileServerMain {
    public static void main(String[] args) {
        try {
            Properties props = new Properties();
            props.setProperty("log4j.appender.CONSOLE", "org.apache.log4j.ConsoleAppender");
            props.setProperty("log4j.appender.CONSOLE.Threshold", "TRACE");
            props.setProperty("log4j.appender.CONSOLE.layout", "org.apache.log4j.PatternLayout");
            props.setProperty("log4j.appender.CONSOLE.layout.ConversionPattern", "%-5p %d{HH:mm:ss} %-30C{1} | %m%n");
            props.setProperty("log4j.rootLogger", "TRACE, CONSOLE");
            PropertyConfigurator.configure(props);

            if (args.length < 1) {
                printUsage();
            } else {
                FileServerSecure fs = new FileServerSecure(load(args[0]));
                fs.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Properties load(String fileName) {
        final Properties prop = new Properties();
        try {
            InputStream is = new FileInputStream(new File(fileName));
            prop.load(is);

        } catch (Exception ep) {
            ep.printStackTrace();
        }
        return prop;
    }

    private static void printUsage() {
        System.out.println("Usage: FileServerMain config.cfg");
    }

    /**
     * Created by dylan on 05/04/15.
     */
    private static class FileServerSecure {

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
        private String welcomePage;

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

            HttpConfiguration httpConfig = new HttpConfiguration();
            httpConfig.setSecureScheme("https");
            httpConfig.setSecurePort(httpsPort);
            httpConfig.setOutputBufferSize(bufferSize);

            ResourceHandler resourceHandler;
            GzipHandler gzipHandlerRES;
            HandlerList handlers;
            try (ServerConnector http = new ServerConnector(server,
                    new HttpConnectionFactory(httpConfig))) {
                http.setPort(httpPort);
                http.setIdleTimeout(timeout);

                KeyStore.getInstance("JKS").load(new FileInputStream(new File(keystorePath)), keystorePassword.toCharArray());
                SslContextFactory sslContextFactory = new SslContextFactory();

                sslContextFactory.setKeyStorePath(keystorePath);
                sslContextFactory.setKeyStorePassword(keystorePassword);
                sslContextFactory.setKeyManagerPassword(keystorePassword);

                HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
                httpsConfig.addCustomizer(new SecureRequestCustomizer());

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
}
