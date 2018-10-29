/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: XMLRules.java
 * Written by Gilda Garreton.
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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

import com.sun.electric.database.topology.Geometric;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.MutableDouble;

import java.util.*;
import java.io.Serializable;

public class XMLRules implements Serializable
{

	/** Hash map to store rules per matrix index */                     public List<HashMap<XMLRules.XMLRule, XMLRules.XMLRule>> matrix;
    /** Layers with spacing rules to speed up the process */            public HashMap<Layer, Set<Layer>> layersWithRules;
    /** To remeber the technology */                                    private Technology tech;

    public XMLRules (Technology t)
    {
        this.tech = t;
        int numLayers = tech.getNumLayers();
        int uTSize = (numLayers * numLayers + numLayers) / 2 + numLayers + tech.getNumNodes();

        matrix = new ArrayList<>(uTSize);
        while (matrix.size() < uTSize)
        {
            matrix.add(null);
        }
        this.layersWithRules = new HashMap<>();
    }

    /**
     * Method to determine the technology associated with this rules set.
     * @return the technology associated with this rules set.
     */
    public Technology getTechnology() { return tech; }

    /**
	 * Method to determine the index in the upper-left triangle array for two layers/nodes. In this type of rules,
     * the index starts after primitive nodes and single layers rules.
     * The sequence of indices is: rules for single layers, rules for nodes, rules that
     * involve more than 1 layers.
	 * @param index1 the first layer/node index.
	 * @param index2 the second layer/node index.
	 * @return the index in the array that corresponds to these two layers/nodes.
	 */
    public int getRuleIndex(int index1, int index2)
	{
        int index = tech.getRuleIndex(index1, index2);
        return index;
    }

     /**
      * Method to find the edge spacing rule between two layer.
      * @param layer1 the first layer.
      * @param layer2 the second layer.
      * @return the edge rule distance between the layers.
      * Returns null if there is no edge spacing rule.
      */
    public DRCTemplate getEdgeRule(Layer layer1, Layer layer2)
    {
        return null;
    }

    /**
     * Method to tell UI if multiple wide rules are allowed
     * @param index
     * @return true if multiple wide riles are allowed.
     */
    public boolean doesAllowMultipleWideRules(int index) { return true; }

	/**
     * Method to get total number of rules stored
     * @return total number of rules
	 */
	public int getNumberOfRules()
	{
        // This function is better not to be cached
        int numberOfRules = 0;

        for (HashMap<XMLRules.XMLRule, XMLRules.XMLRule> map : matrix)
        {
            if (map != null) numberOfRules++;
        }
		return numberOfRules;
	}

    /**
     * To retrieve those nodes whose have rules
     * @return Array of Strings
     */
    public String[] getNodesWithRules()
    {
        String[] nodesWithRules = new String[tech.getNumNodes()];
        int j = 0;
        for(Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext(); )
		{
			PrimitiveNode np = it.next();
			nodesWithRules[j++] = np.getName();
		}
        return nodesWithRules;
    }

    /**
     * Method to retrieve different spacing rules depending on spacingCase.
     * Type can be SPACING (normal values), SPACINGW (wide values),
     * SPACINGE (edge values) and CUTSPA (multi cuts)
     * @param index
     * @param spacingCase
     * @param wideRules
     * @return list of rules subdivided in UCONSPA and CONSPA
     */
    public List<DRCTemplate> getSpacingRules(int index, DRCTemplate.DRCRuleType spacingCase, boolean wideRules)
    {
        List<DRCTemplate> list = new ArrayList<>(2);

        switch (spacingCase)
        {
            case SPACING: // normal rules
            {
                double maxLimit = 0;
                int multi = -1;

                if (wideRules)
                {
                    multi = 0;
                    maxLimit = Double.MAX_VALUE;
                }

                list = getRuleForRange(index, DRCTemplate.DRCRuleType.CONSPA, -1, multi, maxLimit, list);
                list = getRuleForRange(index, DRCTemplate.DRCRuleType.UCONSPA, -1, multi, maxLimit, list);
            }
            break;
            case UCONSPA2D: // multi contact rule
            {
//		        list = getRuleForRange(index, DRCTemplate.DRCRuleType.CONSPA, 1, -1, 0, list);
		        list = getRuleForRange(index, DRCTemplate.DRCRuleType.UCONSPA2D, 1, -1, 0, list);
            }
            break;
            default:
        }
        return (list);
    }

    /**
     *
     * @param index
     * @param newRules
     * @param spacingCase SPACING for normal case, SPACINGW for wide case, CUTSPA for multi cuts
     * @param wideRules
     */
    public void setSpacingRules(int index, List<DRCTemplate> newRules, DRCTemplate.DRCRuleType spacingCase, boolean wideRules)
    {
        List<DRCTemplate> list = new ArrayList<>(0);
        HashMap<XMLRules.XMLRule,XMLRules.XMLRule> map = matrix.get(index);

        // delete old rules
        for (DRCTemplate rule : newRules)
        {
            // Invalid data
            if (rule.getValue(0) <= 0 || rule.ruleName == null || rule.ruleName.equals("")) continue;
            // first remove any possible rule in the map
            switch (spacingCase)
            {
                case SPACING: // normal rules
                {
                    double maxLimit = 0;
                    int multi = -1;

                    if (wideRules) // wide rules
                    {
                        multi = 0;
                        maxLimit = Double.MAX_VALUE;
                    }

                    list = getRuleForRange(index, rule.ruleType, -1, multi, maxLimit, list);
                }
                break;
                case UCONSPA2D: // multi contact rule
                {
                    list = getRuleForRange(index, rule.ruleType, 1, -1, 0, list);
                }
                break;
                default:
            }

            // No the most efficient algorithm
            for (DRCTemplate tmp : list)
                map.remove(tmp);
        }
        for (DRCTemplate rule : newRules)
        {
            // Invalid data
            if (rule.getValue(0) <= 0 || rule.ruleName == null || rule.ruleName.equals("")) continue;

            addRule(index, rule);
        }
    }

     /**
     * Method to retrieve simple layer or node rules
     * @param index the index of the layer or node
     * @param type the rule type.
     * @return the requested rule.
     */
     public XMLRule getRule(int index, DRCTemplate.DRCRuleType type)
     {
         return (getRule(index, type, 0, 0, -1, null, null));
     }

    /**
     * Method to retrieve specific SURROUND rules stored per node that involve two layers
     * @param index the combined index of the two layers involved
     * @param type
     * @param nodeName list containing the name of the primitive
     * @return DRCTemplate containing the rule
     */
    public XMLRule getRule(int index, DRCTemplate.DRCRuleType type, String nodeName)
    {
        List<String> nameList = new ArrayList<>(1);
        nameList.add(nodeName);
        return (getRule(index, type, 0, 0, -1, nameList, null));
    }

