package tests.eu.qualimaster.storm;

import java.util.Map;

import eu.qualimaster.common.signal.AlgorithmChangeSignal;
import eu.qualimaster.common.signal.BaseSignalBolt;
import eu.qualimaster.common.signal.ParameterChangeSignal;
import eu.qualimaster.common.signal.ShutdownSignal;
import eu.qualimaster.dataManagement.DataManager;
import eu.qualimaster.dataManagement.strategies.NoStorageStrategyDescriptor;
import eu.qualimaster.events.EventManager;
import eu.qualimaster.monitoring.events.AlgorithmChangedMonitoringEvent;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Tuple;

/**
 * A simple sink bolt.
 * 
 * @author Holger Eichelberger
 */
@SuppressWarnings("serial")
public class Sink extends BaseSignalBolt {

    private static SignalCollector signals = new SignalCollector(Naming.LOG_SINK);
    private transient OutputCollector collector;
    private transient ISnk sink;
    
    /**
     * Creates a sink instance.
     */
    public Sink() {
        super(Naming.NODE_SINK, Naming.PIPELINE_NAME);
    }
    
    @SuppressWarnings("rawtypes")
    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);
        this.collector = collector;
        this.sink = DataManager.DATA_SINK_MANAGER.createDataSink("pipeline", Snk.class, 
            NoStorageStrategyDescriptor.INSTANCE);
        EventManager.send(new AlgorithmChangedMonitoringEvent(Naming.PIPELINE_NAME, Naming.NODE_SINK, "sink"));
        if (Naming.defaultInitializeAlgorithms(stormConf)) {
            this.sink.connect();
        }
    }

    @Override
    public void execute(Tuple input) {
        startMonitoring();
        Integer data = input.getInteger(0);
        sink.emit(data);
        collector.ack(input);
        emitted(data);
        endMonitoring();
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
    }

    @Override
    public void notifyParameterChange(ParameterChangeSignal signal) {
        signals.notifyParameterChange(signal);
    }
    
    @Override
    public void notifyAlgorithmChange(AlgorithmChangeSignal signal) {
        signals.notifyAlgorithmChange(signal);
    }
    
    @Override
    protected void prepareShutdown(ShutdownSignal signal) {
        signals.notifyShutdown(signal);
    }
    
    // testing
    
    /**
     * Returns the signal collector.
     * 
     * @return the signal collector
     */
    public SignalCollector getSignals() {
        return signals;
    }

    @Override
    public void cleanup() {
        if (null != sink) {
            sink.disconnect();
        }
        super.cleanup();
    }
    
}
