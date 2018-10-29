/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TechFactory.java
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
package com.sun.electric.technology;

import com.sun.electric.Main;
import com.sun.electric.database.id.IdManager;
import com.sun.electric.database.id.IdReader;
import com.sun.electric.database.id.IdWriter;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.user.ActivityLogger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public abstract class TechFactory {

    final String techName;
    private final List<Param> techParams;
    private static List<String> sensitiveTechNames = new ArrayList<String>();

    public static class Param {

        public final String xmlPath;
        public final String prefPath;
        public final Object factoryValue;

        public Param(String xmlPath, String prefPath, Object factoryValue) {
            this.xmlPath = xmlPath;
            this.prefPath = prefPath;
            this.factoryValue = factoryValue;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Param && xmlPath.equals(((Param) o).xmlPath);
        }

        @Override
        public int hashCode() {
            return xmlPath.hashCode();
        }

        @Override
        public String toString() {
            return xmlPath;
        }
    }

    public static TechFactory fromXml(URL url, Xml.Technology xmlTech) {
        String techName = null;
        if (xmlTech != null) {
            techName = xmlTech.techName;
        }
        if (techName == null) {
            techName = TextUtils.getFileNameWithoutExtension(url);
        }
        return new FromXml(techName, true, url, xmlTech);
    }

    public Technology newInstance(Generic generic) {
        return newInstance(generic, Collections.<Param, Object>emptyMap());
    }

    public Technology newInstance(Generic generic, Map<Param, Object> paramValues) {
        try {
            Map<Param, Object> fixedParamValues = new HashMap<Param, Object>();
            for (Param param : techParams) {
                Object value = paramValues.get(param);
                if (value == null || value.getClass() != param.factoryValue.getClass()) {
                    value = param.factoryValue;
                }
                fixedParamValues.put(param, value);
            }
            Technology tech = newInstanceImpl(generic, fixedParamValues);

            // make sure the name is unique
//            Technology already = Technology.findTechnology(tech.getTechName());
//            if (already != null)
//            {
//            	System.out.println("ERROR: Multiple technologies named '" + tech.getTechName() + "'");
//            	return null;
//            }

            if (tech != null) tech.setup();
            return tech;
        } catch (ClassNotFoundException e) {
            TextUtils.recordMissingTechnology("Extra");
        } catch (Exception e) {
            System.out.println("ERROR while loading technology " + this.techName + ": " + ((e != null) ? e.getMessage() : "Assertion"));
            if (Job.getDebug())
            	ActivityLogger.logException(e);
        }
        return null;
    }

    public List<Param> getTechParams() {
        return techParams;
    }

    /**
     * Method to return a list of technology names that are IP sensitive,
     * and are therefore NOT expected to be in the public version of Electric.
     * @return a list of technology names that are IP sensitive.
     */
    public static List<String> getSensitiveTechNames() { return sensitiveTechNames; }

    abstract String getDescription();

    void write(IdWriter writer) throws IOException {
        writer.writeString(techName);
        writer.writeBoolean(false);
    }

    @Override
    public String toString() {
        return techName;
    }

    public static TechFactory getGenericFactory() {
        return new FromClass("generic", "com.sun.electric.technology.technologies.Generic");
    }

    public static Map<String, TechFactory> getKnownTechs() {
        LinkedHashMap<String, TechFactory> m = new LinkedHashMap<String, TechFactory>();
        c(m, "artwork", "com.sun.electric.technology.technologies.Artwork");
        c(m, "fpga", "com.sun.electric.technology.technologies.FPGA");
        c(m, "schematic", "com.sun.electric.technology.technologies.Schematics");
        r(m, "bicmos", "technology/technologies/bicmos.xml", false);
        r(m, "bipolar", "technology/technologies/bipolar.xml", false);
        r(m, "cmos", "technology/technologies/cmos.xml", false);
        r(m, "efido", "technology/technologies/efido.xml", false);
        c(m, "gem", "com.sun.electric.technology.technologies.GEM");
        r(m, "pcb", "technology/technologies/pcb.xml", false);
        r(m, "rcmos", "technology/technologies/rcmos.xml", false);
        p(m, "mocmos", "com.sun.electric.technology.technologies.MoCMOS", false);
        r(m, "mocmosold", "technology/technologies/mocmosold.xml", false);
        r(m, "mocmossub", "technology/technologies/mocmossub.xml", false);
        r(m, "mocmos-cn", "technology/technologies/mocmos-cn.xml", false);
        r(m, "nmos", "technology/technologies/nmos.xml", false);
        p(m, "photonics", "com.sun.electric.technology.technologies.photonics.Photonics", false);
        r(m, "tft", "technology/technologies/tft.xml", false);
        p(m, "tsmc180", "com.sun.electric.plugins.tsmc.TSMC180", true);
        p(m, "cmos90", "com.sun.electric.plugins.tsmc.CMOS90", true);
//        r(m, "CLN40G", "plugins/tsmc/CLN40G.xml", true);
        r(m, "tsmcSun40GP", "plugins/tsmc/tsmcSun40GP.xml", true);
        r(m, "tsmcCLN40G", "plugins/tsmc/tsmcCLN40G.xml", true);
        return Collections.unmodifiableMap(m);
    }

    public static TechFactory getTechFactory(String techName) {
        return getKnownTechs().get(techName);
    }

    TechFactory(String techName) {
        this(techName, Collections.<Param>emptyList());
    }

    TechFactory(String techName, List<Param> techParams) {
        this.techName = techName;
        this.techParams = Collections.unmodifiableList(new ArrayList<Param>(techParams));
    }

    @SuppressWarnings("unchecked")
    private static void p(Map<String, TechFactory> m, String techName, String techClassName, boolean restricted) {
        TechFactory techFactory;
        List<Param> params;
        try {
            Class<?> techClass = Class.forName(techClassName);
            Method getTechParamsMethod = techClass.getMethod("getTechParams");
            params = (List<Param>) getTechParamsMethod.invoke(null);
            techFactory = new FromParamClass(techName, techClass, params);
            if (restricted) sensitiveTechNames.add(techName);
        } catch (Exception e) {
            TextUtils.recordMissingTechnology(techName);
            return;
        }
        m.put(techName, techFactory);
    }

    private static void c(Map<String, TechFactory> m, String techName, String techClassName) {
        m.put(techName, new FromClass(techName, techClassName));
    }

    private static void r(Map<String, TechFactory> m, String techName, String resourceName, boolean restricted) {
        assert techName != null;
        URL url = Main.class.getResource(resourceName);
        if (url == null) {
            return;
        }
        m.put(techName, new FromXml(techName, false, url, null));
        if (restricted) sensitiveTechNames.add(techName);
    }

    abstract Technology newInstanceImpl(Generic generic, Map<Param, Object> paramValues) throws Exception;

    public Xml.Technology getXml(final Map<Param, Object> params) throws Exception {
        return getXml(params, null);
    }

    public abstract Xml.Technology getXml(final Map<Param, Object> params, Map<Object, Map<String, Object>> additionalAttributes) throws Exception;

    static TechFactory read(IdReader reader) throws IOException {
        String techName = reader.readString();
        boolean userDefined = reader.readBoolean();
        if (!userDefined) {
            return getKnownTechs().get(techName);
        }
        boolean hasUrl = reader.readBoolean();
        URL xmlUrl = hasUrl ? new URL(reader.readString()) : null;
        byte[] serializedXml = reader.readBytes();
        try {
            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(serializedXml));
            Xml.Technology xmlTech = (Xml.Technology) in.readObject();
            in.close();
            return fromXml(xmlUrl, xmlTech);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    private static class FromClass extends TechFactory {

        private final String techClassName;

        private FromClass(String techName, String techClassName) {
            super(techName, Collections.<Param>emptyList());
            this.techClassName = techClassName;
        }

        @Override
        Technology newInstanceImpl(Generic generic, Map<Param, Object> paramValues) throws Exception {
            assert paramValues.isEmpty();
            Class<?> techClass = Class.forName(techClassName);
            return (Technology) techClass.getConstructor(Generic.class, TechFactory.class).newInstance(generic, this);
        }

        @Override
        public Xml.Technology getXml(final Map<Param, Object> params, Map<Object, Map<String, Object>> additionalAttributes) throws Exception {
            IdManager idManager = new IdManager();
            Generic generic = Generic.newInstance(idManager);
            Technology tech = newInstance(generic, params);
            return tech.makeXml(additionalAttributes);
        }

        @Override
        String getDescription() {
            return "from " + techClassName;
        }
    }

    private static class FromParamClass extends TechFactory {
//        private final Class<?> techClass;

        private final Method getPatchedXmlMethod;
        Constructor<?> techConstructor;

        private FromParamClass(String techName, Class<?> techClass, List<Param> techParams) throws Exception {
            super(techName, techParams);
//            this.techClass = techClass;
            getPatchedXmlMethod = techClass.getMethod("getPatchedXml", Map.class);
            techConstructor = techClass.getConstructor(Generic.class, TechFactory.class, Map.class, Xml.Technology.class);
        }

        @Override
        Technology newInstanceImpl(Generic generic, Map<Param, Object> paramValues) throws Exception {
        	Xml.Technology t = getXml(paramValues);
        	if (t == null) return null;
            return (Technology) techConstructor.newInstance(generic, this, paramValues, t);
        }

        @Override
        public Xml.Technology getXml(final Map<Param, Object> params, Map<Object, Map<String, Object>> additionalAttributes) throws Exception {
            return (Xml.Technology) getPatchedXmlMethod.invoke(null, params);
        }

        @Override
        String getDescription() {
            return "from " + getPatchedXmlMethod.getName();
        }
    }

    private static class FromXml extends TechFactory {

        private final boolean userDefined;
        private final URL urlXml;
        private Xml.Technology xmlTech;
        private boolean xmlParsed;

        private FromXml(String techName, boolean userDefined, URL urlXml, Xml.Technology xmlTech) {
            super(techName);
            this.userDefined = userDefined;
            this.urlXml = urlXml;
            this.xmlTech = xmlTech;
        }

        @Override
        Technology newInstanceImpl(Generic generic, Map<Param, Object> paramValues) throws Exception {
            assert paramValues.isEmpty();
            Xml.Technology xml = getXml(paramValues);
            if (xml == null) {
                return null;
            }
            Class<?> techClass = Technology.class;
            if (xml.className != null) {
                techClass = Class.forName(xml.className);
            }
            return (Technology) techClass.getConstructor(Generic.class, TechFactory.class, Map.class, Xml.Technology.class).newInstance(generic, this, Collections.emptyMap(), xml);
        }

        @Override
        String getDescription() {
            return (urlXml == null) ? "technology description" : ("from " + urlXml.getFile());
        }

        @Override
        public Xml.Technology getXml(final Map<Param, Object> paramValues, Map<Object, Map<String, Object>> additionalAttributes) throws Exception {
            assert paramValues.isEmpty();
            if (xmlTech == null && !xmlParsed) {
                xmlTech = Xml.parseTechnology(urlXml);
                xmlParsed = true;
                if (xmlTech == null) {
                    throw new Exception("Can't load extra technology: " + urlXml);
                }
                String xmlTechName = xmlTech.techName;
                if (!xmlTechName.equals(techName)) {
                    xmlTech = null;
                    throw new Exception("Tech name " + xmlTechName + " doesn't match " + techName + " in file:" + urlXml);
                }
            }
            return xmlTech;
        }

        void write(IdWriter writer) throws IOException {
            writer.writeString(techName);
            writer.writeBoolean(userDefined);
            if (!userDefined) {
                return;
            }
            boolean hasUrl = urlXml != null;
            writer.writeBoolean(hasUrl);
            if (hasUrl) {
                writer.writeString(urlXml.toString());
            }
            byte[] serializedXml;
            try {
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(byteStream);
                out.writeObject(xmlTech);
                out.flush();
                serializedXml = byteStream.toByteArray();
            } catch (Throwable e) {
                e.printStackTrace();
                serializedXml = new byte[0];
            }
            writer.writeBytes(serializedXml);
        }
    }
}