    /**
	 * Method to get the minimum <type> rule for a Layer
	 * where <type> is the rule type. E.g. MinWidth or Area
	 * @param layer the Layer to examine.
	 * @param type rule type
	 * @return the minimum width rule for the layer.
	 * Returns null if there is no minimum width rule.
	 */
    public DRCTemplate getMinValue(Layer layer, DRCTemplate.DRCRuleType type)
	{
        int index = layer.getIndex();
        if (index < 0) return null;
		return (getRule(index, type));
	}

    /**
     * Method to set the minimum <type> rule for a Layer
     * where <type> is the rule type. E.g. MinWidth or Area
     * @param layer the Layer to examine.
     * @param name the rule name
     * @param value the new rule value
     * @param type rule type
     */
    public void setMinValue(Layer layer, String name, double value, DRCTemplate.DRCRuleType type)
    {
        int index = layer.getIndex();
        if (value <= 0)
        {
            System.out.println("Error: zero value in XMLRules:setMinValue");
            return;
        }
        XMLRule oldRule = getRule(index, type);
        HashMap<XMLRules.XMLRule,XMLRules.XMLRule> map = matrix.get(index);

        // Remove old rule first but only if exists
        if (map != null)
            map.remove(oldRule);
        XMLRule r = new XMLRule(name, new double[]{value}, type, 0, 0, -1, DRCTemplate.DRCMode.ALL.mode());
        addXMLRule(index, r);
    }

    /**
     * Method similar to getRuleForRange() but only search based on range of widths
     * @param index
     * @param type
     * @param multiCut  -1 if don't care, 0 if no cuts, 1 if cuts
     * @param minW
     * @param maxW
     * @param list
     * @return List of DRC rules
     */
    private List<DRCTemplate> getRuleForRange(int index, DRCTemplate.DRCRuleType type, int multiCut, double minW, double maxW,
                                              List<DRCTemplate> list)
    {
        HashMap<XMLRules.XMLRule,XMLRules.XMLRule> map = matrix.get(index);

        if (list == null) list = new ArrayList<>(2);
        if (map == null) return (list);

        for (XMLRule rule : map.values())
        {
            if (rule.ruleType == type)
            {
                // discard rules that are not valid for this particular tech mode (ST or TSMC)
//                if (rule.when != DRCTemplate.DRCMode.ALL.mode() && (rule.when&techMode) != techMode)
//                    continue;
                if (rule.multiCuts != -1 && rule.multiCuts != multiCut)
	                continue; // Cuts don't match
                if (rule.maxWidth <= maxW && rule.maxWidth > minW)
                    list.add(rule);
            }
        }
        return (list);
     }

     /**
     * Method to retrieve a rule based on type and max wide size
     * (valid for metal only)
     */
    private XMLRule getRule(int index, DRCTemplate.DRCRuleType type, double wideS, double length, int multiCut,
                            List<String> possibleNodeNames, List<String> layerNamesInOrder)
    {
        HashMap<XMLRules.XMLRule,XMLRules.XMLRule> map = matrix.get(index);
        if (map == null) return (null);

        XMLRule maxR = null;
        boolean searchFor = (wideS > 0);

        for (XMLRule rule : map.values())
        {
            if (rule.ruleType == type)
            {
                // Needs multiCut values
                if (rule.multiCuts != -1 && rule.multiCuts != multiCut)
	                continue; // Cuts don't match
                // in case of spacing rules, we might need to match special names
                if (rule.nodeName != null && possibleNodeNames != null && !possibleNodeNames.contains(rule.nodeName))
                    continue; // No combination found
                // names in order do not match
                if (layerNamesInOrder != null && (!rule.name1.equals(layerNamesInOrder.get(0)) || !rule.name2.equals(layerNamesInOrder.get(1))))
                    continue;
                // First found is valid
//                if (!searchFor) return (rule);
                if (!searchFor && (maxR == null || maxR.getValue(0) > rule.getValue(0) || maxR.getValue(1) > rule.getValue(1)))
                {
                    maxR = rule;
                }
                else if (rule.maxWidth < wideS && rule.minLength <= length &&
                        (maxR == null || (maxR.maxWidth < rule.maxWidth && maxR.minLength <= rule.minLength)))
                {
                    maxR = rule;
                }
            }
        }
        return (maxR);
     }

    /**
     * To set wide limit for old techs
     * @param values wide limits
     */
    public void setWideLimits(double[] values)
    {
        System.out.println("Review XMLRules::setWideLimits");

        for (HashMap<XMLRules.XMLRule,XMLRules.XMLRule> map : matrix)
        {
            if (map == null) continue;

            for (XMLRule rule : map.values())
            {
                if (rule.maxWidth > 0 && rule.maxWidth != values[0])
                    rule.maxWidth = values[0];
            }
        }
    }

    private void addXMLRule(int index, XMLRule rule)
    {
        HashMap<XMLRules.XMLRule,XMLRules.XMLRule> map = matrix.get(index);
        if (map == null)
        {
            map = new HashMap<>();
            matrix.set(index, map);
        }

       map.put(rule, rule);
    }

    /**
     * Method to delete a given spacing rule
     * @param index
     * @param rule
     */
    public void deleteRule(int index, DRCTemplate rule)
    {
        HashMap<XMLRules.XMLRule,XMLRules.XMLRule> map = matrix.get(index);
        if (map == null) return; // no rule found

        for (XMLRule r : map.values())
        {
            if (r.ruleType == rule.ruleType && r.maxWidth == rule.maxWidth &&
                r.minLength == rule.minLength && r.multiCuts == rule.multiCuts &&
                r.ruleName.equals(rule.ruleName))
            {
                // found element to delete
                map.remove(r);
                return;
            }
        }
    }

    /* OLD FUNCTION*/
    public void addRule(int index, DRCTemplate rule, DRCTemplate.DRCRuleType spacingCase, boolean wideRules)
    {
        throw new Error("Not implemented");
    }

    /**
	 * Method to add a rule based on template
	 * @param index
     * @param rule
     */
	public void addRule(int index, DRCTemplate rule)
	{
		DRCTemplate.DRCRuleType internalType = rule.ruleType;
        List<Layer> list = null;

        // This is only required for this type of rule
        if (rule.ruleType == DRCTemplate.DRCRuleType.EXTENSION || rule.ruleType == DRCTemplate.DRCRuleType.EXTENSIONGATE)
        {
            list = new ArrayList<>(2);
            list.add(tech.findLayer(rule.name1));
            list.add(tech.findLayer(rule.name2));
        }

		switch (rule.ruleType)
		{
			case SPACING:
//		    case SPACINGW:
				internalType = DRCTemplate.DRCRuleType.UCONSPA;
                XMLRule r = new XMLRule(rule);
                r.ruleType = DRCTemplate.DRCRuleType.CONSPA;
                addXMLRule(index, r);
                break;
        }
        XMLRule r = new XMLRule(rule);
        r.ruleType = internalType;
        addXMLRule(index, r);
//        addRule(index, rule.ruleName, rule.value1, internalType, rule.maxWidth, rule.minLength, rule.multiCuts, rule.when, list, rule.nodeName);
	}

