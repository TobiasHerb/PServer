package de.tuberlin.pserver.runtime.partitioning.mtxentries;

/**
 * Represents a reusable matrix entry with row, cols and value.
 */
public interface ReusableMatrixEntry extends MatrixEntry {

    public MatrixEntry set(long row, long col, double value);

}