package tests.eu.qualimaster.storm;

import eu.qualimaster.base.pipeline.RecordingTopologyBuilder;
import eu.qualimaster.infrastructure.PipelineOptions;
import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.topology.TopologyBuilder;

/**
 * Creates the testing topology.
 * 
 * @author Holger Eichelberger
 */
public class Topology {
    
    private static boolean defaultInitAlgorithms = true;
    
    /**
     * Changes the behavior of initializing default algorithms.
     * 
     * @param init whether algorithms shall be initialized by default (true)
     */
    public static void setDefaultInitAlgorithms(boolean init) {
        defaultInitAlgorithms = init;
    }

    /**
     * Creates the testing topology.
     * 
     * @param builder the topology builder
     */
    public static void createTopology(TopologyBuilder builder) {
        Source<Src> source = new Source<Src>(Src.class, Naming.PIPELINE_NAME); // use Src2.class for fixed rate source
        builder.setSpout(Naming.NODE_SOURCE, source, 1).setNumTasks(1);
        Process process = new Process(Naming.NODE_PROCESS, Naming.PIPELINE_NAME);
        builder.setBolt(Naming.NODE_PROCESS, process, 1).setNumTasks(3).shuffleGrouping(Naming.NODE_SOURCE);
        Sink sink = new Sink();
        builder.setBolt(Naming.NODE_SINK, sink, 1).setNumTasks(1).shuffleGrouping(Naming.NODE_PROCESS);
    }
    
    // checkstyle: stop exception type check

    /**
     * Creates a standalone topology.
     * 
     * @param args the topology arguments
     * @throws Exception in case of creation problems
     */
    public static void main(String[] args) throws Exception {
        Config config = new Config();
        Naming.setDefaultInitializeAlgorithms(config, defaultInitAlgorithms);
        config.setMessageTimeoutSecs(100);
        PipelineOptions options = new PipelineOptions(args);
        RecordingTopologyBuilder b = new RecordingTopologyBuilder(options);
        createTopology(b);
        b.close(args[0], config);
        
        // main topology: int numWorkers = options.getNumberOfWorkers(2);
        options.toConf(config);
        
        if (args != null && args.length > 0) {
            config.setNumWorkers(2);
            StormSubmitter.submitTopology(args[0], config, b.createTopology());
        } else {
            config.setMaxTaskParallelism(2);
            final LocalCluster cluster = new LocalCluster();
            cluster.submitTopology(Naming.PIPELINE_NAME, config, b.createTopology());
        }
    }

    // checkstyle: resume exception type check
    
}
