/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: EvalJavaBsh.java
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
package com.sun.electric.tool.lang;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.variable.CodeExpression;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.Resources;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.ui.WindowFrame;

/**
 * Used for evaluating Java expressions in Variables
 * It is meant to be invoked from the Variable context;
 * these methods should not be used from other contexts, and
 * thus are declared protected.
 * <P>
 * This class is thread-safe, but be warned: if multiple threads are hammering
 * the bean shell for evaluations, it will slow down a lot due to
 * contested locks.
 *
 * @author  gainsley
 */
public class EvalJavaBsh {

    // ------------------------ private data ------------------------------------
    /** The bean shell interpreter evaluation method */
    private static Method evalMethod;
    /** The bean shell interpreter source method */
    private static Method sourceMethod;
    /** The bean shell interpreter set method */
    private static Method setMethod;
    /** The bean shell interpreter set method */
    private static Method getMethod;
    /** The bean shell TargetError getTarget method */
    private static Method getTargetMethod;
    /** true if reflection has already been done to find the Bean Shell */
    private static boolean beanShellChecked = false;
    /** The bean shell interpreter class */
    private static Class<?> interpreterClass = null;
    /** The bean shell TargetError class */
    private static Class<?> targetErrorClass;
    /** The bean shell EvalError class */
    private static Class<?> evalErrorClass;
    /** For replacing @variable */
    private static final Pattern atPat = Pattern.compile("@(\\w+)");
    /** For replacing @variable */
    private static final Pattern pPat = Pattern.compile("(P|PAR)\\(\"(\\w+)\"\\)");
    /** Results of replacing */
    private static HashMap<String, String> replaceHash = new HashMap<String, String>();
    /** The bean shell interpreter object */
    private Object envObject;
    /** Context stack for recursive evaluation calls */
    private Stack<VarContext> contextStack = new Stack<VarContext>();
    /** Info stack for recursive evaluation calls */
    private Stack<Object> infoStack = new Stack<Object>();
    /** the singleton object of this class. */
    public static final EvalJavaBsh evalJavaBsh = new EvalJavaBsh();
    /** turn on Bsh verbose DEBUG statements */
    private static boolean DEBUG = false;
    /** turn on stack trace statements for exceptions */
    private static boolean DEBUGSTACKTRACE = false;

    // ------------------------ private and protected methods -------------------
    /** the constructor */
    public EvalJavaBsh() {
        envObject = null;

        if (!hasBeanShell()) return;

        // create the BSH object
        try {
            envObject = interpreterClass.newInstance();
        } catch (Exception e) {
            System.out.println("Can't create an instance of the Bean Shell: " + e.getMessage());
            envObject = null;
            return;
        }

        setVariable("evalJavaBsh", this);
        setVariable("launchingThread", Thread.currentThread());

        try {
            doEval("Object P(String par) { return evalJavaBsh.P(par); }");
            doEval("Object PAR(String par) { return evalJavaBsh.PAR(par); }");

            // the following is for running scripts
            doEval("import com.sun.electric.tool.user.menus.MenuCommands;");
            doEval("import com.sun.electric.database.hierarchy.*;");
            doEval("import com.sun.electric.database.prototype.*;");
            doEval("import com.sun.electric.database.topology.*;");
            doEval("import com.sun.electric.database.variable.ElectricObject;");
            doEval("import com.sun.electric.database.variable.FlagSet;");
            doEval("import com.sun.electric.database.variable.TextDescriptor;");
            doEval("import com.sun.electric.database.variable.VarContext;");
            doEval("import com.sun.electric.database.variable.Variable;");

            // do not import variable.EvalJavaBsh, because calling EvalJavaBsh.runScript
            // will spawn jobs in an unexpected order
            doEval("import com.sun.electric.tool.io.*;");
        } catch (VarContext.EvalException e) {
            e.printStackTrace(System.out);
        }
    }

