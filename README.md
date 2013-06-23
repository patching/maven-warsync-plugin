maven-warsync-plugin
====================

这是一个可以为一个war类型的maven工程，生成[FileSync](http://andrei.gmxhome.de/filesync/)这个eclipse插件的配置文件的maven插件。

usage
--------------------

### 在一个packaging为war类型的工程的pom.xml中添加以下plugin配置

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
						<goal>eclipse</goal>
					</goals>
				</execution>
			</executions>
		</plugin>
</build>
```

### 可选配置参数

* ${warsync.skip}
	如果为true，则跳过该插件目标
* ${warsync.libMode}
	可选"COPY"或者"REF"，默认为"COPY"，表示将所有的依赖复制到{webroot}/WEB-INF/lib下面。如果选择REF，则会在{webroot}/WEB-INF/lib下面生成一个warsync-classpath.jar包，里面的MANIFEST.MF通过Class-Path引用了所有依赖。
* ${warsync.dir}
	即同步到目标war目录，默认为${project.build.directory}/warsync/${project.build.finalName}.war
