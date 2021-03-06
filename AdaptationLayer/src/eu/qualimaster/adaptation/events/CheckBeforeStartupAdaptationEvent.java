package eu.qualimaster.adaptation.events;

import eu.qualimaster.common.QMInternal;
import eu.qualimaster.events.EventManager;
import eu.qualimaster.infrastructure.PipelineLifecycleEvent;

/**
 * Notifies the adaptation that a resource check of a pipeline to be started shall be done.
 * 
 * @author Holger Eichelberger
 */
public class CheckBeforeStartupAdaptationEvent extends AdaptationEvent {

    private static final long serialVersionUID = -6707728566855995516L;
    private PipelineLifecycleEvent event;

    /**
     * Creates a startup check adaptation event.
     * 
     * @param event the causing lifecycle event
     */
    @QMInternal
    public CheckBeforeStartupAdaptationEvent(PipelineLifecycleEvent event) {
        this.event = event;
    }
    
    /**
     * The name of the pipeline being affected.
     * 
     * @return the pipeline name
     */
    public String getPipeline() {
        return event.getPipeline();
    }
    
    @QMInternal
    @Override
    public boolean adjustLifecycle(String failReason, Integer failCode) {
        boolean adjusted = false;
        if (null == failReason && null == failCode) {
            EventManager.send(new PipelineLifecycleEvent(event, PipelineLifecycleEvent.Status.CHECKED));
            adjusted = true;
        } 
        return adjusted;
    }

}
