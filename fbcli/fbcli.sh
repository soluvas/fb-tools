#!/bin/bash
# Must run first: mvn package dependency:copy-dependencies
java -cp 'target/dependency/*:target/fbcli-1.0.0-SNAPSHOT.jar' org.jboss.weld.environment.se.StartMain "$@"

