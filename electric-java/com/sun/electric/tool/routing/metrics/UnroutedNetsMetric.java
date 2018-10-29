package com.sun.electric.tool.routing.metrics;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.technology.technologies.Generic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

/**
 * Class to calculate number of unrouted arcs in a cell
 * @author Gilda Garreton
 */
public class UnroutedNetsMetric extends RoutingMetric<Integer>
{
    //private static Logger logger = LoggerFactory.getLogger(UnroutedNetsMetric.class);

    /* (non-Javadoc)
	 * @see com.sun.electric.tool.routing.metrics.UnroutedNetsMetric#calculate(com.sun.electric.database.hierarchy.Cell)
	 */
	public Integer calculate(Cell cell)
    {
		return processNets(cell, 0);
	}

	/* (non-Javadoc)
	 * @see com.sun.electric.tool.routing.metrics.RoutingMetric#reduce(java.lang.Object,
	 * com.sun.electric.database.topology.ArcInst, com.sun.electric.database.network.Network )
	 */
	@Override
	protected Integer reduce(Integer result, ArcInst instance, Network net)
    {
		int isUnrouted = (instance.getProto() == Generic.tech().unrouted_arc) ? 1 : 0;
		return result + isUnrouted;
	}

	/* (non-Javadoc)
	 * @see com.sun.electric.tool.routing.metrics.RoutingMetric#reduce(java.lang.Object,
	 * com.sun.electric.database.network.Network )
	 */
    @Override
	protected Integer reduce(Integer result, Network net)
    {
        for(Iterator<ArcInst> arcIt = net.getArcs(); arcIt.hasNext();)
        {
            ArcInst instance = arcIt.next();
            if (instance.getProto() == Generic.tech().unrouted_arc)
            {
                result++;
                //logger.debug("process net: " + net.getName() + " arc " + instance.getName());
                break; // found 1 case
            }
        }
        return result;
	}
}
