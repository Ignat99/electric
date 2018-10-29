/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Launcher.java
 *
 * Copyright (c) 2003, Static Free Software. All rights reserved.
 *
 * Electric(tm) is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Electric(tm) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sun.electric;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;

import com.sun.electric.util.test.TestByReflection;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class initializes the Electric. It is the main entrypoint of the system.
 * First it re-launches a JVM with sufficient memory if default memory limit s
 * too small. Then it loads com.sun.electric.Main class in a new ClassLoader.
 * The new ClassLoader is aware of jars with Electric plugins and dependencies.
 */
public final class Launcher {
    private static final boolean enableAssertions = true;
    private static final Logger logger = LoggerFactory.getLogger(Launcher.class);
//    private static LocalLogger logger = new LocalLogger(Launcher.class);

    private static final String[] propertiesToCopy = { "user.home" };

    private Launcher() {
    }

    /**
     * The main entry point of Electric. The "main" method in this class checks
     * to make sure that the JVM has enough memory. If not, it invokes a new JVM
     * with the proper amount. If so, it goes directly to Main.main to start
     * Electric.
     * 
     * @param args
     *            the arguments to the program.
     */
    public static void main(String[] args) {
        // ignore launcher if specified to do so
        for (int i = 0; i < args.length; i++) {
            String str = args[i];
            if (str.equals("-NOMINMEM")) {
                // just start electric
                loadAndRunMain(args, true);
                return;
            }
            if (str.equals("-help") || str.equals("-version") || str.equals("-v")) {
                // just start electric
                loadAndRunMain(args, false);
                return;
            }
        }

        String program = "java";
        String javaHome = System.getProperty("java.home");
        if (javaHome != null)
            program = javaHome + File.separator + "bin" + File.separator + program;

        if (args.length >= 2 && args[0].equals("-regression") || args.length >= 3 && args[0].equals("-debug")
                && args[1].equals("-regression") || args.length >= 4 && args[0].equals("-threads")
                && args[2].equals("-regression") || args.length >= 4 && args[0].equals("-logging")
                && args[2].equals("-regression")) {
            initClasspath(true);
            System.exit(invokeRegression(args) ? 0 : 1);
            return;
        }

        // see if the required amount of memory is already present
        Runtime runtime = Runtime.getRuntime();
        int maxMem = (int) (runtime.maxMemory() / 1000000);
        int maxPerm = (int) (getPermGenMax() / 1000000);
        int maxMemWanted = getUserInt(StartupPrefs.MemorySizeKey, StartupPrefs.MemorySizeDef);
        int maxPermWanted = getUserInt(StartupPrefs.PermSizeKey, StartupPrefs.PermSizeDef);
        if (maxMemWanted <= maxMem && (maxPermWanted == 0 || maxPermWanted <= maxPerm) ||
        	Arrays.asList(args).contains("-pipeserver"))
        {
            // already have the desired amount of memory: just start Electric
            loadAndRunMain(args, true);
            return;
        }

        // Start Electric in subprocess

        // build the command line for reinvoking Electric
        List<String> procArgs = new ArrayList<String>();
        procArgs.add(program);
        procArgs.add("-cp");
        procArgs.add(getJarLocation());
        procArgs.add("-ss2m");
        if (enableAssertions)
            procArgs.add("-ea"); // enable assertions
        procArgs.add("-mx" + maxMemWanted + "m");
        if (maxPermWanted > 0)
            procArgs.add("-XX:MaxPermSize=" + maxPermWanted + "m");
        for (int i = 0; i < propertiesToCopy.length; i++) {
            String propValue = System.getProperty(propertiesToCopy[i]);
            if (propValue != null && propValue.length() > 0) {
                if (propValue.indexOf(' ') >= 0)
                    propValue = "\"" + propValue + "\"";
                procArgs.add("-D" + propertiesToCopy[i] + "=" + propValue);
            }
        }
        procArgs.add("com.sun.electric.Launcher");
        procArgs.add("-NOMINMEM");
        for (int i = 0; i < args.length; i++)
            procArgs.add(args[i]);

        if (maxMemWanted > maxMem)
            logger.info("Current Java memory limit of {}MEG is too small, rerunning Electric with a memory limit of {}MEG",
                    maxMem, maxMemWanted);
        if (maxPermWanted > 0)
            logger.info("Setting maximum permanent space (2nd GC) to {}MEG", maxPermWanted);
        ProcessBuilder processBuilder = new ProcessBuilder(procArgs);
        try {
            processBuilder.start();
            // Sometimes process doesn't finish nicely for some reason, try and
            // kill it here
            System.exit(0);
        } catch (IOException e) {
            // problem figuring out what computer this is: just start Electric
            logger.warn("Error starting Electric subprocess", e);
            loadAndRunMain(args, false);
        }
    }

