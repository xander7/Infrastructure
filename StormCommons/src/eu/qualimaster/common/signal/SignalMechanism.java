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
package eu.qualimaster.common.signal;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.storm.curator.framework.CuratorFramework;
import org.apache.storm.curator.framework.CuratorFrameworkFactory;
import org.apache.storm.curator.retry.RetryOneTime;

import eu.qualimaster.Configuration;
import eu.qualimaster.events.EventManager;

/**
 * Encapsulates the signal mechanism to be used.
 * 
 * @author Holger Eichelberger
 */
public class SignalMechanism {

    private static final Map<String, CuratorFramework> FRAMEWORKS = 
        Collections.synchronizedMap(new HashMap<String, CuratorFramework>());
    private static final Map<String, String> CONNECT_INFO = 
        Collections.synchronizedMap(new HashMap<String, String>());
    private static final Map<String, Namespace> NAMESPACES = 
        Collections.synchronizedMap(new HashMap<String, Namespace>());
    
    /**
     * Represents a namespace that is able to cache signals if needed.
     * 
     * @author Holger Eichelberger
     */
    private static class Namespace {
        
        private NamespaceState state;
        private ConcurrentLinkedQueue<CachedSignal> signals = new ConcurrentLinkedQueue<CachedSignal>();
       
        /**
         * Creates the namespace with default state {@link NamespaceState#DISABLE}.
         */
        private Namespace() {
            state = NamespaceState.DISABLE;
        }
        
        /**
         * Returns the actual namespace state.
         *  
         * @return the state
         */
        private NamespaceState getState() {
            return state;
        }
        
        /**
         * Changes the state. If the namespace becomes {@link NamespaceState#ENABLE}, cached signals are sent.
         * 
         * @param state the new state
         */
        private void setState(NamespaceState state) {
            switch (state) {
            case ENABLE:
                synchronized (signals) {
                    while (!signals.isEmpty()) {
                        send(signals.poll());
                    }
                }
                break;
            case DISABLE:
                break;
            case CLEAR:
                signals.clear();
                break;
            default:
                break;
            }
            this.state = state;
        }
        
        /**
         * Caches a signal for later sending.
         * 
         * @param signal the signal to be cached
         * @throws SignalException if the signal cannot be sent
         */
        private void cacheSignal(CachedSignal signal) throws SignalException {
            switch (state) {
            case ENABLE:
                signal.send();
                break;
            case DISABLE:
                synchronized (signals) {
                    signals.offer(signal);
                }
                break;
            case CLEAR:
                break;
            default: 
                break;
            }
        }
        
        /**
         * Sends a signal. A {@link SignalException} for a deferred signal is logged.
         * 
         * @param signal the signal to be sent
         */
        private void send(CachedSignal signal) {
            if (null != signal) {
                try {
                    signal.send();
                } catch (SignalException e) {
                    getLogger().error(e.getMessage(), e);
                }
            }
        }
        
    }
    
    /**
     * Represents a cached signal to be send in a deferred way when its target namespace becomes enabled.
     * 
     * @author Holger Eichelberger
     */
    private abstract static class CachedSignal {
        
        private AbstractTopologyExecutorSignal signal;
        private CuratorFramework mechanism;

        /**
         * Creates a cached signal without explicit sending mechanism.
         * 
         * @param signal the signal
         */
        protected CachedSignal(AbstractTopologyExecutorSignal signal) {
            this(null, signal);
        }
        
        /**
         * Creates a cached signal with explicit sending mechanism.
         * 
         * @param mechanism the sending mechanism (may be <b>null</b> so that it shall be determined from 
         *     the <code>signal</code> if needed)
         * @param signal the signal to be sent
         */
        protected CachedSignal(CuratorFramework mechanism, AbstractTopologyExecutorSignal signal) {
            this.mechanism = mechanism;
            this.signal = signal;
        }
        
