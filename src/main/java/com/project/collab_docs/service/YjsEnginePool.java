package com.project.collab_docs.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance pool of GraalVM JavaScript engines for Yjs operations.
 * This ensures thread safety and optimal resource utilization.
 */
@Slf4j
@Component
public class YjsEnginePool {

    private static final int MIN_IDLE_ENGINES = 2;
    private static final int MAX_IDLE_ENGINES = 8;
    private static final int MAX_TOTAL_ENGINES = 16;
    private static final Duration MAX_WAIT = Duration.ofSeconds(5);
    private static final Duration EVICTION_RUN_INTERVAL = Duration.ofMinutes(5);
    private static final Duration IDLE_TIME = Duration.ofMinutes(10);

    private GenericObjectPool<YjsEngine> pool;
    private Engine graalEngine;
    private String yjsLibraryCode;
    private String yjsHelperCode;
    private final AtomicInteger engineIdCounter = new AtomicInteger(0);

    @PostConstruct
    public void initialize() throws IOException {
        log.info("Initializing Yjs Engine Pool...");

        // Create shared GraalVM engine for better performance
        graalEngine = Engine.newBuilder("js")
                .option("engine.WarnInterpreterOnly", "false")
                .option("js.ecmascript-version", "2022")
                .build();

        // Load Yjs library and helper functions
        loadYjsLibrary();

        // Configure the pool
        GenericObjectPoolConfig<YjsEngine> config = new GenericObjectPoolConfig<>();
        config.setMinIdle(MIN_IDLE_ENGINES);
        config.setMaxIdle(MAX_IDLE_ENGINES);
        config.setMaxTotal(MAX_TOTAL_ENGINES);
        config.setMaxWait(MAX_WAIT);
        config.setTimeBetweenEvictionRuns(EVICTION_RUN_INTERVAL);
        config.setMinEvictableIdleTime(IDLE_TIME);
        config.setTestOnBorrow(true);
        config.setTestOnReturn(true);
        config.setTestWhileIdle(true);
        config.setBlockWhenExhausted(true);

        // Create the pool
        pool = new GenericObjectPool<>(new YjsEngineFactory(), config);

        // Pre-create minimum engines
        try {
            pool.preparePool();
            log.info("Yjs Engine Pool initialized successfully with {} engines", pool.getNumIdle());
        } catch (Exception e) {
            log.error("Failed to prepare Yjs engine pool", e);
            throw new RuntimeException("Failed to initialize Yjs engine pool", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Yjs Engine Pool...");
        if (pool != null) {
            pool.close();
        }
        if (graalEngine != null) {
            graalEngine.close();
        }
    }

    /**
     * Execute a Yjs operation with automatic engine management
     */
    public <T> T executeWithEngine(YjsOperation<T> operation) {
        YjsEngine engine = null;
        try {
            engine = pool.borrowObject();
            return operation.execute(engine);
        } catch (Exception e) {
            log.error("Error executing Yjs operation", e);
            throw new RuntimeException("Failed to execute Yjs operation", e);
        } finally {
            if (engine != null) {
                try {
                    pool.returnObject(engine);
                } catch (Exception e) {
                    log.error("Error returning engine to pool", e);
                }
            }
        }
    }

    /**
     * Load Yjs library from resources
     */
    private void loadYjsLibrary() throws IOException {
        // Load the minified Yjs library
        ClassPathResource yjsResource = new ClassPathResource("js/yjs.min.js");
        if (!yjsResource.exists()) {
            // Fallback to CDN version embedded in code
            yjsLibraryCode = getEmbeddedYjsLibrary();
        } else {
            yjsLibraryCode = Files.readString(Path.of(yjsResource.getURI()), StandardCharsets.UTF_8);
        }

        // Load helper functions
        yjsHelperCode = """
            // Helper functions for Yjs operations
            const Y = globalThis.Y || require('yjs');
            
            const YjsHelpers = {
                // Merge an update into existing state
                mergeUpdate: function(currentStateBase64, updateBase64) {
                    try {
                        const doc = new Y.Doc();
                        
                        if (currentStateBase64 && currentStateBase64.length > 0) {
                            const currentState = Uint8Array.from(atob(currentStateBase64), c => c.charCodeAt(0));
                            Y.applyUpdate(doc, currentState);
                        }
                        
                        if (updateBase64 && updateBase64.length > 0) {
                            const update = Uint8Array.from(atob(updateBase64), c => c.charCodeAt(0));
                            Y.applyUpdate(doc, update);
                        }
                        
                        const mergedState = Y.encodeStateAsUpdate(doc);
                        return btoa(String.fromCharCode(...mergedState));
                    } catch (error) {
                        throw new Error('Failed to merge update: ' + error.message);
                    }
                },
                
                // Get state vector for sync
                getStateVector: function(stateBase64) {
                    try {
                        const doc = new Y.Doc();
                        if (stateBase64 && stateBase64.length > 0) {
                            const state = Uint8Array.from(atob(stateBase64), c => c.charCodeAt(0));
                            Y.applyUpdate(doc, state);
                        }
                        const stateVector = Y.encodeStateVector(doc);
                        return btoa(String.fromCharCode(...stateVector));
                    } catch (error) {
                        throw new Error('Failed to get state vector: ' + error.message);
                    }
                },
                
                // Compute diff between states
                computeDiff: function(fromStateBase64, toStateBase64) {
                    try {
                        const fromDoc = new Y.Doc();
                        const toDoc = new Y.Doc();
                        
                        if (fromStateBase64 && fromStateBase64.length > 0) {
                            const fromState = Uint8Array.from(atob(fromStateBase64), c => c.charCodeAt(0));
                            Y.applyUpdate(fromDoc, fromState);
                        }
                        
                        if (toStateBase64 && toStateBase64.length > 0) {
                            const toState = Uint8Array.from(atob(toStateBase64), c => c.charCodeAt(0));
                            Y.applyUpdate(toDoc, toState);
                        }
                        
                        const stateVector = Y.encodeStateVector(fromDoc);
                        const diff = Y.encodeStateAsUpdate(toDoc, stateVector);
                        return btoa(String.fromCharCode(...diff));
                    } catch (error) {
                        throw new Error('Failed to compute diff: ' + error.message);
                    }
                },
                
                // Validate an update
                validateUpdate: function(updateBase64) {
                    try {
                        if (!updateBase64 || updateBase64.length === 0) {
                            return false;
                        }
                        const update = Uint8Array.from(atob(updateBase64), c => c.charCodeAt(0));
                        const doc = new Y.Doc();
                        Y.applyUpdate(doc, update);
                        return true;
                    } catch (error) {
                        return false;
                    }
                },
                
                // Merge multiple updates efficiently
                mergeMultipleUpdates: function(baseStateBase64, updatesBase64Array) {
                    try {
                        const doc = new Y.Doc();
                        
                        if (baseStateBase64 && baseStateBase64.length > 0) {
                            const baseState = Uint8Array.from(atob(baseStateBase64), c => c.charCodeAt(0));
                            Y.applyUpdate(doc, baseState);
                        }
                        
                        for (const updateBase64 of updatesBase64Array) {
                            if (updateBase64 && updateBase64.length > 0) {
                                const update = Uint8Array.from(atob(updateBase64), c => c.charCodeAt(0));
                                Y.applyUpdate(doc, update);
                            }
                        }
                        
                        const mergedState = Y.encodeStateAsUpdate(doc);
                        return btoa(String.fromCharCode(...mergedState));
                    } catch (error) {
                        throw new Error('Failed to merge multiple updates: ' + error.message);
                    }
                },
                
                // Get document snapshot for persistence
                getSnapshot: function(stateBase64) {
                    try {
                        const doc = new Y.Doc();
                        if (stateBase64 && stateBase64.length > 0) {
                            const state = Uint8Array.from(atob(stateBase64), c => c.charCodeAt(0));
                            Y.applyUpdate(doc, state);
                        }
                        const snapshot = Y.snapshot(doc);
                        const encoded = Y.encodeSnapshot(snapshot);
                        return btoa(String.fromCharCode(...encoded));
                    } catch (error) {
                        throw new Error('Failed to get snapshot: ' + error.message);
                    }
                },
                
                // Restore from snapshot
                restoreFromSnapshot: function(snapshotBase64, stateVectorBase64) {
                    try {
                        const snapshot = Uint8Array.from(atob(snapshotBase64), c => c.charCodeAt(0));
                        const decoded = Y.decodeSnapshot(snapshot);
                        const doc = Y.createDocFromSnapshot(decoded.ds, decoded.sv);
                        
                        if (stateVectorBase64) {
                            const stateVector = Uint8Array.from(atob(stateVectorBase64), c => c.charCodeAt(0));
                            const update = Y.encodeStateAsUpdate(doc, stateVector);
                            return btoa(String.fromCharCode(...update));
                        } else {
                            const update = Y.encodeStateAsUpdate(doc);
                            return btoa(String.fromCharCode(...update));
                        }
                    } catch (error) {
                        throw new Error('Failed to restore from snapshot: ' + error.message);
                    }
                }
            };
            
            // Make helpers available globally
            globalThis.YjsHelpers = YjsHelpers;
            """;
    }

    /**
     * Fallback embedded Yjs library (simplified version)
     */
    private String getEmbeddedYjsLibrary() {
        // This would contain the actual Yjs library code
        // For production, download yjs.min.js and place in resources/js/
        return """
            // Placeholder - download yjs.min.js from https://cdn.jsdelivr.net/npm/yjs@13/dist/yjs.min.js
            // and place in src/main/resources/js/yjs.min.js
            """;
    }

    /**
     * Factory for creating Yjs engines
     */
    private class YjsEngineFactory extends BasePooledObjectFactory<YjsEngine> {

        @Override
        public YjsEngine create() {
            int engineId = engineIdCounter.incrementAndGet();
            log.debug("Creating new Yjs engine #{}", engineId);

            Context context = Context.newBuilder("js")
                    .engine(graalEngine)
                    .allowHostAccess(HostAccess.ALL)
                    .allowHostClassLookup(className -> false)
                    .option("js.ecmascript-version", "2022")
                    .build();

            try {
                // Load Yjs library
                context.eval("js", yjsLibraryCode);
                // Load helper functions
                context.eval("js", yjsHelperCode);

                // Verify initialization
                Value helpers = context.eval("js", "globalThis.YjsHelpers");
                if (helpers == null || helpers.isNull()) {
                    throw new RuntimeException("Failed to initialize Yjs helpers");
                }

                YjsEngine engine = new YjsEngine(engineId, context);
                log.debug("Successfully created Yjs engine #{}", engineId);
                return engine;

            } catch (Exception e) {
                context.close();
                throw new RuntimeException("Failed to create Yjs engine", e);
            }
        }

        @Override
        public PooledObject<YjsEngine> wrap(YjsEngine engine) {
            return new DefaultPooledObject<>(engine);
        }

        @Override
        public void destroyObject(PooledObject<YjsEngine> p) {
            YjsEngine engine = p.getObject();
            log.debug("Destroying Yjs engine #{}", engine.getId());
            engine.close();
        }

        @Override
        public boolean validateObject(PooledObject<YjsEngine> p) {
            YjsEngine engine = p.getObject();
            try {
                // Quick validation check
                return engine.validate();
            } catch (Exception e) {
                log.warn("Yjs engine #{} validation failed", engine.getId(), e);
                return false;
            }
        }

        @Override
        public void activateObject(PooledObject<YjsEngine> p) {
            // Reset any engine state if needed
            p.getObject().reset();
        }

        @Override
        public void passivateObject(PooledObject<YjsEngine> p) {
            // Clean up before returning to pool
            p.getObject().cleanup();
        }
    }

    /**
     * Get pool statistics
     */
    public PoolStatistics getStatistics() {
        return PoolStatistics.builder()
                .numActive(pool.getNumActive())
                .numIdle(pool.getNumIdle())
                .numWaiters(pool.getNumWaiters())
                .maxTotal(pool.getMaxTotal())
                .createdCount(pool.getCreatedCount())
                .borrowedCount(pool.getBorrowedCount())
                .returnedCount(pool.getReturnedCount())
                .destroyedCount(pool.getDestroyedCount())
                .build();
    }

    /**
     * Functional interface for Yjs operations
     */
    @FunctionalInterface
    public interface YjsOperation<T> {
        T execute(YjsEngine engine) throws Exception;
    }

    /**
     * Pool statistics
     */
    @lombok.Builder
    @lombok.Getter
    public static class PoolStatistics {
        private final int numActive;
        private final int numIdle;
        private final int numWaiters;
        private final int maxTotal;
        private final long createdCount;
        private final long borrowedCount;
        private final long returnedCount;
        private final long destroyedCount;
    }
}
