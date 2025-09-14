package com.project.collab_docs.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.Base64;
import java.util.List;

/**
 * Wrapper for a GraalVM JavaScript context running Yjs.
 * Provides thread-safe access to Yjs operations.
 */
@Slf4j
@Getter
public class YjsEngine implements AutoCloseable{

    private final int id;
    private final Context context;
    private final Value yjsHelpers;
    private volatile boolean closed = false;

    public YjsEngine(int id, Context context) {
        this.id = id;
        this.context = context;
        this.yjsHelpers = context.eval("js", "globalThis.YjsHelpers");

        if (yjsHelpers == null || yjsHelpers.isNull()) {
            throw new IllegalStateException("YjsHelpers not available in context");
        }
    }

    public byte[] mergeUpdate(byte[] currentState, byte[] update) {
        try{
            String currentStateBase64 = currentState != null ?
                    Base64.getEncoder().encodeToString(currentState) : "";
            String updateBase64 = update != null ?
                    Base64.getEncoder().encodeToString(update) : "";

            Value result = yjsHelpers.getMember("mergeUpdate")
                    .execute(currentStateBase64, updateBase64);

            if (result == null || result.isNull()) {
                throw new RuntimeException("Merge operation returned null");
            }

            String mergedBase64 = result.asString();
            return Base64.getDecoder().decode(mergedBase64);
        }catch (Exception e){
            log.error("Error merging Yjs update in engine #{}", id, e);
            throw new RuntimeException("Failed to merge Yjs update", e);
        }
    }

    /**
     * Merge multiple updates efficiently
     * @param baseState Base document state (can be null)
     * @param updates List of updates to apply in order
     * @return The final merged state
     */
    public byte[] mergeMultipleUpdates(byte[] baseState, List<byte[]> updates){
        try {
            String baseStateBase64 = baseState != null ?
                    Base64.getEncoder().encodeToString(baseState) : "";

            String[] updatesBase64 = updates.stream()
                    .map(update -> update != null ?
                            Base64.getEncoder().encodeToString(update) : "")
                    .toArray(String[]::new);

            Value result = yjsHelpers.getMember("mergeMultipleUpdates")
                    .execute(baseStateBase64, updatesBase64);

            if (result == null || result.isNull()) {
                throw new RuntimeException("Merge multiple operation returned null");
            }

            String mergedBase64 = result.asString();
            return Base64.getDecoder().decode(mergedBase64);
        }catch (Exception e) {
            log.error("Error merging multiple Yjs updates in engine #{}", id, e);
            throw new RuntimeException("Failed to merge multiple Yjs updates", e);
        }
    }

    /**
     * Get state vector for synchronization
     * @param state Current document state
     * @return State vector for sync
     */
    public byte[] getStateVector(byte[] state) {
        try {
            String stateBase64 = state != null ?
                    Base64.getEncoder().encodeToString(state) : "";

            Value result = yjsHelpers.getMember("getStateVector")
                    .execute(stateBase64);

            if (result == null || result.isNull()) {
                return new byte[0];
            }

            String vectorBase64 = result.asString();
            return Base64.getDecoder().decode(vectorBase64);

        } catch (Exception e) {
            log.error("Error getting state vector in engine #{}", id, e);
            throw new RuntimeException("Failed to get state vector", e);
        }
    }

    /**
     * Compute diff between two states
     * @param fromState The starting state
     * @param toState The target state
     * @return The diff that transforms fromState to toState
     */
    public byte[] computeDiff(byte[] fromState, byte[] toState) {
        try {
            String fromStateBase64 = fromState != null ?
                    Base64.getEncoder().encodeToString(fromState) : "";
            String toStateBase64 = toState != null ?
                    Base64.getEncoder().encodeToString(toState) : "";

            Value result = yjsHelpers.getMember("computeDiff")
                    .execute(fromStateBase64, toStateBase64);

            if (result == null || result.isNull()) {
                return new byte[0];
            }

            String diffBase64 = result.asString();
            return Base64.getDecoder().decode(diffBase64);

        } catch (Exception e) {
            log.error("Error computing diff in engine #{}", id, e);
            throw new RuntimeException("Failed to compute diff", e);
        }
    }

