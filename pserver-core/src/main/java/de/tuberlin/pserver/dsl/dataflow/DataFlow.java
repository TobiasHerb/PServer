package de.tuberlin.pserver.dsl.dataflow;

import com.google.common.base.Preconditions;
import de.tuberlin.pserver.dsl.state.StateDeclaration;
import de.tuberlin.pserver.dsl.state.properties.RemoteUpdate;
import de.tuberlin.pserver.math.SharedObject;
import de.tuberlin.pserver.runtime.DataManager;
import de.tuberlin.pserver.runtime.MLProgramContext;
import de.tuberlin.pserver.runtime.MLProgramLinker;
import de.tuberlin.pserver.runtime.SlotContext;
import de.tuberlin.pserver.runtime.dht.DHTKey;
import de.tuberlin.pserver.runtime.state.controller.RemoteUpdateController;

import java.util.List;
import java.util.StringTokenizer;

public final class DataFlow {

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private final SlotContext slotContext;

    private final DataManager dataManager;

    private final MLProgramContext programContext;

    // ---------------------------------------------------
    // Constructor.
    // ---------------------------------------------------

    public DataFlow(final SlotContext slotContext) {

        this.slotContext = Preconditions.checkNotNull(slotContext);

        this.dataManager = slotContext.runtimeContext.dataManager;

        this.programContext = slotContext.programContext;
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    public <T extends SharedObject> DHTKey put(final String name, final T obj) { return dataManager.putObject(name, obj); }

    public <T extends SharedObject> T get(final String name) { return dataManager.getObject(name); }

    public void publishUpdate(final String objNames) throws Exception {
        Preconditions.checkNotNull(objNames);
        final StringTokenizer st = new StringTokenizer(objNames, ",");
        while (st.hasMoreTokens()) {
            final String name = st.nextToken().replaceAll("\\s+", "");
            final RemoteUpdateController remoteUpdateController =
                    (RemoteUpdateController) programContext.get(MLProgramLinker.remoteUpdateControllerName(name));
            remoteUpdateController.publishUpdate(slotContext);
        }
    }

    public void publishUpdate() throws Exception {
        @SuppressWarnings("unchecked")
        final List<StateDeclaration> stateDecls = (List<StateDeclaration>)
                programContext.get(MLProgramLinker.stateDeclarationListName());
        for (final StateDeclaration stateDecl : stateDecls) {
            if (stateDecl.remoteUpdate != RemoteUpdate.NO_UPDATE) {
                publishUpdate(stateDecl.name);
            }
        }
    }

    public void pullUpdate(final String objNames) throws Exception {
        Preconditions.checkNotNull(objNames);
        final StringTokenizer st = new StringTokenizer(objNames, ",");
        while (st.hasMoreTokens()) {
            final String name = st.nextToken().replaceAll("\\s+", "");
            final RemoteUpdateController remoteUpdateController =
                    (RemoteUpdateController) programContext.get(MLProgramLinker.remoteUpdateControllerName(name));
            remoteUpdateController.pullUpdate(slotContext);
        }
    }

    public void pullUpdate() throws Exception {
        @SuppressWarnings("unchecked")
        final List<StateDeclaration> stateDecls = (List<StateDeclaration>)
                programContext.get(MLProgramLinker.stateDeclarationListName());
        for (final StateDeclaration stateDecl : stateDecls) {
            if (stateDecl.remoteUpdate != RemoteUpdate.NO_UPDATE) {
                pullUpdate(stateDecl.name);
            }
        }
    }
}