    /** Get the interpreter so other tools may add methods to it. There is only
     * one interpreter, so be careful that separate tools do not conflict in
     * terms of namespace.  I recommend when adding objects or methods to the
     * Interpreter you prepend the object or method names with the Tool name.
     */
//	  public static Interpreter getInterpreter() { return env; }
    /**
     * See what the current context of evaluation is.
     * @return a VarContext.
     */
    public synchronized VarContext getCurrentContext() {
        return contextStack.peek();
    }

    /**
     * See what the current info of evaluation is.
     * @return an Object.
     */
    public synchronized Object getCurrentInfo() {
        return infoStack.peek();
    }

    /**
     * Replaces @var calls to P("var")
     * Replaces P("var") calls to P("ATTR_var")
     * Replaces PAR("var") calls to PAR("ATTR_var")
     * @param expr the expression
     * @return replaced expression
     */
    public static String replace(String expr) {
        String result = replaceHash.get(expr);
        if (result != null) {
            return result;
        }
        StringBuffer sb = new StringBuffer();
        Matcher atMat = atPat.matcher(expr);
        while (atMat.find()) {
            atMat.appendReplacement(sb, "P(\"" + atMat.group(1) + "\")");
        }
        atMat.appendTail(sb);

        result = sb.toString();
        sb = new StringBuffer();
        Matcher pMat = pPat.matcher(result);
        while (pMat.find()) {
            if (pMat.group(2).startsWith("ATTR_")) {
                pMat.appendReplacement(sb, pMat.group(0));
            } else {
                pMat.appendReplacement(sb, pMat.group(1) + "(\"ATTR_" + pMat.group(2) + "\")");
            }
        }
        pMat.appendTail(sb);

        result = sb.toString();
        if (result.equals(expr)) {
            result = expr;
        }
        replaceHash.put(expr, result);
        return result;
    }

    /** Evaluate Object as if it were a String containing java code.
     * Note that this function may call itself recursively.
     * @param ce the CodeExpression to be evaluates.
     * @param context the context in which the object will be evaluated.
     * @param info used to pass additional info from Electric to the interpreter, if needed.
     * @return the evaluated object.
     */
    public synchronized Object evalVarObject(CodeExpression ce, VarContext context, Object info) throws VarContext.EvalException {
        assert ce.isJava();
        String expr = replace(ce.getExpr());  // change @var calls to P(var)
        if (context == null) {
            context = VarContext.globalContext;
        }
        // check for infinite recursion
        for (int i = 0; i < contextStack.size(); i++) {
            VarContext vc = contextStack.get(i);
            Object inf = infoStack.get(i);
            if ((vc == context) && (inf == info)) {
                throw new VarContext.EvalException("JavaBeanShell Eval recursion error");
            }
        }
        contextStack.push(context);             // push context
        infoStack.push(info);                   // push info
        Object ret;
        try {
            ret = doEval(expr);              // ask bsh to evaluate
        } catch (VarContext.EvalException e) {
            // we need to catch, pop off stacks, and re-throw to maintain
            // proper state of stacks.
            contextStack.pop();
            infoStack.pop();
            throw e;
        }
        contextStack.pop();                     // pop context
        infoStack.pop();                        // pop info
        if (DEBUG) {
            System.out.println("BSH: " + expr.toString() + " --> " + ret);
        }
//        if (ret instanceof Number) {
//            // get rid of lots of decimal places on floats and doubles
//            ret = Variable.format((Number)ret, 3);
//        }
        return ret;
    }

    //------------------Methods that may be called through Interpreter--------------
    /**
     * Method to lookup a variable for evaluation.
     * Finds that variable 1 level up the hierarchy.
     * @param name the name of the variable to find.
     * @return the value of the variable (null if not found).
     * @throws VarContext.EvalException
     */
    public synchronized Object P(String name) throws VarContext.EvalException {
        VarContext context = contextStack.peek();
        Object val = context.lookupVarEval(name);
        if (DEBUG) {
            System.out.println(name + " ---> " + val + " (" + val.getClass() + ")");
        }
        return val;
    }