    public static long getPermGenMax()
	{
		for (MemoryPoolMXBean mx : ManagementFactory.getMemoryPoolMXBeans())
		{
			if (mx.getName().indexOf("Perm Gen") >= 0)
				return mx.getUsage().getMax();
		}
		return 0;
	}

    private static String[] removeArg(String[] args, String option) {
        List<String> tmp = new ArrayList<String>();
        Collections.addAll(tmp, args);
        tmp.remove(option);
        return tmp.toArray(new String[tmp.size()]);
    }

    @TestByReflection(testMethodName = "getAdditionalFolder")
    private static String[] getAdditionalFolder(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("-additionalfolder=")) {
                //additionalFolder = arg.replaceAll("-additionalfolder=", "");
                args = removeArg(args, arg);
                return args;
            }
        }
        return args;
    }

    /**
     * Method to return a String that gives the path to the Electric JAR file.
     * If the path has spaces in it, it is quoted.
     * 
     * @return the path to the Electric JAR file.
     */
    public static String getJarLocation() {
        String jarPath = System.getProperty("java.class.path", ".");
        if (jarPath.indexOf(' ') >= 0)
            jarPath = "\"" + jarPath + "\"";
        return jarPath;
    }

    private static int getUserInt(String key, int def) {
        return Preferences.userNodeForPackage(Launcher.class).node(StartupPrefs.USER_NODE).getInt(key, def);
    }

    private static boolean invokeRegression(String[] args) {
        List<String> javaOptions = new ArrayList<String>();
        List<String> electricOptions = new ArrayList<String>();
        electricOptions.add("-debug");
        int regressionPos = 0;
        if (args[0].equals("-threads")) {
            regressionPos = 2;
            electricOptions.add("-threads");
            electricOptions.add(args[1]);
        } else if (args[0].equals("-logging")) {
            regressionPos = 2;
            electricOptions.add("-logging");
            electricOptions.add(args[1]);
        } else if (args[0].equals("-debug")) {
            regressionPos = 1;
            // no need of adding the -debug flag
        }
        String script = args[regressionPos + 1];

        for (int i = regressionPos + 2; i < args.length; i++)
            javaOptions.add(args[i]);
        Process process = null;
        try {
            process = invokePipeserver(javaOptions, electricOptions);
        } catch (java.io.IOException e) {
            logger.error("Failure starting server subprocess", e);
            return false;
        }
        initClasspath(true);
        boolean result = ((Boolean) callByReflection(ClassLoader.getSystemClassLoader(),
                "com.sun.electric.tool.Regression", "runScript", new Class[] { Process.class, String.class },
                null, new Object[] { process, script })).booleanValue();
        return result;
    }

    public static Process invokePipeserver(List<String> javaOptions, List<String> electricOptions)
            throws IOException {
        List<String> procArgs = new ArrayList<String>();
        String program = "java";
        String javaHome = System.getProperty("java.home");
        if (javaHome != null)
            program = javaHome + File.separator + "bin" + File.separator + program;

        procArgs.add(program);
        procArgs.add("-ea");
        procArgs.add("-Djava.util.prefs.PreferencesFactory=com.sun.electric.database.text.EmptyPreferencesFactory");
        procArgs.addAll(javaOptions);
        procArgs.add("-cp");
        procArgs.add(Launcher.getJarLocation());
        procArgs.add("com.sun.electric.Launcher");
        procArgs.addAll(electricOptions);
        procArgs.add("-pipeserver");
        String command = "";
        for (String arg : procArgs)
            command += " " + arg;
        logger.info("exec: {}", command);

        return new ProcessBuilder(procArgs).start();
    }

    private static ClassLoader pluginClassLoader;

    public static Class classFromPlugins(String className) throws ClassNotFoundException {
        return pluginClassLoader.loadClass(className);
    }

    private static void loadAndRunMain(String[] args, boolean loadDependencies) {
        args = getAdditionalFolder(args);

        initClasspath(loadDependencies);
        callByReflection(pluginClassLoader, "com.sun.electric.Main", "main", new Class[] { String[].class },
                null, new Object[] { args });
    }

    private static void initClasspath(boolean loadDependencies) {
        ClassLoader launcherLoader = Launcher.class.getClassLoader();
        pluginClassLoader = launcherLoader;
        if (loadDependencies) {
            initClasspath(readAdditionalFolder(), launcherLoader);
            initClasspath(readMavenDependencies(), launcherLoader);
            // pluginClassLoader = new EClassLoader(pluginJars, appLoader);
        }
        if (enableAssertions) {
            launcherLoader.setDefaultAssertionStatus(true);
            pluginClassLoader.setDefaultAssertionStatus(true);
        }
    }
    
    private static void initClasspath(URL[] urls, ClassLoader launcherLoader) {
        for (URL url : urls) {
            Object result = callByReflection(launcherLoader, "java.net.URLClassLoader", "addURL",
                    new Class[]{URL.class}, launcherLoader, new Object[]{url});
        }
    }

    private static URL[] readAdditionalFolder() {
        List<URL> urls = new ArrayList<URL>();

        File additionalFolder = new File("additional");
        if (additionalFolder.exists() && additionalFolder.isDirectory()) {
            File[] files = additionalFolder.listFiles(new FileFilter() {
                public boolean accept(File pathname) {
                    return pathname.getName().endsWith(".jar");
                }
            });

            for (File file : files) {
                try {
                    urls.add(file.toURI().toURL());
                    logger.info("Add {} to classpath...", file.getAbsoluteFile());
                } catch (MalformedURLException e) {
                    logger.error("Add " + file.getAbsoluteFile() + " to classpath...", e);
                }
            }
        }

        return urls.toArray(new URL[urls.size()]);
    }

    private static URL[] readMavenDependencies() {
        String mavenDependenciesResourceName = "maven.dependencies";
        URL mavenDependencies = ClassLoader.getSystemResource(mavenDependenciesResourceName);
        if (mavenDependencies == null) {
            return new URL[0];
        }

        List<URL> urls = new ArrayList<URL>();
        List<File> repositories = new ArrayList<File>();
        String mavenRepository = ".m2" + File.separator + "repository";

        String homeDirProp = System.getProperty("user.home");
        if (homeDirProp != null) {
            File homeDir = new File(homeDirProp);
            File file = new File(homeDir, mavenRepository);
            if (file.canRead()) {
                repositories.add(file);
                logger.debug("Found repository {}", file);
            } else if (file.exists()) {
                logger.debug("Can't read repository {}", file);
            } else {
                logger.debug("Not found repository {}", file);
            }
        }

        if (mavenDependencies.getProtocol().equals("jar")) {
            String path = mavenDependencies.getPath();
            if (path.startsWith("file:") && path.endsWith("!/" + mavenDependenciesResourceName)) {
                String jarPath = path.substring("file:".length(), path.length() - "!/".length()
                        - mavenDependenciesResourceName.length());
                File jarDir = new File(jarPath).getParentFile();
                File file = new File(jarDir, mavenRepository);
                if (file.canRead()) {
                    repositories.add(file);
                    logger.debug("Found repository {}", file);
                } else if (file.exists()) {
                    logger.debug("Can't read repository {}", file);
                } else {
                    logger.debug("Not found repository {}", file);
                }
            }
        }
        try {
            LineNumberReader in = new LineNumberReader(new InputStreamReader(ClassLoader.getSystemResourceAsStream(mavenDependenciesResourceName)));
            String s;
            while ((s = in.readLine()) != null) {
                String dependency = s.trim();
                if (dependency.isEmpty() || dependency.startsWith("#")) {
                    continue;
                }
                for (File repository : repositories) {
                    File file = new File(repository, dependency);
                    if (file.canRead()) {
                        URL url = file.toURI().toURL();
                        urls.add(url);
                        logger.debug("Add {} to class path", url);
                        break;
                    } else if (file.exists()) {
                        logger.debug("Can't read {}", file);
                    } else {
                        logger.trace("Not found {}", file);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error reading maven dependencies", e);
        }

        return urls.toArray(new URL[urls.size()]);
    }

    private static Object callByReflection(ClassLoader classLoader, String className, String methodName,
            Class[] argTypes, Object obj, Object[] argValues) {
        try {
            Class mainClass = classLoader.loadClass(className);
            Method method = mainClass.getDeclaredMethod(methodName, argTypes);
            method.setAccessible(true);
            return method.invoke(obj, argValues);
        } catch (ClassNotFoundException e) {
            logger.error("Can't invoke Electric", e);
        } catch (NoSuchMethodException e) {
            logger.error("Can't invoke Electric", e);
        } catch (IllegalAccessException e) {
            logger.error("Can't invoke Electric", e);
        } catch (InvocationTargetException e) {
            logger.error("Error in Electric invocation", e.getTargetException());
        }
        return null;
    }

    private static class EClassLoader extends URLClassLoader {
        private EClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("com.sun.electric.plugins.") || name.startsWith("com.sun.electric.scala.")) {
                Class c = findLoadedClass(name);
                if (c == null) {
                    String path = name.replace('.', '/').concat(".class");
                    URL url = getResource(path);
                    if (url != null) {
                        try {
                            DataInputStream in = new DataInputStream(url.openStream());
                            int len = in.available();
                            byte[] ba = new byte[len];
                            in.readFully(ba);
                            in.close();
                            c = defineClass(name, ba, 0, len);
                        } catch (IOException e) {
                            throw new ClassNotFoundException(name);
                        }
                    } else {
                        throw new ClassNotFoundException(name);
                    }
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
            return super.loadClass(name, resolve);
        }
    }
}
