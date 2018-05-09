package com.dksd.jettyserver;

import org.apache.log4j.PropertyConfigurator;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
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
}
