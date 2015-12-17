package de.tuberlin.pserver.runtime.filesystem.record;

import de.tuberlin.pserver.runtime.state.mtxentries.MatrixEntry;
import de.tuberlin.pserver.runtime.state.mtxentries.ReusableMatrixEntry;
import org.apache.commons.csv.CSVRecord;

import java.util.Iterator;

public interface IRecord extends Iterator<MatrixEntry> {

    int size();

    MatrixEntry get(int i);

    public MatrixEntry next(ReusableMatrixEntry reusable);

    //public IRecord set(String[] record, int[] projection, long row);

    public IRecord set(CSVRecord delegate, int[] projection, long row);
}