    /**
     * Method to add relationship between layers with spacing rules.
     * @param lay1 first layer
     * @param lay2 second layer
     */
    public void addRelationship(Layer lay1, Layer lay2)
    {
        assert(lay1 != null && lay2 != null);

        Set<Layer> l = layersWithRules.get(lay1);
        // not found
        if (l == null)
        {
            l = new HashSet<>();
            layersWithRules.put(lay1, l);
        }
        l.add(lay2);
    }

     /**
      * Method to return min value rule depending on type and wire length
      */
    private boolean getMinRule(int index, DRCTemplate.DRCRuleType type, double maxW, MutableDouble maxValue)
    {
        HashMap<XMLRules.XMLRule, XMLRules.XMLRule> map = matrix.get(index);
        boolean found = false;
        if (map == null) return found;
        maxValue.setValue(0.0); // get the largest value among the valid ones. It doesn't select the first
        // found only

        for (XMLRule rule : map.values())
        {
            if (rule.ruleType != type)
                continue;
            if (rule.maxWidth > maxW)
                continue;
            for (int i = 0; i < rule.getNumValues(); i++)
            {
	            double val = rule.getValue(i);
	            if (maxValue.doubleValue() < val)
	            {
	                maxValue.setValue(val);
	                found = true;
	//            if (rule.ruleType == type && rule.maxWidth <= maxW)
	//                return (rule.getValue(0));
	            }
            }
        }
        return (found);
    }

	/**
	 * Method to find the spacing rule between two layer.
	 * @param layer1 the first layer.
     * @param geo1 the first geometric
     * @param layer2 the second layer.
     * @param geo2 the second geometric
     * @param connected true to find the distance when the layers are connected.
     * @param multiCut true to find the distance when this is part of a multicut contact.
     * @param wideS widest polygon
     * @param length length of the intersection
     * @return the spacing rule between the layers.
     * Returns null if there is no spacing rule.
	 */
	public DRCTemplate getSpacingRule(Layer layer1, Geometric geo1,
                                      Layer layer2, Geometric geo2, boolean connected,
                                      int multiCut, double wideS, double length)
	{
		int pIndex = getRuleIndex(layer1.getIndex(), layer2.getIndex());
		DRCTemplate.DRCRuleType type = (connected) ? DRCTemplate.DRCRuleType.CONSPA : DRCTemplate.DRCRuleType.UCONSPA;

        // Composing possible name if
        List<String> list = new ArrayList<>(2);
        //String n1 = null, n2 = null;
        if (geo1 != null) list.add(DRCTemplate.getSpacingCombinedName(layer1, geo1)); // n1 =
        if (geo2 != null) list.add(DRCTemplate.getSpacingCombinedName(layer2, geo2)); // n2 =
        //list.add(n1); list.add(n2);

		XMLRule r = getRule(pIndex, type, wideS, length, multiCut, list, null);

        // Search for surrounding conditions not attached to nodes
//        if (r == null)
//        {
//            r = getRule(pIndex, DRCTemplate.DRCRuleType.SURROUND, wideS, length, multiCut, null, null);
//            if (r != null && r.nodeName != null) r = null; // only spacing rule if not associated to primitive nodes.
//        }

        return (r);
	}

    /**
     * Method to find all rules of specified type associated to Layer layer1
     * @param layer1 layer
     * @param type rule type
     * @return all rules of specified type associated to Layer layer1
     */
    public List<DRCTemplate> getRules(Layer layer1, DRCTemplate.DRCRuleType type)
    {
        List<DRCTemplate> tempList = new ArrayList<>();
        int layerIndex = layer1.getIndex();
        if (layerIndex < 0) return tempList;
        HashMap<XMLRules.XMLRule, XMLRules.XMLRule> map = matrix.get(layerIndex);
        if (map == null) return tempList;

        for (XMLRule rule : map.values())
        {
            if (rule.ruleType == type)
                tempList.add(rule);
        }

//        List<DRCTemplate> tempList = new ArrayList<DRCTemplate>();
//        int layerIndex = layer1.getIndex();
//		int tot = tech.getNumLayers();
//        List<String> list = new ArrayList<String>(2);
//
//        for(int i=0; i<tot; i++)
//		{
//			int pIndex = getRuleIndex(layerIndex, i);
//            list.clear();
//            list.add(tech.getLayer(i).getName());  // layer1 must be the second name
//            list.add(layer1.getName());
//            DRCTemplate temp = getRule(pIndex, type, 0, 0, -1, null, list);
//            if (temp != null)
//                tempList.add(temp);
//        }

		return tempList;
    }

    /**
	 * Method to find the extension rule between two layer.
	 * @param layer1 the first layer.
     * @param layer2 the second layer.
     * @param isGateExtension to decide between the rule EXTENSIONGATE or EXTENSION
     * @return the extension rule between the layers.
     * Returns null if there is no extension rule.
	 */
	public DRCTemplate getExtensionRule(Layer layer1, Layer layer2, boolean isGateExtension)
	{
		int pIndex = getRuleIndex(layer1.getIndex(), layer2.getIndex());
        List<String> list = new ArrayList<String>(2);
        list.add(layer1.getName());
        list.add(layer2.getName());
        DRCTemplate.DRCRuleType rule = (isGateExtension) ? DRCTemplate.DRCRuleType.EXTENSIONGATE : DRCTemplate.DRCRuleType.EXTENSION;
        return (getRule(pIndex, rule, 0, 0, -1, null, list));
	}

    /**
     * Method to tell whether there are any design rules between two layers.
     * @param layer1 the first Layer to check.
     * @param layer2 the second Layer to check.
     * @return true if there are design rules between the layers.
     */
    public boolean isAnySpacingRule(Layer layer1, Layer layer2)
    {
        int pIndex = getRuleIndex(layer1.getIndex(), layer2.getIndex());
        HashMap<XMLRules.XMLRule, XMLRules.XMLRule> map = matrix.get(pIndex);
        if (map == null) return false;
        for (XMLRule rule : map.values())
        {
            if (rule.isSpacingRule()) return true;
        }
        return false;
    }

    /**
     * Method to determine if given node is not allowed by foundry
     * @param nodeIndex index of node in DRC rules map to examine
     * @param type rule type
     * @return the rule if this is a forbidden node otherwise returns null.
     */
    public DRCTemplate isForbiddenNode(int nodeIndex, DRCTemplate.DRCRuleType type)
    {
        HashMap<XMLRules.XMLRule, XMLRules.XMLRule> map = matrix.get(nodeIndex);
        if (map == null) return (null);

        for (XMLRule rule : map.values())
        {
            if (rule.ruleType == type)
            {
                // discard rules that are not valid for this particular tech mode (ST or TSMC)
//                if (rule.when != DRCTemplate.DRCMode.ALL.mode() && (rule.when&techMode) != techMode)
//                    continue;
                return rule; // found
            }
        }
        // nothing found
        return null;
    }

