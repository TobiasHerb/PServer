package de.tuberlin.pserver.math.experimental.types.matrices;

import com.google.common.base.Preconditions;
import de.tuberlin.pserver.app.dht.valuetypes.ByteBufferValue;
import de.tuberlin.pserver.math.experimental.memory.TypedBuffer;
import de.tuberlin.pserver.math.experimental.memory.Types;
import de.tuberlin.pserver.math.experimental.types.tests.DenseVectorOld;
import de.tuberlin.pserver.utils.UnsafeOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class DenseMatrix extends ByteBufferValue implements Matrix {

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private static final Logger LOG = LoggerFactory.getLogger(DenseMatrix.class);

    protected final int rows;

    protected final int cols;

    protected final BlockLayout layout;

    protected final Types.TypeInformation elementTypeInfo;

    private transient TypedBuffer typedBuffer;

    // ---------------------------------------------------
    // Constructors.
    // ---------------------------------------------------

    public DenseMatrix(final int rows, final int cols,
                       final Types.TypeInformation elementTypeInfo,
                       final BlockLayout layout,
                       final boolean isAllocated) {

        super(rows * cols * elementTypeInfo.size(), isAllocated);
        this.rows       = rows;
        this.cols       = cols;
        this.layout     = Preconditions.checkNotNull(layout);
        this.elementTypeInfo = Preconditions.checkNotNull(elementTypeInfo);
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    @Override
    public void allocateMemory(final int instanceID) {
        Preconditions.checkState(allocateMemory == false && buffer == null);
        final Types.TypeInformation typeInfo = new Types.TypeInformation(elementTypeInfo, rows * cols);
        buffer = new TypedBuffer(typeInfo);
        typedBuffer = (TypedBuffer) buffer;
    }

    @Override
    public long numRows() { return rows; }

    @Override
    public long numCols() { return cols; }

    @Override
    public Types.TypeInformation getType() { return typedBuffer.getType(); }

    @Override
    public Types.TypeInformation getElementType() { return elementTypeInfo; }

    @Override
    public TypedBuffer getBuffer() { return (TypedBuffer)buffer; }

    @Override
    public byte[] getRow(final long row) {
        switch (layout) {
            case ROW_LAYOUT:
                return typedBuffer.extractElementSequenceAsByteArray(cols * (int) row, cols);
            case COLUMN_LAYOUT:
                throw new NotImplementedException();
        }
        throw new IllegalStateException();
    }

    @Override
    public byte[] getColumn(final long col) {
        switch (layout) {
            case ROW_LAYOUT:
                throw new NotImplementedException();
            case COLUMN_LAYOUT: {
                final byte[] data = new byte[getElementType().size() * rows];
                for (int i = 0; i < rows; ++i)
                    System.arraycopy(
                            buffer.getRawData(),
                            (int)col * elementTypeInfo.size() + (i * cols * elementTypeInfo.size()),
                            data,
                            i * elementTypeInfo.size(), elementTypeInfo.size()
                    );
                return data;
            }
        }
        throw new IllegalStateException();
    }

    public DenseVectorOld getRowAsDenseVector(final int row) {
        return new DenseVectorOld(getRow(row), elementTypeInfo);
    }

    public DenseVectorOld getColumnAsDenseVector(final int col) {
        return new DenseVectorOld(getColumn(col), elementTypeInfo);
    }

    @Override
    public byte[] getElement(final long row, final long col) {
        return typedBuffer.extractElementAsByteArray(getPos((int) row, (int) col));
    }

    @Override
    public void setElement(final long row, final long col, final byte[] value) {
        typedBuffer.put(getPos((int) row, (int) col), value);
    }

    // ---------------------------------------------------
    // Protected Methods.
    // ---------------------------------------------------

    protected int getPos(final int row, final int col) {
        switch (layout) {
            case ROW_LAYOUT:
                return (row * cols + col);
            case COLUMN_LAYOUT:
                return (col * rows + row);
        }
        throw new IllegalStateException();
    }

    protected int getOffset(final int row, final int col) {
        return getPos(row, col) * elementTypeInfo.size() + UnsafeOp.BYTE_ARRAY_OFFSET;
    }
}