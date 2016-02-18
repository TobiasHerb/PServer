package de.tuberlin.pserver.runtime.state.matrix;

import de.tuberlin.pserver.runtime.driver.ProgramContext;
import de.tuberlin.pserver.runtime.filesystem.FileDataIterator;
import de.tuberlin.pserver.runtime.filesystem.FileSystemManager;
import de.tuberlin.pserver.runtime.filesystem.records.Entry32F;
import de.tuberlin.pserver.runtime.filesystem.records.Record;
import de.tuberlin.pserver.types.matrix.implementation.Matrix32F;
import de.tuberlin.pserver.types.matrix.implementation.matrix32f.sparse.CSRMatrix32F;
import de.tuberlin.pserver.types.typeinfo.DistributedTypeInfo;
import gnu.trove.map.hash.TIntFloatHashMap;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public final class MatrixLoader {

    // ---------------------------------------------------
    // Inner Classes.
    // ---------------------------------------------------

    private static abstract class MatrixLoaderStrategy {

        protected final Matrix32F matrix;

        public MatrixLoaderStrategy(Matrix32F matrix)  { this.matrix = matrix; }

        public void done(Matrix32F dataMatrix) {}

        abstract public void putRecord(Record record, Matrix32F dataMatrix, Matrix32F labelMatrix);

        // Factory Method.
        public static MatrixLoaderStrategy createLoader(DistributedTypeInfo state) {
            if (CSRMatrix32F.class.isAssignableFrom(state.type()))
                return new CSRMatrix32LoaderStrategy((Matrix32F) state);
            if (Matrix32F.class.isAssignableFrom(state.type()))
                return new Matrix32LoaderStrategy((Matrix32F) state);
            throw new IllegalStateException();
        }
    }

    // ---------------------------------------------------

    private final static class Matrix32LoaderStrategy extends MatrixLoaderStrategy {

        private final Entry32F reusable = new Entry32F(-1, -1, Float.NaN);
        public Matrix32LoaderStrategy(Matrix32F matrix)  { super(matrix); }

        @Override
        public void putRecord(Record record, Matrix32F dataMatrix, Matrix32F labelMatrix) {
            while (record.hasNext()) { // Iterate through entries in record...
                final Entry32F entry = record.next(reusable);
                if (entry.getRow() > matrix.rows() || entry.getCol() > matrix.cols())
                    return;
                if (labelMatrix != null && entry.getCol() == 0) // Label always on first column.
                    labelMatrix.set(record.getRow() - labelMatrix.partitioner().matrixPartitionShape().rowOffset, entry.getCol(), record.getLabel());
                else
                    dataMatrix.set(entry.getRow() - dataMatrix.partitioner().matrixPartitionShape().rowOffset, entry.getCol() - ((labelMatrix != null) ? 1 : 0), entry.getValue());
            }
        }
    }

    private final static class CSRMatrix32LoaderStrategy extends MatrixLoaderStrategy {

        private final Entry32F reusable = new Entry32F(-1, -1, Float.NaN);
        private final TIntFloatHashMap rowData = new TIntFloatHashMap();

        public CSRMatrix32LoaderStrategy(Matrix32F matrix)  { super(matrix); }

        @Override
        public void putRecord(Record record, Matrix32F dataMatrix, Matrix32F labelMatrix) {
            while (record.hasNext()) { // Iterate through entries in record...
                final Entry32F entry = record.next(reusable);
                if (entry.getRow() > matrix.rows() || entry.getCol() > matrix.cols())
                    continue;
                if (labelMatrix != null && entry.getCol() == 0) // Label always on first column.
                    labelMatrix.set(record.getRow(), entry.getCol(), record.getLabel()); // TODO: VERIFY THAT!!!!!!!!!!!!!!!!!!!!
                else {
                    rowData.put((int) (entry.getCol() - ((labelMatrix != null) ? 1 : 0) % matrix.cols()), entry.getValue());
                }
            }
            ((CSRMatrix32F) dataMatrix).addRow(rowData);
            rowData.clear();
        }

        @Override
        public void done(Matrix32F dataMatrix) {
            ((CSRMatrix32F) dataMatrix).build();
        }
    }

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private final ProgramContext programContext;

    private final FileSystemManager fileManager;

    private final List<Pair<DistributedTypeInfo, FileDataIterator>> loadingTasks;

    // ---------------------------------------------------
    // Constructor.
    // ---------------------------------------------------

    public MatrixLoader(ProgramContext programContext) {
        this.programContext = programContext;
        this.fileManager    = programContext.runtimeContext.fileManager;
        this.loadingTasks   = new ArrayList<>();
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    public void add(DistributedTypeInfo state, Matrix32F stateObj) {
        FileDataIterator fileIterator = fileManager.createFileIterator(programContext, state);
        loadingTasks.add(Pair.of(state, fileIterator));
    }

    @SuppressWarnings("unchecked")
    public void load() {
        programContext.runtimeContext.fileManager.computeInputSplitsForRegisteredFiles();
        for (Pair<DistributedTypeInfo, FileDataIterator> task : loadingTasks) {
            DistributedTypeInfo state = task.getLeft();

            Matrix32F dataMatrix = (Matrix32F)state;
            FileDataIterator<Record> fileIterator = task.getRight();
            Matrix32F labelMatrix = null;
            if (!"".equals(state.input().labels())) {
                labelMatrix = programContext.runtimeContext.runtimeManager.getDHT(state.input().labels());
            }
            final MatrixLoaderStrategy loader = MatrixLoaderStrategy.createLoader(state);
            while (fileIterator.hasNext())
                loader.putRecord(fileIterator.next(), dataMatrix, labelMatrix);
            loader.done(dataMatrix);
        }
    }
}