    /**
	 * Method to find the worst spacing distance in the design rules.
	 * Finds the largest spacing rule in the Technology.
     * @param lastMetal last metal to check if only metal values are requested
     * @param worstDistance the largest spacing distance in the Technology. Zero if nothing found
	 * @return true if a value was found
     */
    public boolean getWorstSpacingDistance(int lastMetal, MutableDouble worstDistance)
	{
        boolean worstDistanceFound = false;
        worstDistance.setValue(0);
        MutableDouble mutableDist = new MutableDouble(0);

        if (lastMetal != -1)
        {
            int numM = tech.getNumMetals();
            assert(numM >= lastMetal);
            numM = lastMetal;
            List<Layer> layers = new ArrayList<>(numM);
            for (Iterator<Layer> itL = tech.getLayers(); itL.hasNext();)
            {
                Layer l = itL.next();
                if (l.getFunction().isMetal())
                    layers.add(l);
            }
            for (int i = 0; i < numM; i++)
            {
                Layer l1 = layers.get(i);
                for (int j = i; j < numM; j++) // starts from i so metal1-metal2(default one) can be checked
                {
                    int index = getRuleIndex(l1.getIndex(), layers.get(j).getIndex());
                    boolean found = getMinRule(index, DRCTemplate.DRCRuleType.UCONSPA, Double.MAX_VALUE, mutableDist);
                    double worstValue = mutableDist.doubleValue();
                    if (found && worstValue > worstDistance.doubleValue())
                    {
                        worstDistance.setValue(worstValue);
                        worstDistanceFound = true;
                    }
                }
            }
        }
        else
        {
            for(int i = 0; i < matrix.size(); i++)
            {
                boolean found = getMinRule(i, DRCTemplate.DRCRuleType.UCONSPA, Double.MAX_VALUE, mutableDist);
                double worstValue = mutableDist.doubleValue();
                if (found && worstValue > worstDistance.doubleValue())
                {
                    worstDistance.setValue(worstValue);
                    worstDistanceFound = true;
                }
            }
        }
        return worstDistanceFound;
	}

    /**
	 * Method to find the worst spacing distance in the design rules.
	 * Finds the largest spacing rule in the Technology.
     * @param layers layers to search
     * @param worstDistance the largest spacing distance in the Technology. Zero if nothing found
	 * @return true if a value was found
     */
    public boolean getWorstSpacingDistance(Set<Layer> layers, MutableDouble worstDistance)
	{
        boolean worstDistanceFound = false;
        worstDistance.setValue(0);
        MutableDouble worstValue = new MutableDouble(0);

        for (Layer l : layers)
        {
            boolean found = getMaxSurround(l, 0, worstValue);
            if (found && worstValue.doubleValue() > worstDistance.doubleValue())
            {
                worstDistance.setValue(worstValue.doubleValue());
                worstDistanceFound = true;
            }
        }

        return worstDistanceFound;
	}

    /**
     * Fast method to know if a layer has any DRC rule associated with it
     * @param layer Layer object to analyze
     * @return True if there is at least one DRC rule
     */
    public boolean hasLayerRules(Layer layer)
    {
        return layersWithRules.get(layer) != null;
    }

    /**
	 * Method to find the maximum design-rule distance around a layer.
	 * @param layer the Layer to examine.
     * @param maxSize the maximum design-rule distance around the layer.
     * @param worstLayerRule -1 if nothing found.
	 * @return true if a value was found
	 */
	public boolean getMaxSurround(Layer layer, double maxSize, MutableDouble worstLayerRule)
	{
        worstLayerRule.setValue(-1);
        boolean worstValueFound = false;
        int layerIndex = layer.getIndex();
		int tot = tech.getNumLayers();

        // Need of marking layers which have actually spacing rules!
        MutableDouble mutableDist = new MutableDouble(-1);

        Set<Layer> set = layersWithRules.get(layer);
        // Check if there is any spacing rule for this layer
        if (set == null)
            return worstValueFound;
        // get the real list of layers
        for (Layer l : set)
        {
			int pIndex = getRuleIndex(layerIndex, l.getIndex());
			// Better to use Double.MAX_VALUE rather than maxSize
            boolean found = getMinRule(pIndex, DRCTemplate.DRCRuleType.UCONSPA, Double.MAX_VALUE, mutableDist);
            double worstValue = mutableDist.doubleValue();
            if (found && worstValue > worstLayerRule.doubleValue())
            {
                worstLayerRule.setValue(worstValue);
                worstValueFound = true;
            }
        }
        return worstValueFound;
	}

