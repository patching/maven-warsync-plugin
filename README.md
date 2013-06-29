maven-warsync-plugin
====================

This is a maven plugin generating configuration files of [FileSync](http://andrei.gmxhome.de/filesync/) eclipse plugin for maven webapp project.

The maven plugin will create a war directory (with all runtime jar files needed) under the project's build directory during `generate-resources` maven phase, and create profiles of the project (including referenced projects) to tell FileSync to synchronize class files and other resources (with maven filter feature) to the war directory.

**Never** `mvn install`

Latest Version
--------------------

`1.0`

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
						<!-- bind phase: generate-resources -->
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
After run `mvn eclipse:eclipse`:
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
