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
 - `mvn exec:java -Dexec.args="-pr2222 -ph2223 -pv2224"` using Maven

or
 - `java -cp "target\ejstatd-1.0.0.jar;%JAVA_HOME%\lib\tools.jar" com.github.anthony_o.ejstatd.EJstatd -pr2222 -ph2223 -pv2224` on Windows, if `JAVA_HOME` is set as an environment variable 
 - `java -cp "target/ejstatd-1.0.0.jar:$JAVA_HOME/lib/tools.jar" com.github.anthony_o.ejstatd.EJstatd -pr2222 -ph2223 -pv2224` on Unix (using Bash), if `JAVA_HOME` is set as an environment variable

You can also specify the arguments with spaces before the ports, like this:
 - `mvn exec:java -Dexec.args="-pr 2222 -ph 2223 -pv 2224"`

# Usage in Docker
In this section we will consider using those 3 ports as example, don't forget to replace them with yours: `2222` for `pr`, `2223` for `ph` and `2224` for `pv`.

Inside a Docker container, don't forget to specify `-Djava.rmi.server.hostname=$HOST_HOSTNAME` when launching `ejstatd`. This environment variable should be set to the host's hostname passing for example `-e HOST_HOSTNAME=$HOSTNAME` to `docker run` command.

You should as well force the 3 ports (using `-pr2222 -ph2223 -pv2224` when launching `ejstatd`) and expose them to the Docker host specifying `-p 2222:2222 -p 2223:2223 -p 2224:2224` to `docker run` command.

To sum up, here is the minimum Docker run command:
 - `docker run -e HOST_HOSTNAME=$HOSTNAME -p 2222:2222 -p 2223:2223 -p 2224:2224 myimage`

And inside the Docker image `myimage`, `ejstatd` should be launched from a script in background in this way:
 - `mvn -Djava.rmi.server.hostname=$HOST_HOSTNAME exec:java -Dexec.args="-pr 2222 -ph 2223 -pv 2224" &`

Then you could access this `ejstatd` using JVisualVM running on your Desktop PC for example adding a "Remote Host" specifying your Docker hostname as "Host name" and adding a "Custom jstatd Connections" (in the "Advanced Settings") by setting "2222" to "Port".

# Usage with Openshift (Production case, assuming that you cannot restart POD)
1. Download `ejstatd` & corresponding `tools.jar` (from java folder) to the machine. (You can use [Droppy Tool](https://github.com/stackp/Droopy)  or even wget if you have public facing storage ) for that.
2. Run it: 
```bash
java -Djava.rmi.server.hostname=localhost -cp "ejstatd-1.0.0.jar:tools1.8.jar" com.github.anthony_o.ejstatd.EJstatd -pr2222 -ph2223 -pv2224 
```
3. On the VisualVm machine - login to openshift & enable port forwarding:
```bash
oc login <address> --token=<token>
oc port-forward <POD ID> 2222 2223 2224
```
4. Open VisualVm & connect jstat to local machine port 2222

# Prerequisites
 1. Install a [JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
 1. [Install Maven](http://maven.apache.org/install.html) (make sure its `bin` folder is in the `PATH`)
 1. Clone this Github project
 1. `cd` into the project and create the package using `mvn package` command

# Thanks
This program is based on JDK 7's `jstatd` (using sources from its [Mercurial repository](http://hg.openjdk.java.net/jdk7u/jdk7u/jdk)) and highly inspired by [jdonofrio728](https://github.com/jdonofrio728/)'s [JakestatD](https://github.com/jdonofrio728/jakestatd).
