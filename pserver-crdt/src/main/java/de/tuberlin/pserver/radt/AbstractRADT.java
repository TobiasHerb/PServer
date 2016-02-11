package de.tuberlin.pserver.radt;

import de.tuberlin.pserver.AbstractReplicatedDataType;
import de.tuberlin.pserver.crdt.operations.Operation;
import de.tuberlin.pserver.runtime.driver.ProgramContext;
import de.tuberlin.pserver.runtime.events.MsgEventHandler;

import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Queue;

public abstract class AbstractRADT<T> extends AbstractReplicatedDataType<T> implements RADT {

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    protected final int[] vectorClock;
    // priority queue
    private final Queue<RADTOperation<CObject<T>>> queue;

    // ---------------------------------------------------
    // Constructor.
    // ---------------------------------------------------

    protected AbstractRADT(String id, int noOfReplicas, ProgramContext programContext) {
        super(id, noOfReplicas, programContext);

        // Initialize vector clock
        this.vectorClock = new int[noOfReplicas];
        Arrays.fill(vectorClock, 0);

        // Initialize queue
        this.queue = new PriorityQueue<>((o1, o2) -> {
            if (o1.getValue().getS4Vector().precedes(o2.getValue().getS4Vector())) return -1;
            else if (o2.getValue().getS4Vector().precedes(o1.getValue().getS4Vector())) return 1;
            else return 0;
        });

        runtimeManager.addMsgEventListener("Operation_" + id, new MsgEventHandler() {
            @Override
            public void handleMsg(int srcNodeID, Object value) {
                if (value instanceof Operation) {
                    //Suppress the unchecked warning cause by generics cast from object to Operation<T>
                    @SuppressWarnings("unchecked")
                    Operation op = (Operation) value;

                    if(op.getType() == Operation.OpType.END) {
                        addFinishedNode(srcNodeID);
                        synchronized (AbstractRADT.this) {
                            AbstractRADT.this.notifyAll();
                        }
                    } else {
                        @SuppressWarnings("unchecked")
                        RADTOperation<CObject<T>> radtOp = (RADTOperation<CObject<T>>) value;
                        //System.out.println("[" + nodeId + "] Received " + ((ArrayOperation<T>)radtOp).getValue() + "; " + radtOp.getS4Vector());
                        queue.add(radtOp);

                        while(queue.peek() != null && isCausallyReadyFor(queue.peek())) {
                            radtOp = queue.poll();
                            updateVectorClock(radtOp.getVectorClock());
                            update(srcNodeID, radtOp);
                        }
                    }
                }
            }
        });

        // Start the RADT
        //ready();
    }

    // ---------------------------------------------------
    // Protected Methods.
    // ---------------------------------------------------

    protected synchronized boolean isCausallyReadyFor(RADTOperation<CObject<T>> op) {
        // TODO: what about if sum > vectorClockSum => must the operation be purged from queue?
        // TODO: this needs verification
        //System.out.println();
        //System.out.println("Local vector clock: " + vectorClock[op.getS4Vector().getSiteId()]);
        //System.out.println("Remote vector clock: " + op.getVectorClock()[op.getS4Vector().getSiteId()]);
        return vectorClock[op.getS4Vector().getSiteId()] == (op.getVectorClock()[op.getS4Vector().getSiteId()] - 1);
    }

    protected synchronized int[] increaseVectorClock() {
        vectorClock[nodeId]++;
        return vectorClock.clone();
    }

    protected synchronized void updateVectorClock(int[] remoteVectorClock) {
        for(int i = 0; i < remoteVectorClock.length; i++) {
            vectorClock[i] = Math.max(vectorClock[i], remoteVectorClock[i]);
        }
    }

    // ---------------------------------------------------
    // Private Methods.
    // ---------------------------------------------------
}