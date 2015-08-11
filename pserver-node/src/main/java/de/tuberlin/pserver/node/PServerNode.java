package de.tuberlin.pserver.node;

import de.tuberlin.pserver.core.events.Event;
import de.tuberlin.pserver.core.events.EventDispatcher;
import de.tuberlin.pserver.core.events.IEventHandler;
import de.tuberlin.pserver.core.infra.InfrastructureManager;
import de.tuberlin.pserver.core.infra.MachineDescriptor;
import de.tuberlin.pserver.core.net.NetManager;
import de.tuberlin.pserver.runtime.*;
import de.tuberlin.pserver.runtime.dht.DHTManager;
import de.tuberlin.pserver.runtime.events.PServerJobFailureEvent;
import de.tuberlin.pserver.runtime.events.PServerJobResultEvent;
import de.tuberlin.pserver.runtime.events.PServerJobSubmissionEvent;
import de.tuberlin.pserver.runtime.usercode.UserCodeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PServerNode extends EventDispatcher {

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private static final Logger LOG = LoggerFactory.getLogger(PServerNode.class);

    private final MachineDescriptor machine;

    private final InfrastructureManager infraManager;

    private final NetManager netManager;

    private final UserCodeManager userCodeManager;

    private final DataManager dataManager;

    private final ExecutionManager executionManager;

    private final ExecutorService executor;

    private CountDownLatch jobStartBarrier = null;

    private CountDownLatch jobEndBarrier = null;

    // ---------------------------------------------------
    // Constructors.
    // ---------------------------------------------------

    public PServerNode(final PServerNodeFactory factory) {
        super(true, "PSERVER-NODE-THREAD");

        this.machine            = factory.machine;
        this.infraManager       = factory.infraManager;
        this.netManager         = factory.netManager;
        this.userCodeManager    = factory.userCodeManager;
        this.dataManager        = factory.dataManager;
        this.executionManager   = factory.executionManager;
        this.executor           = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        netManager.addEventListener(PServerJobSubmissionEvent.PSERVER_JOB_SUBMISSION_EVENT, new PServerJobHandler());
    }

    // ---------------------------------------------------
    // Event Handler.
    // ---------------------------------------------------

    private final class PServerJobHandler implements IEventHandler {

        @Override
        public void handleEvent(final Event e) {
            final PServerJobSubmissionEvent jobSubmission = (PServerJobSubmissionEvent)e;
            LOG.info("Received job on instance " + "[" + infraManager.getNodeID() + "]" + jobSubmission.toString());
            jobStartBarrier = new CountDownLatch(1);
            jobEndBarrier   = new CountDownLatch(jobSubmission.perNodeParallelism);

            final Class<?> clazz = userCodeManager.implantClass(jobSubmission.byteCode);

            if (!JobExecutable.class.isAssignableFrom(clazz))
                throw new IllegalStateException();

            if (jobSubmission.perNodeParallelism > executionManager.getNumOfSlots())
                throw new IllegalStateException();

            @SuppressWarnings("unchecked")
            final Class<? extends JobExecutable> jobClass = (Class<? extends JobExecutable>) clazz;

            final JobContext jobContext = new JobContext(
                    jobSubmission.clientMachine,
                    jobSubmission.jobUID,
                    clazz.getName(),
                    clazz.getSimpleName(),
                    infraManager.getMachines().size(),
                    jobSubmission.perNodeParallelism,
                    infraManager.getNodeID(),
                    netManager,
                    DHTManager.getInstance(),
                    dataManager,
                    executionManager
            );

            executionManager.registerJob(jobContext.jobUID, jobContext);

            for (int i = 0; i < jobContext.numOfInstances; ++i) {
                final int threadID = i;
                executor.execute(() -> {
                    try {
                        final JobExecutable jobInvokeable = jobClass.newInstance();
                        final SlotContext slotContext = new SlotContext(
                                jobContext,
                                threadID,
                                jobInvokeable
                        );

                        executionManager.registerSlotContext(slotContext);
                        jobContext.addInstance(slotContext);
                        jobInvokeable.injectContext(slotContext);
                        executeLifecycle(jobInvokeable);

                        if (threadID == 0) {
                            try {
                                jobEndBarrier.await();
                            } catch (InterruptedException ie) {
                                LOG.error(ie.getMessage());
                            }
                            final List<Serializable> results = dataManager.getResults(jobSubmission.jobUID);
                            final PServerJobResultEvent jre = new PServerJobResultEvent(
                                    machine,
                                    infraManager.getNodeID(),
                                    jobSubmission.jobUID,
                                    results
                            );
                            netManager.sendEvent(jobSubmission.clientMachine, jre);
                            executionManager.unregisterJob(jobContext.jobUID);
                        }

                        jobContext.removeInstance(slotContext);
                        executionManager.unregisterSlotContext();

                    } catch (Exception ex) {
                        final PServerJobFailureEvent jfe = new PServerJobFailureEvent(
                                machine,
                                jobSubmission.jobUID,
                                infraManager.getNodeID(),
                                threadID, clazz.getSimpleName(),
                                ex.getCause()
                        );
                        netManager.sendEvent(jobSubmission.clientMachine, jfe);
                    }
                });
            }
        }
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    @Override
    public void deactivate() {
        netManager.deactivate();
        infraManager.deactivate();
        super.deactivate();
    }

    // ---------------------------------------------------
    // Private Methods.
    // ---------------------------------------------------

    private void executeLifecycle(final JobExecutable job) {
        try {

            if (job.slotContext.slotID == 0) {

                LOG.info("Enter " + job.slotContext.jobContext.simpleClassName + " prologue phase.");

                final long start = System.currentTimeMillis();

                job.prologue();

                dataManager.postProloguePhase(job.slotContext);

                final long end = System.currentTimeMillis();

                LOG.info("Leave " + job.slotContext.jobContext.simpleClassName
                        + " prologue phase [duration: " + (end - start) + " ms].");

                Thread.sleep(5000); // TODO: Not very elegant...

                jobStartBarrier.countDown();
            }

            jobStartBarrier.await();

            {
                LOG.info("Enter " + job.slotContext.jobContext.simpleClassName + " computation phase.");

                final long start = System.currentTimeMillis();

                job.compute();

                final long end = System.currentTimeMillis();

                LOG.info("Leave " + job.slotContext.jobContext.simpleClassName +
                        " computation phase [duration: " + (end - start) + " ms].");
            }

            if (job.slotContext.slotID == 0) {

                LOG.info("Enter " + job.slotContext.jobContext.simpleClassName + " epilogue phase.");

                final long start = System.currentTimeMillis();

                job.epilogue();

                final long end = System.currentTimeMillis();

                LOG.info("Leave " + job.slotContext.jobContext.simpleClassName
                        + " epilogue phase [duration: " + (end - start) + " ms].");
            }

            jobEndBarrier.countDown();

        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
}