        /**
         * Returns the signal.
         * 
         * @return the signal
         */
        protected AbstractTopologyExecutorSignal getSignal() {
            return signal;
        }
        
        /**
         * Returns the signal sending mechanism.
         * 
         * @return the mechanism (may be <b>null</b>)
         */
        protected CuratorFramework getMechanism() {
            return mechanism;
        }
        
        /**
         * Sends the signal.
         * 
         * @throws SignalException in case that sending fails
         */
        protected abstract void send() throws SignalException;
        
    }
    
    /**
     * Clears the internal state of this class.
     */
    public static void clear() {
        for (CuratorFramework framework: FRAMEWORKS.values()) {
            framework.close();
        }
    }
    
    /**
     * Provide a namespace specific connect string for distributed processing where the infrastructure
     * configuration is not set correctly.
     * 
     * @param namespace the namespace
     * @param connectString the connect string
     */
    public static void setConnectString(String namespace, String connectString) {
        CONNECT_INFO.put(namespace, connectString);
    }
    
    /**
     * Releases the signal mechanism instance for the given <code>namespace</code>
     * from the internal cache and closes the mechanism.
     *   
     * @param namespace the namespace to remove the framework for
     */
    public static void releaseMechanism(String namespace) {
        CuratorFramework framework = FRAMEWORKS.remove(namespace);
        if (null != framework) {
            framework.close();
        }
    }

    /**
     * Prepares the signal mechanism for the given namespace.
     * 
     * @param namespace the namespace
     * @throws IOException in case that preparing fails
     * @see #releaseMechanism(String)
     */
    public static void prepareMechanism(String namespace) throws IOException {
        if (Configuration.getPipelineSignalsCurator()) {
            obtainFramework(namespace);
        }
    }
    
    /**
     * Obtains the respective curator framework.
     * 
     * @param namespace the namespace
     * @return the curator famework
     * @throws IOException in case of errors obtaining the curator framework
     */
    static CuratorFramework obtainFramework(String namespace) throws IOException {
        CuratorFramework result = FRAMEWORKS.get(namespace);
        if (null == result) {
            String connectString = CONNECT_INFO.get(namespace);
            if (null == connectString) {
                connectString = Configuration.getZookeeperConnectString();
            }
            result = CuratorFrameworkFactory.builder().namespace(namespace)
                .connectString(connectString)
                .retryPolicy(new RetryOneTime(500)).build();
            FRAMEWORKS.put(namespace, result);
            result.start();
        }
        return result;
    }

    /**
     * Sends a signal to the given topology / namespace / executor.
     * 
     * @param framework the framework to send with
     * @param executor the executor to send to
     * @param payload the signal payload to send
     * @throws SignalException in case that sending fails
     */
    static void sendSignal(CuratorFramework framework, String executor, String payload) 
        throws SignalException {
        sendSignal(framework, executor, payload.getBytes());
    }
    
    // checkstyle: stop exception type check
    
    /**
     * Sends a signal to the given topology / namespace / executor.
     * 
     * @param framework the framework to send with
     * @param executor the executor to send to
     * @param payload the signal payload to send
     * @throws SignalException in case that sending fails
     */
    static void sendSignal(CuratorFramework framework, String executor, byte[] payload) 
        throws SignalException {
        try {
            String namespace = framework.getNamespace();
            String path = executor;
            org.apache.storm.zookeeper.data.Stat stat = framework.checkExists().forPath(path);
            if (stat == null) {
                framework.create().creatingParentsIfNeeded().forPath(path);
                getLogger().info("created path " + path);
                stat = framework.checkExists().forPath(path);
            }
            getLogger().info("path " + stat);
            if (stat == null) {
                throw new Exception("component does not exist " + namespace + "/" + executor);
            }
            getLogger().info(System.currentTimeMillis() + "sending " + payload + " to " + namespace + "/" + executor);
            framework.setData().forPath(path, payload);
        } catch (Exception e) {
            getLogger().error(e.getMessage(), e);
            throw new SignalException(e);
        }
    }
    