    /**
     * Validate if an update is valid
     * @param update The update to validate
     * @return true if valid, false otherwise
     */
    public boolean validateUpdate(byte[] update) {
        try {
            if (update == null || update.length == 0) {
                return false;
            }

            String updateBase64 = Base64.getEncoder().encodeToString(update);

            Value result = yjsHelpers.getMember("validateUpdate")
                    .execute(updateBase64);

            return result != null && result.asBoolean();

        } catch (Exception e) {
            log.warn("Update validation failed in engine #{}", id, e);
            return false;
        }
    }

    /**
     * Get a snapshot for persistence
     * @param state Current document state
     * @return Snapshot suitable for database storage
     */
    public byte[] getSnapshot(byte[] state) {
        try {
            String stateBase64 = state != null ?
                    Base64.getEncoder().encodeToString(state) : "";

            Value result = yjsHelpers.getMember("getSnapshot")
                    .execute(stateBase64);

            if (result == null || result.isNull()) {
                return new byte[0];
            }

            String snapshotBase64 = result.asString();
            return Base64.getDecoder().decode(snapshotBase64);

        } catch (Exception e) {
            log.error("Error getting snapshot in engine #{}", id, e);
            throw new RuntimeException("Failed to get snapshot", e);
        }
    }

    /**
     * Restore from a snapshot
     * @param snapshot The snapshot to restore from
     * @param stateVector Optional state vector for partial restore
     * @return The restored state
     */
    public byte[] restoreFromSnapshot(byte[] snapshot, byte[] stateVector) {
        try {
            String snapshotBase64 = snapshot != null ?
                    Base64.getEncoder().encodeToString(snapshot) : "";
            String stateVectorBase64 = stateVector != null ?
                    Base64.getEncoder().encodeToString(stateVector) : null;

            Value result;
            if (stateVectorBase64 != null) {
                result = yjsHelpers.getMember("restoreFromSnapshot")
                        .execute(snapshotBase64, stateVectorBase64);
            } else {
                result = yjsHelpers.getMember("restoreFromSnapshot")
                        .execute(snapshotBase64);
            }

            if (result == null || result.isNull()) {
                return new byte[0];
            }

            String restoredBase64 = result.asString();
            return Base64.getDecoder().decode(restoredBase64);

        } catch (Exception e) {
            log.error("Error restoring from snapshot in engine #{}", id, e);
            throw new RuntimeException("Failed to restore from snapshot", e);
        }
    }

    /**
     * Validate that the engine is working correctly
     * @return true if engine is healthy
     */
    public boolean validate() {
        if (closed) {
            return false;
        }

        try {
            // Simple validation check
            Value result = context.eval("js", "1 + 1");
            return result != null && result.asInt() == 2;
        } catch (Exception e) {
            log.warn("Engine #{} validation failed", id, e);
            return false;
        }
    }

    /**
     * Reset engine state (called when activated from pool)
     */
    public void reset() {
        if (!closed) {
            try {
                // Clear any temporary variables or state if needed
                context.eval("js", "if (typeof gc === 'function') { gc(); }");
            } catch (Exception e) {
                log.debug("Engine #{} reset warning: {}", id, e.getMessage());
            }
        }
    }

    /**
     * Cleanup engine (called when returned to pool)
     */
    public void cleanup() {
        if (!closed) {
            try {
                // Force garbage collection if available
                context.eval("js", "if (typeof gc === 'function') { gc(); }");
            } catch (Exception e) {
                log.debug("Engine #{} cleanup warning: {}", id, e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                context.close();
                log.debug("Yjs engine #{} closed", id);
            } catch (Exception e) {
                log.error("Error closing Yjs engine #{}", id, e);
            }
        }
    }
}
