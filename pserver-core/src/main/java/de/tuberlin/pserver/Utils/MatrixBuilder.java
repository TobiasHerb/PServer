package de.tuberlin.pserver.utils;


import com.google.common.base.Preconditions;
import de.tuberlin.pserver.compiler.StateDescriptor;
import de.tuberlin.pserver.math.Format;
import de.tuberlin.pserver.math.Layout;
import de.tuberlin.pserver.math.matrix.Matrix;
import de.tuberlin.pserver.math.matrix.dense.Dense64Matrix;
import de.tuberlin.pserver.math.matrix.sparse.Sparse64Matrix;
import de.tuberlin.pserver.math.utils.Utils;
import de.tuberlin.pserver.runtime.ProgramContext;
import de.tuberlin.pserver.runtime.partitioning.IMatrixPartitioner;
import de.tuberlin.pserver.types.DistributedMatrix;

public final class MatrixBuilder {

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private long rows, cols;

    private Format format;

    private Layout layout;

    private double[] data;

    // ---------------------------------------------------
    // Constructor.
    // ---------------------------------------------------

    public MatrixBuilder() {
        reset();
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    public MatrixBuilder dimension(final long rows, final long cols) {
        this.rows = rows;
        this.cols = cols;
        return this;
    }

    public MatrixBuilder format(final Format format) {
        this.format = Preconditions.checkNotNull(format);
        return this;
    }

    public MatrixBuilder layout(final Layout layout) {
        this.layout = Preconditions.checkNotNull(layout);
        return this;
    }

    public MatrixBuilder data(final double[] data) {
        this.data = Preconditions.checkNotNull(data);
        return this;
    }

    public static Matrix fromMatrixLoadTask(StateDescriptor decl, ProgramContext programContext) {
        switch (decl.scope) {
            case REPLICATED:
                return new MatrixBuilder()
                        .dimension(decl.rows, decl.cols)
                        .format(decl.format)
                        .layout(decl.layout)
                        .build();

            case PARTITIONED:
                return new DistributedMatrix(
                        programContext,
                        decl.rows,
                        decl.cols,
                        IMatrixPartitioner.newInstance(decl.partitionerClass, decl.rows, decl.cols, programContext.runtimeContext.nodeID, decl.atNodes),
                        decl.layout,
                        decl.format
                        //, false
                );
            case LOGICALLY_PARTITIONED:
                return new DistributedMatrix(
                        programContext,
                        decl.rows,
                        decl.cols,
                        IMatrixPartitioner.newInstance(decl.partitionerClass, decl.rows, decl.cols, programContext.runtimeContext.nodeID, decl.atNodes),
                        decl.layout,
                        decl.format
                        //, true
                );

        }
        throw new IllegalStateException("Unkown scope: " + decl.scope.toString());
    }

    public Matrix build() {
        switch (format) {
            case SPARSE_FORMAT:
                    return new Sparse64Matrix(rows, cols, layout);
            case DENSE_FORMAT:
                    if (data == null)
                        return new Dense64Matrix(rows, cols, new double[Utils.toInt(rows * cols)], layout);
                    else
                        return new Dense64Matrix(rows, cols, data, layout);
        }
        throw new IllegalStateException();
    }

    // ---------------------------------------------------
    // Private Methods.
    // ---------------------------------------------------

    private void reset() {
        rows    = -1;
        cols    = -1;
        format  = Format.DENSE_FORMAT;
        layout  = Layout.ROW_LAYOUT;
    }
}