    /**
	 * Method to apply overrides to a set of rules.
	 * @param override the override string.
	 * @param tech the Technology in which these rules live.
	 */
	public void applyDRCOverrides(String override, Technology tech)
	{
       // if (Main.getDebug()) System.out.println("Check this function"); @TODO GVG cmos90:applyDRCOverrides
        //@TODO check DRCCheckMode.ALL

		int pos = 0;
		int len = override.length();
		while (pos < len)
		{
			int startKey = pos;
			int endKey = override.indexOf(':', startKey);
			if (endKey < 0) break;
			String key = override.substring(startKey, endKey);
			if (key.equals("c") || key.equals("cr") || key.equals("u") || key.equals("ur") ||
				key.equals("cw") || key.equals("cwr") || key.equals("uw") || key.equals("uwr") ||
				key.equals("cm") || key.equals("cmr") || key.equals("um") || key.equals("umr") ||
				key.equals("e") || key.equals("er"))
			{
				startKey = endKey + 1;
				Layer layer1 = Technology.getLayerFromOverride(override, startKey, '/', tech);
				if (layer1 == null) break;
				startKey = override.indexOf('/', startKey);
				if (startKey < 0) break;
				Layer layer2 = Technology.getLayerFromOverride(override, startKey+1, '=', tech);
				if (layer2 == null) break;
				startKey = override.indexOf('=', startKey);
				if (startKey < 0) break;
				endKey = override.indexOf(';', startKey);
				if (endKey < 0) break;
				String newValue = override.substring(startKey+1, endKey);
				int index = getRuleIndex(layer1.getIndex(), layer2.getIndex());
				if (key.equals("c"))
				{
                    XMLRule rule = getRule(index,  DRCTemplate.DRCRuleType.CONSPA);
                    if (rule != null) rule.setValue(0, TextUtils.atof(newValue));
				} else if (key.equals("cr"))
				{
                    XMLRule rule = getRule(index,  DRCTemplate.DRCRuleType.CONSPA);
                    if (rule != null) rule.ruleName = newValue;
				} else if (key.equals("u"))
				{
                    XMLRule rule = getRule(index,  DRCTemplate.DRCRuleType.UCONSPA);
                    if (rule != null) rule.setValue(0, TextUtils.atof(newValue));
				} else if (key.equals("ur"))
				{
                    XMLRule rule = getRule(index,  DRCTemplate.DRCRuleType.UCONSPA);
                    if (rule != null) rule.ruleName = newValue;
				} else if (key.equals("cw"))
				{
					//conListWide[index] = new Double(TextUtils.atof(newValue));
                    System.out.println("Not implemented in XMLRules:applyDRCOverrides");
				} else if (key.equals("cwr"))
				{
					//conListWideRules[index] = newValue;
                    System.out.println("Not implemented in XMLRules:applyDRCOverrides");
				} else if (key.equals("uw"))
				{
					//unConListWide[index] = new Double(TextUtils.atof(newValue));
                    System.out.println("Not implemented in XMLRules:applyDRCOverrides");
				} else if (key.equals("uwr"))
				{
					//unConListWideRules[index] = newValue;
                    System.out.println("Not implemented in XMLRules:applyDRCOverrides");
				} else if (key.equals("cm"))
				{
					//conListMulti[index] = new Double(TextUtils.atof(newValue));
                    System.out.println("Not implemented in XMLRules:applyDRCOverrides");
				} else if (key.equals("cmr"))
				{
					//conListMultiRules[index] = newValue;
                    System.out.println("Not implemented in XMLRules:applyDRCOverrides");
				} else if (key.equals("um"))
				{
					//unConListMulti[index] = new Double(TextUtils.atof(newValue));
                    System.out.println("Not implemented in XMLRules:applyDRCOverrides");
				} else if (key.equals("umr"))
				{
					//unConListMultiRules[index] = newValue;
                    System.out.println("Not implemented in XMLRules:applyDRCOverrides");
				} else if (key.equals("e"))
				{
					//edgeList[index] = new Double(TextUtils.atof(newValue));
                    System.out.println("Not implemented in XMLRules:applyDRCOverrides");
				} else if (key.equals("er"))
				{
					//edgeListRules[index] = newValue;
                    System.out.println("Not implemented in XMLRules:applyDRCOverrides");
				}
				pos = endKey + 1;
				continue;
			}
			if (key.equals("m") || key.equals("mr"))
			{
				startKey = endKey + 1;
				Layer layer = Technology.getLayerFromOverride(override, startKey, '=', tech);
				if (layer == null) break;
				startKey = override.indexOf('=', startKey);
				if (startKey < 0) break;
				endKey = override.indexOf(';', startKey);
				if (endKey < 0) break;
				String newValue = override.substring(startKey+1, endKey);
				int index = layer.getIndex();
				if (key.equals("m"))
				{
                    XMLRule rule = getRule(index,  DRCTemplate.DRCRuleType.MINWID);
                    if (rule != null) rule.setValue(0, TextUtils.atof(newValue));
				} else if (key.equals("mr"))
				{
                    XMLRule rule = getRule(index,  DRCTemplate.DRCRuleType.MINWID);
                    if (rule != null) rule.ruleName = newValue;
				}
				pos = endKey + 1;
				continue;
			}
			if (key.equals("n") || key.equals("nr"))
			{
				startKey = endKey + 1;
				int endPos = override.indexOf('=', startKey);
				if (endPos < 0) break;
				String nodeName = override.substring(startKey, endPos);
				PrimitiveNode np = tech.findNodeProto(nodeName);
				if (np == null) break;
//				int index = 0;
				for(Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext(); )
				{
					PrimitiveNode oNp = it.next();
					if (oNp == np) break;
//					index++;
				}
				if (key.equals("n"))
				{
					startKey = override.indexOf('=', startKey);
					if (startKey < 0) break;
					endKey = override.indexOf('/', startKey);
					if (endKey < 0) break;
//					String newValue1 = override.substring(startKey+1, endKey);
					int otherEndKey = override.indexOf(';', startKey);
					if (otherEndKey < 0) break;
//					String newValue2 = override.substring(endKey+1, otherEndKey);
//                    setMinNodeSize(index*2, "NODSIZE", TextUtils.atof(newValue1), TextUtils.atof(newValue2));
//                    setMinNodeSize(index*2+1, TextUtils.atof(newValue2));
				} else if (key.equals("nr"))
				{
					startKey = override.indexOf('=', startKey);
					if (startKey < 0) break;
					endKey = override.indexOf(';', startKey);
					if (endKey < 0) break;
//					String newValue = override.substring(startKey+1, endKey);
                    System.out.println("No implemented in TSMRules");
//                    setMinNodeSize(index, TextUtils.atof(newValue));
				}
				pos = endKey + 1;
				continue;
			}
			if (key.equals("w"))
			{
                startKey = endKey + 1;
                endKey = override.indexOf(';', startKey);
                if (endKey < 0) break;
                String newValue = override.substring(startKey, endKey);
			    //rules.wideLimit = new Double(TextUtils.atof(newValue));
				double value = TextUtils.atof(newValue);
				if (value > 0) setWideLimits(new double[] {value});
                pos = endKey + 1;
                continue;
			}

			/*
			if (key.equals("W"))
			{
				startKey = endKey + 1;
				// Getting the number of wide values
				//endKey = override.indexOf('[', startKey);
				startKey = override.indexOf('[', endKey) + 1;
				endKey = override.indexOf(']', startKey);
				StringTokenizer parse = new StringTokenizer(override.substring(startKey, endKey));
				if (endKey < 0) break;

				try
				{
					while (parse.hasMoreElements())
					{
						String val = parse.nextToken(",");
						double value = TextUtils.atof(val);
						if (value > 0)
							setWideLimits(new double[] {value});
					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
				//String newValue = override.substring(startKey, endKey);
				//wideLimit = new Double(TextUtils.atof(newValue));
				pos = endKey + 2;
				continue;
			}
			*/
			// Skip this format
			endKey = override.indexOf(';', startKey);
			pos = endKey + 1;
		}
	}

