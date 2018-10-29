---------------- This is Electric, Version 9.07 ----------------


Electric is written in the Java programming language and is distributed in a
single ".jar" file.  There are two variations on the ".jar" file:
  With source code (called "electric-X.XX.jar")
  Without source code (called, "electricBinary-X.XX.jar").
Both of these files have the binary ".class" files needed to run Electric,
but the one with source-code is larger because it also has all of the Java code.
Latest source code can be downloaded from Electric Home Page:
http://savannah.gnu.org/projects/electric .

---------------- Requirements:

Electric requires OpenJDK, Apache Harmony, or Oracle Java version 1.8.
It is developed with Oracle Java, so if you run into problems with
other versions, try installing Java 1.8 or later from Oracle.

---------------- Running:

Running Electric varies with the different platforms.  Most systems allow you
to double-click on the .jar file. 

If double-clicking doesn't work, try running it from the command-line by typing: 
     java -jar electric.jar

An alternate command-line is: 
     java -classpath electric.jar com.sun.electric.Launcher

---------------- Adding Plug-Ins:

Electric plug-ins are additional pieces of code that can be downloaded separately
to enhance the system's functionality.  Currently, these plug-ins are available:
 
> Static Free Software extras
  This includes the IRSIM simulator and interfaces for 3D Animation.
  The IRSIM simulator is a gate-level simulator from Stanford University. Although
  originally written in C, it was translated to Java so that it could plug into
  Electric.  The Static Free Software extras is available from Static Free Software at:
    www.staticfreesoft.com/electricSFS-X.XX.jar

> Java
  The Bean Shell is used to do scripting and parameter evaluation in Electric.  Advanced
  operations that make use of cell parameters will need this plug-in.  The Bean Shell is
  available from:
    www.beanshell.org

> Python
  Jython is used to do scripting in Electric.  Jython is available from:
    www.jython.org
  Build the "standalone" installation to get the JAR file.

> 3D
  The 3D facility lets you view an integrated circuit in three-dimensions. It requires
  the Java3D package, which is available from the Java Community Site, www.j3d.org.
  This is not a plugin, but rather an enhancement to your Java installation. 

> Animation
  Another extra that can be added to the 3D facility is 3D animation.  This requires
  the Java Media Framework (JMF) and extra animation code.  The Java Media Framework is
  available from Oracle (this is not a plugin: it is an enhancement to your Java installation).

> Russian User's Manual
  An earlier version of the user's manual (8.02) has been translated into Russian.
  This manual is available from Static Free Software at:
    www.staticfreesoft.com/electricRussianManual-X.XX.jar

To attach a plugin, it must be in the CLASSPATH.  The simplest way to do that is to
invoked Electric from the command line, and specify the classpath.  For example, to
add the beanshell (a file named "bsh-2.0b1.jar"), type: 
    java -classpath electric.jar:bsh-2.0b1.jar com.sun.electric.Launcher

On Windows, you must use the ";" to separate jar files, and you might also have to
quote the collection since ";" separates commands:
    java -classpath "electric.jar;bsh-2.0b1.jar" com.sun.electric.Launcher

Note that you must explicitly mention the main Electric class (com.sun.electric.Launcher)
when using plug-ins since all of the jar files are grouped together as the "classpath".

---------------- Building from Sources:

Extract the source ".jar" file.  It will contain the subdirectory "com" with all
source code.  The file "build.xml" has the Ant scripts for compiling this code.

When rebuilding Electric, there are some Macintosh vs. non-Macintosh issues to consider:

> Build on a Macintosh
  The easiest thing to do is to remove references to "AppleJavaExtensions.jar"
  from the Ant script (build.xml).  This package is a collection of "stubs" to
  replace Macintosh functions that are unavailable elsewhere.  You can also build
  a native "App" by running the "mac-app" Ant script.  This script makes use of files
  in the "packaging" folder.  Macintosh computers must be running OS 10.3 or later. 

> Build on non-Macintosh
  If you are building Electric on and for a non-Macintosh platform, remove references
  to "AppleJavaExtensions.jar" from the Ant script (build.xml).  Also, remove the module
  "com.sun.electric.MacOSXInterface.java".  It is sufficient to delete this module,
  because Electric automatically detects its presence and is able to run without it.

