/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: WizardField.java
 *
 * Copyright (c) 2008, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.tecEditWizard2;

import java.util.List;
import java.util.ArrayList;

/**
 * This class defines a valueField value in the Technology Creation Wizard.
 */
class WizardFieldBasic
{
    public double value;
    public String rule;
}

public class WizardField extends WizardFieldBasic
{
    public String name; // mostly layer name

    public WizardField(String n) { name = n; }
    public WizardField() { rule = ""; }
    public WizardField(double v, String r) { value = v; rule = r; }
}

// Special class to contain both X and Y values of a rule
class WizardRuleFields
{
    WizardField xValue, yValue;
    String name; // variable name

    WizardRuleFields(String n)
    {
        name = n;
        xValue = new WizardField();
        yValue = new WizardField();
    }
}

/**
 * This class is to handle wide spacing rules
 */
class WideWizardField extends WizardFieldBasic
{
    public double maxW;
    public double minLen;
    public List<String> names;

    public WideWizardField()
    {
        names = new ArrayList<String>();
    }
}
