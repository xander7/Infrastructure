/*
 * Copyright 2009-2015 University of Hildesheim, Software Systems Engineering
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.qualimaster.monitoring.storm;

import eu.qualimaster.infrastructure.PipelineLifecycleEvent.Status;
import eu.qualimaster.monitoring.MonitoringManager;
import eu.qualimaster.monitoring.systemState.PipelineNodeSystemPart;
import eu.qualimaster.monitoring.systemState.PipelineSystemPart;
import eu.qualimaster.monitoring.systemState.SystemPart;
import eu.qualimaster.observables.IObservable;
import eu.qualimaster.observables.ResourceUsage;
//import eu.qualimaster.observables.Scalability;
//import eu.qualimaster.observables.TimeBehavior;

/**
 * A simple class collecting pipeline statistics from collected pipeline nodes (excluding storm internal nodes).
 * Call {@link #collect(PipelineNodeSystemPart)} on individual nodes and at the end {@link #commit()} for the 
 * entire pipeline. [public for testing]
 * 
 * @author Holger Eichelberger
 */
public class PipelineStatistics {

    private PipelineSystemPart part;
    //private long topologyThroughputItems = 0;
    //private double topologyLatency = 0;
    private double topologyCapacity = 0;
    private int needInitalization = 0;
    private int initialized = 0;
    
    /**
     * Creates the pipeline statistics.
     * 
     * @param part the related pipeline system part
     */
    public PipelineStatistics(PipelineSystemPart part) {
        this.part = part;
    }
    
    /**
     * Whether the pipeline is started.
     * 
     * @return <code>true</code> if the pipeline is started, <code>false</code> else
     */
    boolean isStarted() {
        return Status.STARTED == part.getStatus();
    }

    /**
     * Commits the aggregated pipeline measures. [public for testing]
     * 
     * @return whether all pipeline elements are initialized
     */
    public boolean commit() {
        // needInitialization > 0 may exclude simple pipelines
        boolean allInitialized = isStarted() || (needInitalization > 0 && needInitalization == initialized);
        //setPartPositiveValue(part, TimeBehavior.THROUGHPUT_ITEMS, topologyThroughputItems);
        //setPartPositiveValue(part, Scalability.ITEMS, topologyThroughputItems);
        ////setPartPositiveValue(part, TimeBehavior.THROUGHPUT_VOLUME, topologyThroughputVolume);
        //setPartPositiveValue(part, TimeBehavior.LATENCY, topologyLatency);
        setPartPositiveValue(part, ResourceUsage.CAPACITY, 
            needInitalization > 0 ? topologyCapacity / needInitalization : 0);
        // do not summarize the executors, they are set implicitly
        
        if (!allInitialized && !MonitoringManager.hasAdaptationModel()) { // enable DML connect if no model
            allInitialized = true;
        }
        return allInitialized;
    }
    
    /**
     * Sets a value for the given <code>observable</code> in <code>part</code> but only if <code>value</code>
     * is positive. This avoids sending messages if actually nothing has been observed.
     * 
     * @param part the target system part 
     * @param observable the target observable in <code>part</code>
     * @param value the actual value
     */
    static void setPartPositiveValue(SystemPart part, IObservable observable, double value) {
        if (value > 0) {
            part.setValue(observable, value, null);
        }
    }
    
    /**
     * Collects the values from the <code>nodePart</code> to successively obtain pipeline statistics.
     * 
     * @param nodePart the node part
     */
    public void collect(PipelineNodeSystemPart nodePart) {
        needInitalization++;
        //topologyThroughputItems += nodePart.getObservedValue(TimeBehavior.THROUGHPUT_ITEMS);
        ////topologyThroughputVolume += executorThroughputVolume;
        //topologyLatency += nodePart.getObservedValue(TimeBehavior.LATENCY);
        topologyCapacity += nodePart.getObservedValue(ResourceUsage.CAPACITY);
        // assuming latency is an aggregating obs
        // do not summarize the executors, they are set implicitly
        if (!isStarted() && nodePart.isInitialized()) {
            initialized++;
        }
    }
    
}