    /**
	 * Method to build "factory" design rules, given the current technology settings.
	 * Returns the "factory" design rules for this Technology.
	 * Returns null if there is an error loading the rules.
     */
    public void loadDRCRules(Technology tech, Foundry foundry, DRCTemplate theRule, boolean pSubstrateProcess)
    {
        int numMetals = tech.getNumMetals();

        if (theRule.isRuleIgnoredInPSubstrateProcess(pSubstrateProcess))  // Skip this rule in PSubstrate process
            return;

        // load the DRC tables from the explanation table
        int when = theRule.when;

        if (when != DRCTemplate.DRCMode.ALL.mode())
        {
            // New calculation
            boolean newValue = true;
            // Check all possibles foundries for this particular technology
            for (Foundry.Type t : Foundry.Type.getValues())
            {
                // The foundry is present but is not the chosen one, then invalid rule
                if (t == Foundry.Type.NONE) continue;

                if ((when&t.getMode()) != 0 && foundry.getType() != t)
                    newValue = false;
                if (!newValue) break;
            }
            boolean oldValue = true;
            // One of the 2 is present. Absence means rule is valid for both
            if ((when&Foundry.Type.ST.getMode()) != 0 && foundry.getType() == Foundry.Type.TSMC)
                oldValue = false;
            else if ((when&Foundry.Type.TSMC.getMode()) != 0 && foundry.getType() == Foundry.Type.ST)
                oldValue = false;
            if(oldValue != newValue)
                assert(false); // check this condition and clean the code!
            if (!oldValue)
                return; // skipping this rule
        }

        // Skipping metal rules if there is no match in number of metals
        // Add M2/M3/M4/M5/M6
        // @TOD CHECK THIS METAL CONDITION
        if ((when&(DRCTemplate.DRCMode.M7.mode()|DRCTemplate.DRCMode.M8.mode()|DRCTemplate.DRCMode.M5.mode()|DRCTemplate.DRCMode.M6.mode())) != 0)
        {
            DRCTemplate.DRCMode m = DRCTemplate.DRCMode.ALL;
//          numMetals >= 2 ? DRCTemplate.DRCMode.valueOf("M"+numMetals) : DRCTemplate.DRCMode.ALL;

	          try{
	              if (numMetals >= 2)
	              {
	                   m = DRCTemplate.DRCMode.valueOf("M"+numMetals);
	              }
	          }
	          catch (Exception e)
	          {
	              System.out.println("Trying to upload metal layer that does not exist:" + numMetals);
	          }

            if ((when&m.mode()) == 0)
                return;
        }

        // find the layer or primitive names
        Layer lay1 = null;
        int index1 = -1;
        if (theRule.name1 != null)
        {
            lay1 = tech.findLayer(theRule.name1);
            if (lay1 == null)
                index1 = tech.getRuleNodeIndex(theRule.name1);
            else
                index1 = lay1.getIndex();
            if (index1 == -1)
            {
                System.out.println("Warning: no layer '" + theRule.name1 + "' in " +
                        tech.getTechName());
                return;
            }
        }
        Layer lay2 = null;
        int index2 = -1;
        if (theRule.name2 != null)
        {
            lay2 = tech.findLayer(theRule.name2);
            if (lay2 == null)
                index2 = tech.getRuleNodeIndex(theRule.name2);
            else
                index2 = lay2.getIndex();
            if (index2 == -1)
            {
                System.out.println("Warning: no layer '" + theRule.name2 + "' in " +
                        tech.getTechName());
                return;
            }
        }

        // find the index in a two-layer upper-diagonal table
        int index = -1;
        if (index1 >= 0 && index2 >= 0)
            index = getRuleIndex(index1, index2);
        else if (index1 >= 0)
            index = index1;
        else if (index2 >= 0)
            assert(false); // check this case

        // get more information about the rule
        double distance = theRule.getValue(0);

        // find the nodes and arcs associated with the rule
        PrimitiveNode nty = null;
        ArcProto aty = null;
        if (theRule.nodeName != null)
        {
            if (theRule.ruleType == DRCTemplate.DRCRuleType.ASURROUND)
            {
                aty = tech.findArcProto(theRule.nodeName);
                if (aty == null)
                {
                    System.out.println("Warning: no arc '" + theRule.nodeName + "' in mocmos technology");
                    return;
                }
            } else if (theRule.ruleType != DRCTemplate.DRCRuleType.SPACING) // Special case with spacing rules
            {
                nty = tech.findNodeProto(theRule.nodeName);
                if (nty == null)
                {
                    System.out.println("Warning: no node '" + theRule.nodeName + "' in " +
                            tech.getTechName());
                    return;
                }
            }
        }

        // set the rule
        double [] specValues;
        switch (theRule.ruleType)
        {
            case MINWID:
//                tech.setLayerMinWidth(theRule.name1, theRule.ruleName, distance);
            case MINWIDCOND:
                addRule(index1, theRule);
                addRelationship(lay1, lay1); // adding this rule for arcs if only min width is the only rule for given layer
                break;
            case FORBIDDEN:
                if (nty != null) // node forbidden
                    addRule(tech.getPrimNodeIndexInTech(nty), theRule);
                else
                    addRule(index, theRule);
                break;
            case G0CPL:
            case DIAGONALVIA:
            case DIFFMASKSEP:
            case MINAREA:
            case MINENCLOSEDAREA:
            case EXTENSION:
            case EXTENSIONGATE:
                if (index == -1)
                    addRule(index1, theRule);
                else
                    addRule(index, theRule);
                break;
            case SPACING:
            case SPACINGE:
            case CONSPA:
            case UCONSPA:
            case UCONSPA2D:
                addRule(index, theRule);
                addRelationship(lay1, lay2);
                addRelationship(lay2, lay1);
                break;
            case CUTSURX:
                specValues = nty.getSpecialValues();
                specValues[2] = distance;
                assert(false);
                break;
            case CUTSURY:
                specValues = nty.getSpecialValues();
                specValues[3] = distance;
                assert(false);
                break;
            case NODSIZ:
                addRule(tech.getPrimNodeIndexInTech(nty), theRule);
                break;
            case SURROUND:
                addRule(index, theRule);
                break;
            case ASURROUND:
//                aty.setArcLayerSurroundLayer(lay1, lay2, distance);
                break;
            default:
            {
                System.out.println("Rule " +  theRule.ruleName + " type " + theRule.ruleType +
                        " not implemented in " + tech.getTechName());
                //assert(false);
            }
        }
    }

//    private DRCTemplate resizeContact(PrimitiveNode contact, Technology.NodeLayer cutNode, Technology.NodeLayer cutSurNode, String contactName)
//    {
//        DRCTemplate cutSize = getRule(cutNode.getLayer().getIndex(), DRCTemplate.DRCRuleType.MINWID); // min and max for contact cuts
//        int index = getRuleIndex(cutSurNode.getLayer().getIndex(), cutNode.getLayer().getIndex());
//        // Try first to get any rule under NodeLayersRule and if nothing found then get pair set defined as Layersrule
//        DRCTemplate cutSur = getRule(index, DRCTemplate.DRCRuleType.SURROUND, contactName);
//        DRCTemplate rule = null;
//
//        if (cutSize == null || cutSur == null)
//            System.out.println("No cut or surround rule found for " + contactName + " for layers "
//                    + cutNode.getLayer().getName() + " " + cutSurNode.getLayer().getName()
//                    + ".\n Correct size of contact " + contactName + " is not guaranteed");
////        assert(cutSize != null); assert(cutSur != null);
//        else
//            rule = resizeContact(contact, cutNode, cutSurNode, cutSize, cutSur, contactName);
//        return rule;
//    }
//
//    private DRCTemplate resizeContact(PrimitiveNode contact, Technology.NodeLayer cutNode, Technology.NodeLayer cutSurNode,
//                                      DRCTemplate cutSize, DRCTemplate cutSur, String contactName)
//    {
//        double cutX = cutSur.getValue(0), cutY = cutSur.getValue(1);
//        if (cutY < 0) cutY = cutX; // takes only value
//        double cutSizeValue = cutSize.getValue(0);
//        double totalSurrX = cutSizeValue + cutX*2;
//        double totalSurrY = cutSizeValue + cutY*2;
//
//        assert cutNode.sizeRule != null;
//        assert cutSizeValue == cutNode.getMulticutSizeX();
//        assert cutSizeValue == cutNode.getMulticutSizeY();
//
////        DRCTemplate minNode = getRule(contact.getPrimNodeIndexInTech(), DRCTemplate.DRCRuleType.NODSIZ);
//        if (getRule(contact.getPrimNodeIndexInTech(), DRCTemplate.DRCRuleType.NODSIZ) == null) {
////            contact.setDefSize(totalSurrX, totalSurrY);
//            contact.setMinSize(totalSurrX, totalSurrY, "Minimum size set by resize function");
//        }
//        double minWidth = contact.getMinSizeRule().getWidth();
//        double minHeight = contact.getMinSizeRule().getHeight();
//        double offX = (minWidth - totalSurrX)/2;
//        double offY = (minHeight - totalSurrY)/2;
//        contact.setSizeOffset(new SizeOffset(offX, offX, offY, offY));
////        DRCTemplate minNode = getRule(contact.getPrimNodeIndexInTech(), DRCTemplate.DRCRuleType.NODSIZ);
////        if (minNode != null)
////        {
////            tech.setDefNodeSize(contact, minNode.getValue(0), minNode.getValue(1));
////            double offX = (contact.getMinSizeRule().getWidth() - totalSurrX)/2;
////            double offY = (contact.getMinSizeRule().getHeight() - totalSurrY)/2;
////            contact.setSizeOffset(new SizeOffset(offX, offX, offY, offY));
////        }
////        else
////        {
////            contact.setSizeOffset(new SizeOffset(0, 0, 0, 0));
////            contact.setDefSize(totalSurrX, totalSurrY);
////            contact.setMinSize(totalSurrX, totalSurrY, "Minimum size");
////        }
//
//        cutNode.setPoints(Technology.TechPoint.makeIndented(minWidth/2, minHeight/2));
//        return cutSur;
//    }
//
//    /**
//     * Public method to resize metal contacts
//     * @param contacts
//     * @param numMetals
//     */
//    public void resizeMetalContacts(PrimitiveNode[] contacts, int numMetals)
//    {
//        for (int i = 0; i < numMetals - 1; i++)
//        {
//            PrimitiveNode metalContact = contacts[i];
//            assert !metalContact.isNotUsed();
//            Technology.NodeLayer node = metalContact.getLayers()[2]; //cut
//            Technology.NodeLayer m1Node = metalContact.getLayers()[0]; // first metal
//            Technology.NodeLayer m2Node = metalContact.getLayers()[1]; // second metal
//
//            resizeContact(metalContact, node, m2Node, metalContact.getName());   // cur surround with respect to higher metal
//
//            SizeOffset so = metalContact.getProtoSizeOffset();
//            m1Node.setPoints(Technology.TechPoint.makeIndented(so.getHighXOffset()));
//            m2Node.setPoints(Technology.TechPoint.makeIndented(so.getHighXOffset()));
//        }
//    }
//
//    /**
//     * Common resize function for well and active contacts
//     * @param contacts array of contacts to resize
//     * @param contactNames Different contact names for butted contacts so already defined rules can be used.
//     * @param aligned
//     * @param buttedTop
//     * @param buttedRightLeft
//     */
//    public void resizeContactsWithActive(PrimitiveNode[] contacts, String[] contactNames,
//                                         boolean aligned, boolean buttedTop, boolean buttedRightLeft
//    )
//    {
//        for (int i = 0; i < contacts.length; i++)
//        {
//            PrimitiveNode contact = contacts[i];
//            Technology.NodeLayer cutNode = contact.getLayers()[4]; // Cut
//            Technology.NodeLayer activeNode = contact.getLayers()[1]; // active
//            String contactName = (contactNames != null) ? contactNames[i] : contact.getName();
//            DRCTemplate cutSize = getRule(cutNode.getLayer().getIndex(), DRCTemplate.DRCRuleType.MINWID); // min and max for contact cuts
//            int index = getRuleIndex(activeNode.getLayer().getIndex(), cutNode.getLayer().getIndex());
//            // Try first to get any rule under NodeLayersRule and if nothing found then get pair set defined as Layersrule
//            DRCTemplate cutSur = getRule(index, DRCTemplate.DRCRuleType.SURROUND, contactName);
//
//            resizeContact(contact, cutNode, activeNode, cutSize, cutSur, contactName);
//
//            Technology.NodeLayer metalNode = contact.getLayers()[0]; // metal1
//            Technology.NodeLayer wellNode = contact.getLayers()[2]; // well
//            Technology.NodeLayer selNode = contact.getLayers()[3]; // select
//
//            // setting well-active actSurround
//            index = getRuleIndex(activeNode.getLayer().getIndex(), wellNode.getLayer().getIndex());
//            DRCTemplate actSurround = getRule(index, DRCTemplate.DRCRuleType.SURROUND, contactName);
//
//            index = getRuleIndex(activeNode.getLayer().getIndex(), selNode.getLayer().getIndex());
//            DRCTemplate selSurround = getRule(index, DRCTemplate.DRCRuleType.SURROUND, contactName);
//
//            assert(actSurround != null); assert(selSurround != null);
//
//            SizeOffset so = contact.getProtoSizeOffset();
//            double value = so.getHighXOffset() - actSurround.getValue(0);
//            wellNode.setPoints(Technology.TechPoint.makeIndented(value));
//
//            value = so.getHighXOffset() - selSurround.getValue(0);
//            EdgeH left = (buttedRightLeft) ? EdgeH.fromLeft(0) : EdgeH.fromLeft(value);
//            EdgeV bottom = EdgeV.fromBottom(value);
//            EdgeH right = (buttedRightLeft) ? EdgeH.fromRight(0) : EdgeH.fromRight(value);
//            EdgeV top = (buttedTop) ? EdgeV.fromTop(0) : EdgeV.fromTop(value);
//            Technology.TechPoint [] pts = new Technology.TechPoint [] {
//                new Technology.TechPoint(left, bottom),
//                new Technology.TechPoint(right, top)};
//            selNode.setPoints(pts);
//
//            // setting metal-cut distance if rule is available
//            index = getRuleIndex(metalNode.getLayer().getIndex(), cutNode.getLayer().getIndex());
//            DRCTemplate metalSurround = getRule(index, DRCTemplate.DRCRuleType.SURROUND, contactName);
//            if (metalSurround != null)
//            {
//                double distX = cutSur.getValue(0)-metalSurround.getValue(0)+so.getHighXOffset();
//                double distY = cutSur.getValue(1)-metalSurround.getValue(1)+so.getHighYOffset();
//
//                if (aligned) // Y values don't move -> don't grow
//                {
//                    double distFromCenterX = cutSize.getValue(0)/2 + metalSurround.getValue(0);
//                    double distFromCenterY = cutSize.getValue(1)/2 + metalSurround.getValue(1);
//                    if (buttedTop && buttedRightLeft)
//                    {
//                        pts = new Technology.TechPoint [] {
//                        new Technology.TechPoint(EdgeH.fromCenter(-distFromCenterX), EdgeV.fromCenter(-distFromCenterY)),
//                        new Technology.TechPoint(EdgeH.fromCenter(distFromCenterX), EdgeV.fromCenter(distFromCenterY))};
//                    }
//                    else
//                    {
//                        pts = new Technology.TechPoint [] {
//                        new Technology.TechPoint(EdgeH.fromLeft(distX), EdgeV.fromCenter(-distFromCenterY)),
//                        new Technology.TechPoint(EdgeH.fromRight(distX), EdgeV.fromCenter(distFromCenterY))};
//                    }
//                }
//                else
//                {
//                    pts = Technology.TechPoint.makeIndented(distX, distY);
//                }
//                metalNode.setPoints(pts);
//            }
//
//            index = getRuleIndex(activeNode.getLayer().getIndex(), cutNode.getLayer().getIndex());
//            DRCTemplate activeSurround = getRule(index, DRCTemplate.DRCRuleType.SURROUND, contactName);
//            if (activeSurround != null)
//            {
//                if (buttedTop && buttedRightLeft)
//                {
////                    double distFromCenterX = cutSize.getValue(0)/2 + activeSurround.getValue(0);
//                    double distFromCenterY = cutSize.getValue(1)/2 + activeSurround.getValue(1);
//                    pts = new Technology.TechPoint [] {
//                        new Technology.TechPoint(EdgeH.fromLeft(so.getHighXOffset()), EdgeV.fromCenter(-distFromCenterY)),
//                        new Technology.TechPoint(EdgeH.fromRight(so.getHighXOffset()), EdgeV.fromTop(so.getHighYOffset()))};
//                    activeNode.setPoints(pts);
//                }
//                else
//                    activeNode.setPoints(Technology.TechPoint.makeIndented(so.getHighXOffset(), so.getHighYOffset()));
//            }
//        }
//    }
//
//    /**
//     * Common resize function for well and active contacts
//     * @param contact poly contact to resize
//     */
//    public void resizePolyContact(PrimitiveNode contact)
//    {
//        Technology.NodeLayer cutNode = contact.getLayers()[2]; // Cut
//        Technology.NodeLayer polyNode = contact.getLayers()[1]; // poly
//        DRCTemplate cutSur = resizeContact(contact, cutNode, polyNode, contact.getName());
//
//        if (cutSur == null) // error in getting the rule
//        {
//            System.out.println("Error reading surround rule in poly contact " + contact.getName());
//        }
//
//        // If doesn't have NODSIZ rule then apply the min on the poly
////        DRCTemplate minNode = getRule(contact.getPrimNodeIndexInTech(), DRCTemplate.DRCRuleType.NODSIZ);
////        if (minNode == null)
////        {
////            DRCTemplate cutSize = getRule(cutNode.getLayer().getIndex(), DRCTemplate.DRCRuleType.MINWID); // min and max for contact cuts
////            EPoint p = new EPoint(cutSur.getValue(0)*2 + cutSize.getValue(0), cutSur.getValue(1)*2 + cutSize.getValue(1));
////            contact.setDefSize(p.getX(), p.getY());
////            contact.setMinSize(p.getX(), p.getY(), "Minimum size");
////        }
//
//        Technology.NodeLayer metalNode = contact.getLayers()[0]; // metal1
//
//        // setting metal-cut distance if rule is available
//        SizeOffset so = contact.getProtoSizeOffset();
//        int index = getRuleIndex(metalNode.getLayer().getIndex(), cutNode.getLayer().getIndex());
//        DRCTemplate metalSurround = getRule(index, DRCTemplate.DRCRuleType.SURROUND, contact.getName());
//        if (metalSurround != null)
//        {
//            // Only for the min cases
////            polyNode.setPoints(Technology.TechPoint.makeIndented(0));
//            EPoint point = new EPoint(cutSur.getValue(0)-metalSurround.getValue(0),
//                    cutSur.getValue(1)-metalSurround.getValue(1));
//            metalNode.setPoints(Technology.TechPoint.makeIndented(point.getX()+so.getHighXOffset(),
//                    point.getY()+so.getHighYOffset()));
//        }
//
//        index = getRuleIndex(polyNode.getLayer().getIndex(), cutNode.getLayer().getIndex());
//        DRCTemplate polySurround = getRule(index, DRCTemplate.DRCRuleType.SURROUND, contact.getName());
//        if (polySurround != null)
//        {
//            EPoint point = new EPoint(cutSur.getValue(0)-polySurround.getValue(0),
//                    cutSur.getValue(1)-polySurround.getValue(1));
//            polyNode.setPoints(Technology.TechPoint.makeIndented(point.getX()+so.getHighXOffset(),
//                    point.getY()+so.getHighYOffset()));
//        }
//    }