    /**
     * Method to lookup a variable for evaluation.
     * Finds that variable anywhere up the hierarchy.
     * @param name the name of the variable to find.
     * @return the value of the variable (null if not found).
     * @throws VarContext.EvalException
     */
    public synchronized Object PAR(String name) throws VarContext.EvalException {
        throw new VarContext.EvalException("The PAR() function has been disabled because "
                + "it confounds the techniques used in Topology.java for detecting when Cell "
                + "instances have the same transistor sizes. RKao");
//        VarContext context = (VarContext)contextStack.peek();
//        Object val = context.lookupVarFarEval(name);
//        if (DEBUG) System.out.println(name + " ---> " + val + " ("+val.getClass()+")");
//        return val;
    }

    //---------------------------Running Scripts-------------------------------------
    /** Run a Java Bean Shell script */
    public static void runScript(String script) {
        runScriptJob job = new runScriptJob(script);
        job.startJob();
    }

    /**
     * returns EditingPreferences with default sizes and text descriptors
     * @return EditingPreferences with default sizes and text descriptors
     */
    public static EditingPreferences getEditingPreferences() {
        return Job.getRunningJob().getEditingPreferences();
    }

    /**
     * Display specified Cell after termination of currently running script
     * @param cell
     */
    public static void displayCell(Cell cell) {
        Job curJob = Job.getRunningJob();
        if (curJob instanceof runScriptJob) {
            ((runScriptJob) curJob).displayCell(cell);
        }
    }

    /**
     * Method to return the highlighted NodeInsts and ArcInsts to the currently running BSH script.
     */
    public static List<Geometric> getHighlighted()
    {
        Job curJob = Job.getRunningJob();
        if (curJob instanceof runScriptJob)
            return ((runScriptJob)curJob).highlightedEObjs;
        return null;
    }

    /** Run a Java Bean Shell script */
    public static Job runScriptJob(String script) {
        return new runScriptJob(script);
    }

    @SuppressWarnings("serial")
	private static class runScriptJob extends Job {

        private String script;
        private Cell cell;
        private List<Geometric> highlightedEObjs;

        protected runScriptJob(String script) {
            super("JavaBsh script: " + script, User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
            this.script = script;

            // cache highlighted objects
            highlightedEObjs = null;
        	WindowFrame wf = WindowFrame.getCurrentWindowFrame(false);
        	if (wf != null)
        	{
        		Highlighter highlighter = wf.getContent().getHighlighter();
        		if (highlighter != null)
        			highlightedEObjs = highlighter.getHighlightedEObjs(true, true);
        	}
        }

        public boolean doIt() throws JobException {

        	EvalJavaBsh evaluator = new EvalJavaBsh();
            evaluator.doSource(script); // May throw exception
            return true;
        }

        private void displayCell(Cell cell) {
            if (this.cell != null) {
                return;
            }
            this.cell = cell;
            fieldVariableChanged("cell");
        }

        @Override
        public void terminateOK() {
            if (cell != null) {
                Job.getUserInterface().displayCell(cell);
            }
        }
    }

    // ****************************** REFLECTION FOR ACCESSING THE BEAN SHELL ******************************
    
