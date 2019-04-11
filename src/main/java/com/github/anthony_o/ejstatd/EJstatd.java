/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.github.anthony_o.ejstatd;

import sun.jvmstat.monitor.remote.RemoteHost;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.CodeSource;
import java.util.*;

/**
 * Application providing remote access to the jvmstat instrumentation
 * exported by local Java Virtual Machine processes. Remote access is
 * provided through an RMI interface.
 *
 * @author Brian Doherty
 * @since 1.7+
 */
public class EJstatd {

    private static Registry registry;
    private static int port = -1;
    private static boolean startRegistry = true;

    private static void printUsage() {
        System.err.println("usage: ejstatd [-nr] [-pr port] [-ph port] [-pv port] [-n rminame]");
    }

    static void bind(final String name, final RemoteHostImpl remoteHost)
                throws RemoteException, MalformedURLException, Exception {

        try {
            Naming.rebind(name, remoteHost);
        } catch (java.rmi.ConnectException e) {
            /*
             * either the registry is not running or we cannot contact it.
             * start an internal registry if requested.
             */
            if (startRegistry && registry == null) {
                int localport = (port < 0) ? Registry.REGISTRY_PORT : port;
                registry = LocateRegistry.createRegistry(localport);
                bind(name, remoteHost);
            }
            else {
                System.out.println("Could not contact registry\n"
                                   + e.getMessage());
                e.printStackTrace();
            }
        } catch (RemoteException e) {
            System.err.println("Could not bind " + name + " to RMI Registry");
            e.printStackTrace();
        }
    }

