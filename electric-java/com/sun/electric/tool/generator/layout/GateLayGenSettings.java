package com.sun.electric.tool.generator.layout;

import com.sun.electric.database.text.Setting;
import com.sun.electric.tool.Tool;
import com.sun.electric.tool.ToolSettings;

/**
 * Constraints project preferences for the gate layout generator
 */
public class GateLayGenSettings extends Tool {
	public static GateLayGenSettings tool = new GateLayGenSettings();

    /**
	 * The constructor sets up the DRC tool.
	 */
	private GateLayGenSettings()
	{
		super("GateLayoutGenerator", "GateLayoutGenerator");
	}

    public static Setting getFoundrySetting() { return ToolSettings.getFoundrySetting(); }
    public static Setting getEnableNCCSetting() {return ToolSettings.getEnableNCCSetting();}
    public static Setting getSizeQuantizationErrorSetting() { return ToolSettings.getSizeQuantizationErrorSetting(); }
    public static Setting getMaxMosWidthSetting() {return ToolSettings.getMaxMosWidthSetting();}
    public static Setting getVddYSetting() {return ToolSettings.getVddYSetting();}
    public static Setting getGndYSetting() {return ToolSettings.getGndYSetting();}
    public static Setting getNmosWellHeightSetting() {return ToolSettings.getNmosWellHeightSetting();}
    public static Setting getPmosWellHeightSetting() {return ToolSettings.getPmosWellHeightSetting();}
    public static Setting getSimpleNameSetting() {return ToolSettings.getSimpleNameSetting();}
}
