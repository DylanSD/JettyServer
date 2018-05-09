#!/bin/bash

mvn exec:java -Dname="Jetty" -Dexec.mainClass="com.dksd.comms.server.FileServerMain" -Dexec.args="local.cfg"