    public static void main(final String[] args) throws IOException, URISyntaxException, ClassNotFoundException {
        String rminame = null;
        int argc = 0;
        int remoteHostPort = Integer.parseInt(System.getProperty("ejstatd.remoteHost.port", "0"));

        for ( ; (argc < args.length) && (args[argc].startsWith("-")); argc++) {
            String arg = args[argc];

            if (arg.compareTo("-nr") == 0) {
                startRegistry = false;
            } else if (arg.startsWith("-pr")) {
                if (arg.compareTo("-pr") != 0) {
                    port = Integer.parseInt(arg.substring(3));
                } else {
                  argc++;
                  if (argc >= args.length) {
                      printUsage();
                      System.exit(1);
                  }
                  port = Integer.parseInt(args[argc]);
                }
            } else if (arg.startsWith("-ph")) {
                if (arg.compareTo("-ph") != 0) {
                    remoteHostPort = Integer.parseInt(arg.substring(3));
                } else {
                    argc++;
                    if (argc >= args.length) {
                        printUsage();
                        System.exit(1);
                    }
                    remoteHostPort = Integer.parseInt(args[argc]);
                }
            } else if (arg.startsWith("-pv")) {
                if (arg.compareTo("-pv") != 0) {
                    System.setProperty("ejstatd.remoteVm.port", new Integer(arg.substring(3)).toString());
                } else {
                    argc++;
                    if (argc >= args.length) {
                        printUsage();
                        System.exit(1);
                    }
                    System.setProperty("ejstatd.remoteVm.port", new Integer(args[argc]).toString());
                }
            } else if (arg.startsWith("-n")) {
                if (arg.compareTo("-n") != 0) {
                    rminame = arg.substring(2);
                } else {
                    argc++;
                    if (argc >= args.length) {
                        printUsage();
                        System.exit(1);
                    }
                    rminame = args[argc];
                }
            } else {
                printUsage();
                System.exit(1);
            }
        }

        if (argc < args.length) {
            printUsage();
            System.exit(1);
        }

        if (System.getProperty("java.security.policy") == null) {
            // Add "permission java.security.AllPermission" for the codebase of this class by default
            File policyFile = File.createTempFile("ejstatd.all.", ".policy");
            policyFile.deleteOnExit();

            // Adding "permission java.security.AllPermission" for this jar + JDK tools.jar + eventually the main jar launching this (needed because if we run from IntelliJ the main class really launched is not this one)
            Collection<Class<?>> classesToAllow = new ArrayList<Class<?>>();

            Class<?> currentRunningClass;
            // detecting the class which runs EJstatd in order to add permission to it too (happens when launching from Maven) thanks to http://stackoverflow.com/a/36949543/535203
            StackTraceElement trace[] = Thread.currentThread().getStackTrace();
            currentRunningClass = Class.forName(trace[trace.length - 1].getClassName());

            if (Thread.class.isAssignableFrom(currentRunningClass)) {
                // This is only a Thread, we must know the class which is run by this Thread, happens when launching from Maven
                String className = trace[trace.length - 2].getClassName();
                try {
                    currentRunningClass = Class.forName(className);
                } catch (ClassNotFoundException e) {
                    // Happens when launching from Maven because the class launched by this Thread is not loaded by this Thread's ClassLoader. We will use the main's Thread ClassLoader to get this class
                    // Detecting main Thread thanks to http://stackoverflow.com/a/939995/535203
                    Map<Thread, StackTraceElement[]> stackTraceMap = Thread.getAllStackTraces();
                    for (Thread thread : stackTraceMap.keySet()) {
                        if ("main".equals(thread.getName())) {
                            // This is the main thread, we now use its classloader
                            ClassLoader mainClassLoader = thread.getContextClassLoader();
                            currentRunningClass = mainClassLoader.loadClass(className);
                            // Also add other classes that needs those permissions
                            StackTraceElement[] mainStackTrace = thread.getStackTrace();
                            classesToAllow.add(mainClassLoader.loadClass(mainStackTrace[mainStackTrace.length - 1].getClassName())); // the real main called by Maven
                            classesToAllow.add(mainClassLoader.loadClass("org.apache.maven.DefaultMaven"));
                            classesToAllow.add(mainClassLoader.loadClass("org.apache.maven.cli.MavenCli"));
                            break;
                        }
                    }
                }
            }

            classesToAllow.add(currentRunningClass);
            if (!currentRunningClass.equals(EJstatd.class)) {
                // we are running from another main, adding also EJstatd.class
                classesToAllow.add(EJstatd.class);
            }
            classesToAllow.add(RemoteHost.class); // used to allow JDK's tools.jar

            Set<String> codebasesToAllow = new HashSet<String>();
            for (Class<?> klass : classesToAllow) {
                CodeSource codeSource = klass.getProtectionDomain().getCodeSource();
                if (codeSource != null) {
                    // System.err.println("allow " + codeSource.getLocation().toURI().toString());
                    codebasesToAllow.add(codeSource.getLocation().toURI().toString());
                }
            }
            if (!codebasesToAllow.contains("jrt:/jdk.internal.jvmstat") && codebasesToAllow.contains("jrt:/jdk.jstatd")) {
                codebasesToAllow.add("jrt:/jdk.internal.jvmstat");
            }

            FileOutputStream policyOutputStream = new FileOutputStream(policyFile);
            try {
                for (String codebase : codebasesToAllow) {
                    if (codebase.endsWith("/")) {
                        codebase += "-"; // in development, we are launching from a folder with compiled classes in it
                    }
                    policyOutputStream.write(("grant codebase \""+codebase+"\" {permission java.security.AllPermission;};").getBytes());
                }
            } finally {
                policyOutputStream.close();
            }
            System.setProperty("java.security.policy", policyFile.toString());
            System.err.println("java.security.policy=" + policyFile.toString());
            System.err.println(new String(Files.readAllBytes(Paths.get(policyFile.toString()))));
        }

        if (System.getSecurityManager() == null) {
            System.setSecurityManager(new RMISecurityManager());
        }

        StringBuilder name = new StringBuilder();

        if (port >= 0) {
            name.append("//:").append(port);
        }

        if (rminame == null) {
            rminame = "JStatRemoteHost";
        }

        name.append("/").append(rminame);

        try {
            // use 1.5.0 dynamically generated subs.
            System.setProperty("java.rmi.server.ignoreSubClasses", "true");
            RemoteHostImpl remoteHost = new RemoteHostImpl();
            RemoteHost stub = (RemoteHost) UnicastRemoteObject.exportObject(
                    remoteHost, remoteHostPort);
            bind(name.toString(), remoteHost);
        } catch (MalformedURLException e) {
            if (rminame != null) {
                System.out.println("Bad RMI server name: " + rminame);
            } else {
                System.out.println("Bad RMI URL: " + name + " : "
                                   + e.getMessage());
            }
            System.exit(1);
        } catch (java.rmi.ConnectException e) {
            // could not attach to or create a registry
            System.out.println("Could not contact RMI registry\n"
                               + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.out.println("Could not create remote object\n"
                               + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