    /*******************************************/
    /*** Local class to store information ******/
    public static class XMLRule extends DRCTemplate
	{
        public XMLRule(DRCTemplate rule)
        {
            super(rule);
        }

        public XMLRule(String name, double[] values, DRCRuleType type, double maxW, double minLen, int multiCuts, int when)
		{
            super(name, when, type, maxW, minLen, null, null, values, multiCuts);
		}

        public boolean isSpacingRule()
        {
            return ruleType == DRCTemplate.DRCRuleType.CONSPA || ruleType == DRCTemplate.DRCRuleType.UCONSPA;
        }

        @Override
        public boolean equals(Object obj)
		{
			// reflexive
			if (obj == this) return true;

			// should consider null case
			// symmetry but violates transitivity?
			// It seems Map doesn't provide obj as PolyNode
			if (!(obj instanceof XMLRule))
				return obj.equals(this);

			XMLRule a = (XMLRule)obj;
            boolean basic = ruleName.equals(a.ruleName) && ruleType == a.ruleType;
            if (basic) // checking node names as well
                basic = nodeName == null || nodeName.equals(a.nodeName);
            if (basic) // checking the layerNames
                basic = name1 == null || name1.equals(a.name1);
            if (basic)
                basic = name2 == null || name2.equals(a.name2);
            return (basic);
		}

        @Override
        public int hashCode()
		{
            return ruleType.hashCode();
        }
	}
}
