# ejstatd - Enhanced `jstatd`

A firewall and Docker friendly and Java policy free version of [`jstatd`](http://docs.oracle.com/javase/7/docs/technotes/tools/share/jstatd.html).

`jstatd` is a basic monitoring tool that can be used to remotely monitor you JVM's. It requires 3 ports in order to work by default. The program allows you to pick one of them using the standard command line arguments, while the other 2 are randomly picked. This makes using this tool in larger environments a pain for firewall administrators.

With `ejstatd` you can control those ports using those 3 parameters: (in addition to classical `-p` to control RMI registry port):
 - `-pr <port>`: specify the port on which the RMI registry will start (like `jstatd`'s `-p`)
 - `-ph <port>` (or setting the port number to JVM system property `ejstatd.remoteHost.port`): control the port on which the `sun.jvmstat.monitor.remote.RemoteHost` will be exported.
 - `-pv <port>` (or setting the port number to JVM system property `ejstatd.remoteVm.port`): control the port on which the `sun.jvmstat.monitor.remote.RemoteVm` will be exported.

`ejstatd` also gets rid of `jstatd` usual [`access denied ("java.util.PropertyPermission" "java.rmi.server.ignoreSubClasses" "write")`](http://stackoverflow.com/q/9939883/535203) problem without defining a `java.security.policy` system property: it writes its own needed java policy file and use it (if you don't define this system property using `-Djava.security.policy`).

# Usage
After having compiled the project (`mvn package`), one can launch `ejstatd` using those 2 different ways (replace the ports specified here - `2222`, `2223` and `2224` - with your own):
 - `mvn -e exec:java -Dexec.args="-pr2222 -ph2223 -pv2224"` using Maven

or
 - `java -cp "target\ejstatd-1.0.0.jar;%JAVA_HOME%\lib\tools.jar" com.github.anthony_o.ejstatd.EJstatd -pr2222 -ph2223 -pv2224` on Windows, if `JAVA_HOME` is set as an environment variable 
 - `java -cp "target/ejstatd-1.0.0.jar:$JAVA_HOME/lib/tools.jar" com.github.anthony_o.ejstatd.EJstatd -pr2222 -ph2223 -pv2224` on Unix (using Bash), if `JAVA_HOME` is set as an environment variable

You can also specify the arguments with spaces before the ports, like this:
 - `mvn -e exec:java -Dexec.args="-pr 2222 -ph 2223 -pv 2224"`

# Prerequisites
 1. Install a [JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
 1. [Install Maven](http://maven.apache.org/install.html) (make sure its `bin` folder is in the `PATH`)
 1. Clone this Github project
 1. `cd` into the project and create the package using `mvn package` command

# Thanks
This program is based on JDK 7's `jstatd` (using sources from its [Mercurial repository](http://hg.openjdk.java.net/jdk7u/jdk7u/jdk)) and highly inspired by [jdonofrio728](https://github.com/jdonofrio728/)'s [JakestatD](https://github.com/jdonofrio728/jakestatd).