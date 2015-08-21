package de.tuberlin.pserver.math.matrix;


import com.google.common.base.Preconditions;
import de.tuberlin.pserver.math.Format;
import de.tuberlin.pserver.math.Layout;
import de.tuberlin.pserver.math.matrix.dense.DMatrix;
import de.tuberlin.pserver.math.matrix.sparse.SMutableMatrix;
import de.tuberlin.pserver.math.utils.Utils;

public class MatrixBuilder {

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private long rows, cols;

    private Format format;

    private Layout layout;

    private boolean mutable = true;

    private double[] data;

    private int initialCapacity = -1;

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

    public MatrixBuilder mutable(final boolean mutable) {
        this.mutable = mutable;
        return this;
    }

    public MatrixBuilder data(final double[] data) {
        this.data = Preconditions.checkNotNull(data);
        return this;
    }

    public MatrixBuilder initialCapacity(final int initialCapacity) {
        this.initialCapacity = initialCapacity;
        return this;
    }

    public Matrix build() {
        switch (format) {
            case SPARSE_FORMAT:
                if (mutable) {
                    return new SMutableMatrix(rows, cols, layout, initialCapacity);
                } else {
                    return new SMutableMatrix(rows, cols, layout, initialCapacity);
                }
            case DENSE_FORMAT:
                if (mutable) {
                    if (data == null) {
                        return new DMatrix(rows, cols, new double[Utils.toInt(rows * cols)], layout);
                    }
                    return new DMatrix(rows, cols, data, layout);
                }
                else {
                    throw new UnsupportedOperationException("");
                }
        }
        throw new IllegalStateException();
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    private void reset() {
        rows    = -1;
        cols    = -1;
        format  = Format.DENSE_FORMAT;
        layout  = Layout.ROW_LAYOUT;
        mutable = true;
    }
}
