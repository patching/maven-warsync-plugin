maven-warsync-plugin
====================

This is a maven plugin generating configuration files of [FileSync](http://andrei.gmxhome.de/filesync/) eclipse plugin for maven webapp project.


Usage
--------------------

### add the plugin config in pom.xml of a maven webapp project

```XML
<build>
  <plugins>			
		<plugin>
			<groupId>com.taobao.maven</groupId>
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

### optional arguments

* ${warsync.skip}
will skip the gaol if is true.
* ${warsync.libMode}
 Default is "COPY".
 COPY: copy all dependencies into {webroot}/WEB-INF/lib
 REF: create warsync-classpath.jar with MANIFEST.MF file that specified all dependencies in "Class-Path" entry.
* ${warsync.dir}
Default is ${project.build.directory}/warsync/${project.build.finalName}.war


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
After execute "mvn eclipse:eclipse" or "mvn generate-resources": 
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
                            ...(all runtime dependencies' jar file)	--> only when ${warsync.libMode} == COPY
                            warsync-classpath.jar			--> only when ${warsync.libMode} == REF
    pom.xml
```
