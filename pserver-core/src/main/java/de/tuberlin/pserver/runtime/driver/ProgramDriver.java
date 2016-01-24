package de.tuberlin.pserver.runtime.driver;


import com.google.common.base.Preconditions;
import de.tuberlin.pserver.compiler.Compiler;
import de.tuberlin.pserver.compiler.*;
import de.tuberlin.pserver.dsl.transaction.TransactionController;
import de.tuberlin.pserver.dsl.unit.UnitMng;
import de.tuberlin.pserver.math.matrix.Matrix;
import de.tuberlin.pserver.math.matrix.MatrixBase;
import de.tuberlin.pserver.runtime.RuntimeContext;
import de.tuberlin.pserver.runtime.core.common.Deactivatable;
import de.tuberlin.pserver.runtime.core.infra.InfrastructureManager;
import de.tuberlin.pserver.runtime.core.usercode.UserCodeManager;
import de.tuberlin.pserver.runtime.events.ProgramSubmissionEvent;
import de.tuberlin.pserver.runtime.state.matrix.MatrixLoader;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class ProgramDriver implements Deactivatable {

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private final InfrastructureManager infraManager;

    private final UserCodeManager userCodeManager;

    private final RuntimeContext runtimeContext;

    // ---------------------------------------------------

    private ProgramContext programContext;

    private ProgramTable programTable;

    private Program instance;


    private Map<String, Object> remoteObjectRefs;

    private GlobalObjectAllocator globalObjectAllocator;

    private StateAllocator stateAllocator;

    private MatrixLoader matrixLoader;

    // ---------------------------------------------------
    // Constructor.
    // ---------------------------------------------------

    public ProgramDriver(final InfrastructureManager infraManager,
                         final UserCodeManager userCodeManager,
                         final RuntimeContext runtimeContext) {

        this.infraManager = Preconditions.checkNotNull(infraManager);
        this.userCodeManager = Preconditions.checkNotNull(userCodeManager);
        this.runtimeContext = Preconditions.checkNotNull(runtimeContext);
    }

    public void deactivate() {
        programContext.deactivate();
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    @SuppressWarnings("unchecked")
    public Program install(final ProgramSubmissionEvent submissionEvent) throws Exception {
        Preconditions.checkNotNull(submissionEvent);

        final int nodeDOP = infraManager.getMachines().size();
        final Class<? extends Program> programClass = (Class<? extends Program>) userCodeManager.implantClass(submissionEvent.byteCode);
        this.instance = programClass.newInstance();
        this.programTable = new Compiler(runtimeContext, programClass).compile(instance, nodeDOP);

        this.programContext = new ProgramContext(
                runtimeContext,
                submissionEvent.clientMachine,
                submissionEvent.programID,
                programClass.getName(),
                programClass.getSimpleName(),
                programTable,
                nodeDOP
        );

        this.matrixLoader = new MatrixLoader(programContext);
        this.stateAllocator = new StateAllocator();
        this.globalObjectAllocator = new GlobalObjectAllocator();
        this.remoteObjectRefs = new HashMap<>();

        instance.injectContext(programContext);

        return instance;
    }

    public void run() throws Exception {

        try {
            Thread.sleep(7000); // TODO: REMOVE !!! FEHLER!!!!!
        } catch (InterruptedException ex) {
            //throw new IllegalStateException(ex);
        }

        //
        // PSERVER ML PROGRAM EXECUTION LIFECYCLE!
        //

        allocateGlobalObjects();

        bindGlobalObjects(instance);

        allocateState();

        bindState(instance);

        bindTransactions();

        defineProgram(instance);

        programContext.synchronizeUnit(UnitMng.GLOBAL_BARRIER);

        instance.run();
    }

    // ---------------------------------------------------
    // Private Methods.
    // ---------------------------------------------------

    private void allocateGlobalObjects() throws Exception {
        for (final GlobalObjectDescriptor globalObject : programContext.programTable.getGlobalObjects()) {
            remoteObjectRefs.put(globalObject.stateName, globalObjectAllocator.alloc(programContext, globalObject));
        }
    }

    private void bindGlobalObjects(final Program instance) throws Exception {
        for (final GlobalObjectDescriptor globalObject : programContext.programTable.getGlobalObjects()) {
            final Field field = programTable.getProgramClass().getDeclaredField(globalObject.stateName);
            field.set(instance, remoteObjectRefs.get(globalObject.stateName));
        }
    }

    private void allocateState() throws Exception {
        for (final StateDescriptor state : programContext.programTable.getState()) {
            if (MatrixBase.class.isAssignableFrom(state.stateType)) {
                final Pair<MatrixBase, MatrixBase> stateAndProxy = stateAllocator.alloc(programContext, state);
                if (stateAndProxy.getLeft() != null) {
                    runtimeContext.runtimeManager.putDHT(state.stateName, stateAndProxy.getLeft());
                    if (!("".equals(state.path)))
                        matrixLoader.add(state, (Matrix)stateAndProxy.getLeft());
                }
                if (stateAndProxy.getRight() != null)
                    remoteObjectRefs.put(state.stateName, stateAndProxy.getRight());
            } else
                throw new IllegalStateException();
        }

        programContext.synchronizeUnit(UnitMng.GLOBAL_BARRIER);
        matrixLoader.load();
    }

    private void bindState(final Program instance) throws Exception {
        for (final StateDescriptor state : programTable.getState()) {
            final Field field = programTable.getProgramClass().getDeclaredField(state.stateName);
            final Object stateObj;
            if (ArrayUtils.contains(state.atNodes, programContext.nodeID)) {
                stateObj = runtimeContext.runtimeManager.getDHT(state.stateName);
            } else {
                stateObj = remoteObjectRefs.get(state.stateName);
            }

            Preconditions.checkState(stateObj != null, "State object '" + state.stateName
                    + "' not found at Node [" + runtimeContext.nodeID + "].");

            field.set(instance, stateObj);
        }
    }

    private void bindTransactions() throws Exception {
        for (final TransactionController transactionController : programTable.getTransactionControllers()) {
            transactionController.bindTransaction();
        }
    }

    private void defineProgram(final Program instance) {
        for (final UnitDescriptor unit : programTable.getUnits()) {
            if (ArrayUtils.contains(unit.atNodes, runtimeContext.nodeID)) {
                try {
                    unit.method.invoke(instance, instance.getLifecycle());
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }
}