    // checkstyle: resume exception type check
    
    /**
     * Returns the logger for this class.
     * 
     * @return the logger
     */
    private static Logger getLogger() {
        return LogManager.getLogger(SignalMechanism.class);
    }
    
    /**
     * Sends <code>signal</code> via <code>mechanism</code>. If <code>mechanism</code> is not given and 
     * {@link Configuration#getPipelineSignalsCurator()} is enabled, this class tries to obtain a mechanism via the
     * configuration.
     * 
     * @param mechanism the mechanism - may be <b>null</b>
     * @param signal the actual signal
     * @throws SignalException in case that obtaining the mechanism fails or that sending fails
     */
    static void sendSignal(CuratorFramework mechanism, AbstractTopologyExecutorSignal signal) throws SignalException {
        String namespace = signal.getNamespace();
        Namespace space = obtainNamespace(namespace);
        if (Configuration.getPipelineSignalsCurator()) {
            if (null == mechanism) {
                try {
                    mechanism = obtainFramework(namespace);
                } catch (IOException e) {
                    throw new SignalException(e);
                }
            }
            if (NamespaceState.DISABLE == space.getState()) {
                space.cacheSignal(new CachedSignal(mechanism, signal) {

                    @Override
                    protected void send() throws SignalException {
                        sendSignal(getMechanism(), getSignal().getExecutor(), getSignal().createPayload());
                    }
                    
                });
            } else {
                sendSignal(mechanism, signal.getExecutor(), signal.createPayload());
            }
        } else {
            if (NamespaceState.DISABLE == space.getState()) {
                space.cacheSignal(new CachedSignal(signal) {

                    @Override
                    protected void send() {
                        EventManager.send(getSignal());
                    }
                    
                });
            } else {
                EventManager.send(signal);
            }
        }
    }
    
    /**
     * Defines namespace states.
     * 
     * @author Holger Eichelberger
     */
    public enum NamespaceState {
        
        /**
         * The namespace is disabled (by default) and signals are cached until the namespace becomes {@link #ENABLE}.
         */
        DISABLE,
        
        /**
         * The namespace is enabled. If there are cached signals, they are sent out.
         */
        ENABLE,
        
        /**
         * The namespace is clearing, i.e., about to be removed at the end of lifetime of a pipeline. The next
         * state will implicitly be {@link #DISABLE}.
         */
        CLEAR
    }
    
    /**
     * Returns the actual state of a namespace.
     *  
     * @param namespace the namespace name
     * @return the state
     */
    public static NamespaceState getState(String namespace) {
        NamespaceState state;
        Namespace space = NAMESPACES.get(namespace);
        if (null != space) {
            state = space.getState();
        } else {
            state = NamespaceState.DISABLE;
        }
        return state;
    }
    
    /**
     * Obtains a namespace.
     * 
     * @param namespace the namespace name
     * @return the namespace object
     */
    private static Namespace obtainNamespace(String namespace) {
        Namespace space;
        if (null == namespace) { // shall not occur
            namespace = "";
        }
        synchronized (NAMESPACES) {
            space = NAMESPACES.get(namespace);
            if (null == space) {
                space = new Namespace();
                NAMESPACES.put(namespace, space);
            }
        }
        return space;
    }
    
    /**
     * Changes the state of a signal namespace.
     * 
     * @param namespace the namespace
     * @param state the new state
     */
    public static void changeSignalNamespaceState(String namespace, NamespaceState state) {
        if (null != namespace) {
            Namespace space = NAMESPACES.get(namespace); // do not create per se
            switch(state) {
            case ENABLE:
                space = obtainNamespace(namespace);
                space.setState(state);
                break;
            case DISABLE:
                space.setState(state);
                break;
            case CLEAR:
                if (null != space) {
                    space.setState(state);
                }
                NAMESPACES.remove(namespace);
                break;
            default:
                break;
            }
        }
    }

}