    public static boolean hasBeanShell() {
        if (!beanShellChecked) {
        	beanShellChecked = true;

            // find the BSH classes
            try {
                interpreterClass = Class.forName("bsh.Interpreter");
                targetErrorClass = Class.forName("bsh.TargetError");
                evalErrorClass = Class.forName("bsh.EvalError");
            } catch (ClassNotFoundException e) {
            	TextUtils.recordMissingComponent("Bean Shell");
                interpreterClass = null;
                return false;
            }

            // find the necessary methods on the BSH class
            try {
                evalMethod = interpreterClass.getMethod("eval", new Class[]{String.class});
                sourceMethod = interpreterClass.getMethod("source", new Class[]{String.class});
                setMethod = interpreterClass.getMethod("set", new Class[]{String.class, Object.class});
                getMethod = interpreterClass.getMethod("get", new Class[]{String.class});
                getTargetMethod = targetErrorClass.getMethod("getTarget", (Class[]) null);
            } catch (NoSuchMethodException e) {
                System.out.println("Can't find methods in the Bean Shell: " + e.getMessage());
                interpreterClass = null;
                return false;
            }
        }

        // if already initialized, return state
        return interpreterClass != null;
    }
    
//    private void initBSH() {
//        // if already initialized, return
//        if (interpreterClass != null) {
//            return;
//        }
//
//        // find the BSH classes
//        try {
//            interpreterClass = Class.forName("bsh.Interpreter");
//            targetErrorClass = Class.forName("bsh.TargetError");
//            evalErrorClass = Class.forName("bsh.EvalError");
//        } catch (ClassNotFoundException e) {
//        	TextUtils.recordMissingComponent("Bean Shell");
//            interpreterClass = null;
//            return;
//        }
//
//        // find the necessary methods on the BSH class
//        try {
//            evalMethod = interpreterClass.getMethod("eval", new Class[]{String.class});
//            sourceMethod = interpreterClass.getMethod("source", new Class[]{String.class});
//            setMethod = interpreterClass.getMethod("set", new Class[]{String.class, Object.class});
//            getMethod = interpreterClass.getMethod("get", new Class[]{String.class});
//            getTargetMethod = targetErrorClass.getMethod("getTarget", (Class[]) null);
//        } catch (NoSuchMethodException e) {
//            System.out.println("Can't find methods in the Bean Shell: " + e.getMessage());
//            interpreterClass = null;
//            return;
//        }
//    }

    /**
     * Set a variable in the Java Bean Shell
     * @param name the name of the variable
     * @param value the value to set the variable to
     */
    public void setVariable(String name, Object value) {
        try {
            if (envObject != null) {
                setMethod.invoke(envObject, new Object[]{name, value});
            }
        } catch (Exception e) {
            handleInvokeException(e, "Bean shell error setting " + name + " to " + value + ": ");
        }
    }

    public Object getVariable(String name) {
        try {
            if (envObject != null) {
                return getMethod.invoke(envObject, new Object[]{name});
            }
        } catch (Exception e) {
            handleInvokeException(e, "Bean shell error getting variable " + name);
        }
        return null;
    }

    // -------------------------- Private Methods -----------------------------
    /**
     * Evaluate a string containing Java Bean Shell code.
     * @param line the string to evaluate
     * @return an object representing the evaluated string, or null on error.
     * @throws VarContext.EvalException exception
     */
    private Object doEval(String line) throws VarContext.EvalException {
        Object returnVal = null;
        try {
            if (envObject != null) {
                returnVal = evalMethod.invoke(envObject, new Object[]{line});
            }
        } catch (Exception e) {
            if (e instanceof InvocationTargetException) {
                // re-throw original EvalException, if any
                VarContext.EvalException ee = getEvalException((InvocationTargetException) e);
                if (ee != null) {
                    throw ee;
                }
            }
            if (!handleInvokeException(e, "Bean shell error evaluating " + line)) {
                throw new VarContext.EvalException(e.getMessage(), e);
            }
        }
        return returnVal;
    }

