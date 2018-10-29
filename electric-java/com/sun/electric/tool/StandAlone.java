/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: StandAlone.java
 *
 * Copyright (c) 2014, Static Free Software. All rights reserved.
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
package com.sun.electric.tool;

import com.sun.electric.Main;
import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.Environment;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.id.IdManager;
import com.sun.electric.database.network.NetworkTool;
import com.sun.electric.database.text.Pref;
import com.sun.electric.database.text.Setting;
import com.sun.electric.database.text.Version;
import com.sun.electric.technology.TechFactory;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.tool.user.User;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * Support for stand alone use of Electric tools (without Gui and Job system
 */
public class StandAlone
{
    /**
     * This is initializatio of Electric static fields.
     * It can't be undone.
     */
    public static void initPrefs(Map<String,Object> initPrefs) {
        // Don't use java preferences
        Pref.forbidPreferences();
        // init necessary tools
        initTools();
        // No more preferences
        Pref.lockCreation(initPrefs);
        // Restricted user interface
        Job.setUserInterface(new StandAloneUserInterface());
    }
    
    /**
     * Start Electric database
     * @param settingChanges Settings changes
     */
    public static void startElectric(Map<String, Object> settingChanges) {
        String[] techNames = {"artwork", "schematic"};
        startElectric(techNames, settingChanges);
    }
    
    /**
     * Start Electric database
     * @param techNames necessary technologies from TechFactory (excluding Generic)
     * @param settingChanges Settings changes
     */
    public static void startElectric(String[] techNames, Map<String, Object> settingChanges) {
    	System.out.println("Electric Version: " + Version.getVersion());
    	
        EDatabase database = new EDatabase(IdManager.stdIdManager.getInitialSnapshot(), "serverDB");
        EDatabase.setServerDatabase(database);
        database.lock(true);
        // init technologies
        database.setToolSettings((Setting.RootGroup) ToolSettings.getToolSettings(""));
        assert database.getGeneric() == null;
        Generic generic = Generic.newInstance(database.getIdManager());
        database.addTech(generic);
        Map<String, TechFactory> techFactories = TechFactory.getKnownTechs();
        Map<TechFactory.Param, Object> paramValues = Collections.emptyMap();
        for (String techName : techNames) {
            database.addTech(techFactories.get(techName).newInstance(generic, paramValues));
        }
        // allow database changes
        database.lowLevelBeginChanging(null);
        // initialize settings
        Setting.SettingChangeBatch changeBatch = new Setting.SettingChangeBatch();
        for (Map.Entry<Setting, Object> e : database.getSettings().entrySet()) {
            Setting setting = e.getKey();
            Object value = e.getValue();
            if (settingChanges.containsKey(setting.getXmlPath())) {
                assert value.getClass() == setting.getFactoryValue().getClass();
                changeBatch.add(setting, settingChanges.get(setting.getXmlPath()));
            }
        }
        database.implementSettingChanges(changeBatch);
        Environment.setThreadEnvironment(database.getEnvironment());
        EditingPreferences.lowLevelSetThreadLocalEditingPreferences(new EditingPreferences(true, database.getTechPool()));
    }

    /**
     * Close Electric Database
     */
    public static void closeElectric() {
        EditingPreferences.lowLevelSetThreadLocalEditingPreferences(null);
        Environment.setThreadEnvironment(null);
        EDatabase.setServerDatabase(null);
    }

    private static void initTools() {
        User.getUserTool();
        IOTool.getIOTool().init();
        NetworkTool.getNetworkTool();
    }
    private static class StandAloneUserInterface extends Main.UserInterfaceDummy
    {
        @Override
        public EDatabase getDatabase()
        {
            return EDatabase.serverDatabase();
        }

        @Override
        public void termLogging(final ErrorLogger logger, boolean explain, boolean terminate)
        {
            System.out.println(logger.getInfo());
            for (Iterator<ErrorLogger.MessageLog> it = logger.getLogs(); it.hasNext();)
            {
                System.out.println(it.next().getMessage());
            }
        }
    }
}
