package de.tuberlin.pserver.counters;

import de.tuberlin.pserver.crdt.AbstractCRDT;
import de.tuberlin.pserver.runtime.DataManager;

public abstract class AbstractCounter extends AbstractCRDT implements CounterCRDT {
    protected int count = 0;

    public AbstractCounter(String id, DataManager dataManager) {
        super(id, dataManager);
    }

    @Override
    public int getCount() {
        // Defensive copy so the value can not be changed without evoking the add method which broadcasts updates
        // TODO: Does this defensive copy make sense?
        int tmp = count;
        return tmp;
    }
}
