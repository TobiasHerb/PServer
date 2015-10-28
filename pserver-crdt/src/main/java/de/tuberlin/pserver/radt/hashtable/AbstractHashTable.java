package de.tuberlin.pserver.radt.hashtable;

import de.tuberlin.pserver.radt.AbstractRADT;
import de.tuberlin.pserver.radt.Slot;
import de.tuberlin.pserver.runtime.RuntimeManager;

import java.util.Hashtable;

public abstract class AbstractHashTable<K,V> extends AbstractRADT<V> implements IHashTable<V> {
    protected final Hashtable<K, Slot<K,V>> hashTable;
    protected final Cemetery<Slot<K,V>> cemetery;

    protected AbstractHashTable(int size, String id, int noOfReplicas, RuntimeManager runtimeManager) {
        super(size, id, noOfReplicas, runtimeManager);

        // Initialize HashTable
        this.hashTable = new Hashtable<>();

        // Initialize Cemetery
        this.cemetery = new Cemetery<>();
    }
}