    public Object doEvalLine(String line) {
        Object obj = null;
        try {
            obj = doEval(line);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return obj;
    }

    /**
     * Method to determine whether a string is valid Java code.
     * @param line the string to test.
     * @return true if the string is valid, evaluatable Java code.
     */
    public boolean isValidJava(String line) {
        try {
            if (envObject != null) {
                evalMethod.invoke(envObject, new Object[]{line});
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    /**
     * Execute a Java Bean Shell script file.
     * @param file the file to run.
     */
    public void doSource(String file) throws JobException {
        try {
            if (envObject != null) {
                sourceMethod.invoke(envObject, new Object[]{file});
            }
        } catch (Exception e) {
            String description = "Java Bean shell error sourcing '" + file + "'";
            if (e instanceof InvocationTargetException) {
                // This wraps an exception thrown by the method invoked.
                Throwable t = e.getCause();
                if (t != null) {
                    handleBshError((Exception) t, description);
                }
                if (evalErrorClass.isInstance(t)) {
                    // The Bean Shell had an error
                    t = doGetTarget(t);
                }
                throw new JobException(t);
            } else if (e instanceof IllegalArgumentException) {
                System.out.println(description + ": " + e.getMessage());
                if (DEBUG) {
                    e.printStackTrace(System.out);
                }
                throw new JobException(e);
            } else if (e instanceof IllegalAccessException) {
                System.out.println(description + ": " + e.getMessage());
                if (DEBUG) {
                    e.printStackTrace(System.out);
                }
                throw new JobException(e);
            } else {
                System.out.println("Unhandled Exception: ");
                System.out.println(description + ": " + e.getMessage());
                e.printStackTrace(System.out);
                throw new JobException(e);
            }
        }
    }

    private static Throwable doGetTarget(Object ex) {
        Throwable returnVal = null;
        if (interpreterClass != null && targetErrorClass.isInstance(ex)) {
            try {
                returnVal = (Throwable) getTargetMethod.invoke(ex, (Object[]) null);
            } catch (Exception e) {
                handleInvokeException(e, "Java Bean shell error getting exception target");
            }
        }
        return returnVal;
    }

    /**
     * If the InvocationTargetException was generated because of an EvalException,
     * get the EvalException that is wrapped by the target exception.
     * @param e the invocation target exception
     * @return the initial evaluation exception, or null if none.
     */
    private VarContext.EvalException getEvalException(InvocationTargetException e) {
        Throwable t = e.getCause();
        if (t == null) {
            return null;
        }
        Throwable tt = doGetTarget(t);
        if (tt == null) {
            return null;
        }
        if (tt instanceof VarContext.EvalException) {
            return (VarContext.EvalException) tt;
        }
        return null;
    }

    /**
     * Handle exceptions thrown by attempting to invoke a reflected method or constructor.
     * @param e The exception thrown by the invoked method or constructor.
     * @param description a description of the event to be printed with the error message.
     * @return true if exception handled (error message printed), false if not
     */
    private static boolean handleInvokeException(Exception e, String description) {

        boolean handled = false;
        if (e instanceof InvocationTargetException) {
            // This wraps an exception thrown by the method invoked.
            Throwable t = e.getCause();
            if (t != null) {
                handled = handleBshError((Exception) t, description);
            }
        } else if (e instanceof IllegalArgumentException) {
            System.out.println(description + ": " + e.getMessage());
            if (DEBUG) {
                e.printStackTrace(System.out);
            }
        } else if (e instanceof IllegalAccessException) {
            System.out.println(description + ": " + e.getMessage());
            if (DEBUG) {
                e.printStackTrace(System.out);
            }
        } else {
            System.out.println("Unhandled Exception: ");
            System.out.println(description + ": " + e.getMessage());
            e.printStackTrace(System.out);
            handled = false;
        }
        return handled;
    }

    /**
     * Handle Bean Shell evaluation errors.  Sends it to system.out.
     * @param e the TargetError exception thrown.
     * @param description a description of the event that caused the error to be thrown.
     */
    private static boolean handleBshError(Exception e, String description) {
        if (targetErrorClass.isInstance(e)) {
            // The Bean Shell had an error
            Throwable t = doGetTarget(e);
            if (t != null) {
                if (t instanceof VarContext.EvalException) {
                    if (DEBUG) {
                        System.out.println("Java EvalException: " + description + ": " + t.getMessage());
                        if (DEBUGSTACKTRACE) {
                            e.printStackTrace(System.out);
                        }
                    }
                } else {
                    if (t.getMessage() != null) {
                        System.out.println(description + ": " + t.getMessage());
                    } else if (t.getStackTrace() != null) {
                        System.out.println(description + ": ");
                        t.printStackTrace(System.out);
                    } else {
                        System.out.println(description + ": " + t);
                    }
                    if (DEBUGSTACKTRACE) {
                        e.printStackTrace(System.out);
                    }
                }
            }
        } else {
            System.out.println("Unhandled Java Bsh Exception: " + description + ":\n " + e.getMessage());
            if (DEBUGSTACKTRACE) {
                e.printStackTrace(System.out);
            }
            return false;
        }
        return true;
    }
}