> Build on non-Macintosh, to run on all platforms
  To build Electric so that it can run on all platforms, Macintosh and other, you will
  need to keep the module "com.sun.electric.MacOSXInterface.java".  However, in order
  to build it, you will need the stub package "AppleJavaExtensions.jar".  The package
  can be downloaded from Apple at
    http://developer.apple.com/samplecode/AppleJavaExtensions/index.html.

---------------- Building from Sources hosted on savannah.gnu.org in NetBeans IDE

1) Start NetBeans 7.0 or later.
2) Install the Team Server Plugin:
   2.1) Use Tools / Plugins and choose the "Available Plugins" tab in the Plugins manager.
   2.2) In the left pane, check the "Team Server" plugin and click "Install".
   2.3) Click "Close" to exit the Plugins manager.
   2.4) Use Window / Services to open the "Services" tab
   2.5) Expand the "Team Server" node and check that the "savannah.gnu.org" Team Server is listed.
3) Download Electric Sources from savannah.gnu.org .
   3.1) Choose File / Open Team Project... from the main menu.
   3.2) Search for electric project
   3.3) Select "Electric: VLSI Design System" and click Open From Team Server
   3.4) Expand "Electric: VLSI Design System" node in the Team tab
   3.5) Expand Sources subnode
   3.6) Click "Source Code Repository (get)"
   3.6) Either enter "Folder To Get" in "Get Sources From Team Server" dialog or click "Browse" button near it.
        The "Folder to Get" of Electric-X.XX is "tags/electric-X.XX" .
        The "Folder to Get" of latest Electric sources is "trunk/electric" .
   3.7) Choose "Local Folder" in "Get Sources From Team Server" where to download Electric Sources.
        The default is "~/NetBeansProjects/electric~svn".
   3.8) Click "Get From Team Server"
   3.9) The "Checkout Completed" dialog will say that project "electric" was checkout.
        It will suggest you to open a project.
   3.10) Click "Open Project..."
   3.11) Choose "electric" and click "Open".
4) Build Electric
   4.1) Right-click "electric" node in "Projects" tab.
   4.2) Choose "Build".
   Electric project is large. If build hangs then it may be necessary to add "-J-Xmx2g" to netbeans_default_options
   in file <NETBEANS_INSTALLATION>/etc/netbeans.conf . 
5) Run Electric.
   5.1) Choose either "Run > Run Project (electric)" or "Debug > Debug Project (electric)" from the main menu.
6) Create electric distribution for your organization (optional).
   6.1) Right-click at the Electric project icon. Choose "Properties|Configuration|release-profile"
   6.2) Choose "Build|Clean and build main project".
   6.3) Copy ~/NetBeansProjects/electric~svn/electric/target/electric-9.04-a-with-dependencies.jar to a shared location
        in your file system.

---------------- Building from latest Sources in command-line:

1) Check that these tools are installed on your computer:
   JDK 1.8 or later
   Subversion
   Apache Ant version 1.8.0 or later (http://ant.apache.org)

   The following variable should be defined in your environment:
      JAVA_PATH - path to the JDK root directory

2) Obtain the latest sources using Subversion
   a) For the first time
      cd WORK-DIRECTORY
      svn checkout http://svn.savannah.gnu.org/svn/electric
      cd electric
   b) Next time
      cd WORK-DIRECTORY/electric
      svn update

3) Compile sources
   cd packaging
   ant

4) Run the Electric
   java -jar WORK-DIRECTORY/electric/packaging/electricPublic-X.XX.jar

   You might execute Electric with larger heap size if your design is large.
      java -Xmx2g -jar WORK-DIRECTORY/electric/packaging/electricPublic-X.XX.jar

---------------- Discussion:

There are three mailing lists devoted to Electric:

> google groups "electricvlsi"
  View at: http://groups.google.com/group/electricvlsi

> bug-gnu-electric
  Subscribe at http://mail.gnu.org/mailman/listinfo/bug-gnu-electric

> discuss-gnu-electric
  Subscribe at http://mail.gnu.org/mailman/listinfo/discuss-gnu-electric

In addition, you can send mail to:
    info@staticfreesoft.com
