package de.tuberlin.pserver.crdt;

import de.tuberlin.pserver.crdt.operations.EndOperation;
import de.tuberlin.pserver.crdt.operations.Operation;
import de.tuberlin.pserver.runtime.DataManager;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;


// TODO: what about exceptions in general
// TODO: what about when counters reach MAX_INT => exception or keep counting somehow?
// TODO: what if someone uses the same id for two crdts?
// TODO: what if only one node is running?
// TODO: maybe use blocking queues for buffers
// TODO: comments and documentation
// TODO: improve the P2P discovery and termination
// TODO: what if there is not one replica on every node? => pass number of replicas into constructor!?
// TODO: change constructor to CRDT.newReplica(...) to reflect that it is replicas being dealt with?
// TODO: Javadoc package descriptions

/**
 * <p>
 * This class provides a skeletal implementation of the {@code CRDT} interface, to minimize the effort required to
 * implement this interface in subclasses.
 *</p>
 *<p>
 * In particular, this class provides functionality for starting/finishing a CRDT and broadcasting/receiving updates.
 *</p>
 *<p>
 * To implement a CRDT, the programmer needs to extend this class, implement the desired local data structure of one
 * replica, call the {@code broadcast} method when operations should be sent to all replicas and provide an
 * implementation of the {@code update} method. All further communication between replicas and starting/finishing of the
 * CRDT are handled by this class.
 *</p>
 *<p>
 * When extending this class make sure to call the {@code super()} constructor which will enable the above functionalities.
 *</p>
 *
 * @param <T> the type of elements in this CRDT
 */
public abstract class AbstractCRDT<T> implements CRDT {

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private final Set<Integer> runningNodes;

    private final Set<Integer> finishedNodes;

    private final Queue<Operation> buffer;

    private final String id;

    private final DataManager dataManager;

    private boolean allNodesRunning;

    private boolean allNodesFinished;

    // ---------------------------------------------------
    // Constructor.
    // ---------------------------------------------------

    /** Sole constructor
     *
     * @param id the ID of the CRDT that this replica belongs to
     * @param dataManager the {@code DataManager} belonging to this {@code MLProgram}
     * */
    protected AbstractCRDT(String id, DataManager dataManager) {
        this.runningNodes = new HashSet<>();
        this.finishedNodes = new HashSet<>();
        this.buffer = new LinkedList<>();

        this.dataManager = dataManager;
        this.id = id;

        this.allNodesRunning = false;
        this.allNodesFinished = false;

        dataManager.addDataEventListener("Running_"+id, new DataManager.DataEventHandler() {
            @Override
            public void handleDataEvent(int srcNodeID, Object value) {
                runningNodes.add(srcNodeID);
                //System.out.println(runningNodes.size());

                if (runningNodes.size() == dataManager.remoteNodeIDs.length) {
                    allNodesRunning = true;
                    dataManager.removeDataEventListener("Running_"+id, this);

                    // This is necessary to reach replicas that were not online when the first "Running" message was sent
                    dataManager.pushTo("Running_"+id, 0, dataManager.remoteNodeIDs);
                }
            }
        });

        dataManager.addDataEventListener("Operation_" + id, new DataManager.DataEventHandler() {
            @Override
            public void handleDataEvent(int srcNodeID, Object value) {
                if(value instanceof Operation) {
                    //Suppress the unchecked warning cause by generics cast from object to Operation<T>
                    @SuppressWarnings("unchecked")
                    Operation<?> op = (Operation<?>) value;
                    if (op.getType() == Operation.END) {
                        addFinishedNode(srcNodeID);
                    } else {
                            update(srcNodeID, op);
                    }}
            }
        });

        ready();
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    /**
     * Should be called when a CRDT replica is finished producing and applying local updates. It will cause this replica
     * to wait for all replicas of this CRDT to finish and will apply any updates received to reach the replica's final
     * state. (This is a blocking call)
     */
    // TODO: this is blocking if not all nodes start/finish...
    @Override
    public final void finish() {
        System.out.println("[DEBUG] All nodes: " + isAllNodesRunning());

        while(!isAllNodesRunning()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("[DEBUG] All nodes: " + isAllNodesRunning());
        System.out.println("[DEBUG] BufferB: " + buffer.size());

        broadcast(new EndOperation());

        while(!isAllNodesFinished()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Gets the {@code buffer} queue associated with this CRDT replica. The buffer contains operations applied to the
     * replica locally but not yet broadcast to other replicas.
     *
     * @return a copy of the CRDTs current buffer
     */
    public final Queue getBuffer() {
        return new LinkedList<>(this.buffer);
    }

    // ---------------------------------------------------
    // Protected Methods.
    // ---------------------------------------------------

    /**
     * Broadcasts an {@code Operation} to all replicas belonging to this CRDT (same {@code id}) or buffers the
     * {@code Operation} for later broadcasting if not all replicas are online yet.
     *
     * @param op the operation that should be broadcast
     */
    protected final void broadcast(Operation op) {
        // send to all nodes
        if(isAllNodesRunning()) {
            broadcastBuffer();
            dataManager.pushTo("Operation_"+id, op, dataManager.remoteNodeIDs);
        } else {
            buffer(op);
        }
    }

    /**
     * Applies an {@code Operation} received from another replica to the local replica of a CRDT.
     *
     * @param srcNodeId the id of the node that broadcast the operation
     * @param op the operation to be applied locally
     * @return true if the operation was successfully applied
     */
    protected abstract boolean update(int srcNodeId, Operation<?> op);

    // ---------------------------------------------------
    // Private Methods.
    // ---------------------------------------------------

    private void ready() {
        dataManager.pushTo("Running_" + id, 0, dataManager.remoteNodeIDs);
    }

    private boolean isAllNodesRunning() {
        return allNodesRunning;
    }

    private boolean isAllNodesFinished() {
        return allNodesFinished;
    }

    private void buffer(Operation op) {
        buffer.add(op);
    }

    private boolean broadcastBuffer() {
        if(buffer.size() > 0) {
            System.out.println("[DEBUG] Broadcasting buffer size " + buffer.size());
            while (buffer.size() > 0) {
                dataManager.pushTo("Operation_" + id, buffer.poll(), dataManager.remoteNodeIDs);
            }
            return true;
        }
        return false;
    }

    private void addFinishedNode(int nodeID) {
        finishedNodes.add(nodeID);

        if(finishedNodes.size() == dataManager.remoteNodeIDs.length) {
            allNodesFinished = true;
        }
    }
}