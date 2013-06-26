maven-warsync-plugin
====================

This is a maven plugin generating configuration files of [FileSync](http://andrei.gmxhome.de/filesync/) eclipse plugin for maven webapp project.


Usage
--------------------

### add the plugin config to the pom.xml of a maven webapp project

```XML
<build>
  <plugins>			
		<plugin>
			<groupId>com.github.patching</groupId>
			<artifactId>maven-warsync-plugin</artifactId>
			<version>${warsync.version}</version>
			<executions>
				<execution>
					<goals>
						<!-- binded phase: generate-resources -->
						<goal>eclipse</goal>
					</goals>
				</execution>
			</executions>
		</plugin>
</build>
```

A Sample
--------------------

For a maven project with multiple modules:
```
sample/			--> sample.pom
    module1/		--> module1.jar
        src/
            main/
        pom.xml
    module2/		--> module2.jar
        src/
            main/
        pom.xml
    module3/		--> module3.war
        src/
            main/
                webapp/
        pom.xml
    pom.xml
```
After run "mvn eclipse:eclipse": 
```
sample/
    module1/
        .setting/
            de.loskutov.FileSync.prefs		--> will be recognized by FileSync eclipse plugin
        src/
            main/
        pom.xml
    module2/
        .setting/
            de.loskutov.FileSync.prefs		--> will be recognized by FileSync eclipse plugin
        src/
            main/
        pom.xml
    module3/
        .setting/
            de.loskutov.FileSync.prefs		--> will be recognized by FileSync eclipse plugin
        src/
            main/
                webapp/
        pom.xml
        target/
            warsync/
                module3.war/
                    WEB-INF/
                        lib/
                            ...(all runtime dependencies' jar files)	--> only when ${warsync.libMode} == copy
                            warsync-classpath.jar						--> only when ${warsync.libMode} == ref
    pom.xml
```
