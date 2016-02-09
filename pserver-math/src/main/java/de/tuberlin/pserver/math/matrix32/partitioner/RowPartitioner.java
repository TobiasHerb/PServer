package de.tuberlin.pserver.math.matrix32.partitioner;

import de.tuberlin.pserver.math.matrix32.entries.Entry32;

public class RowPartitioner extends MatrixPartitioner {

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private PartitionShape shape;

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    public RowPartitioner(PartitionerType partitionerType, long rows, long cols, int nodeId, int[] atNodes) {
        super(partitionerType, rows, cols, nodeId, atNodes);
        shape = getPartitionShape();
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    @Override
    public int getPartitionOfEntry(final Entry32 entry) {
        double numOfRowsPerInstance = (double) rows / atNodes.length;
        int partition = (int) (entry.getRow() / numOfRowsPerInstance);
        if(partition >= atNodes.length) {
            throw new IllegalStateException("The calculated partition id (row = " + entry.getRow() + ", rows = "+rows+", numNodes = "+atNodes.length+") -> " + partition + " must not exceed numNodes.");
        }
        return atNodes[partition];
    }

    @Override
    public int getPartitionOfEntry(long row, long col) {
        double numOfRowsPerInstance = (double) rows / atNodes.length;
        int partition = (int) (row / numOfRowsPerInstance);
        if(partition >= atNodes.length) {
            throw new IllegalStateException("The calculated partition id (row = " + row + ", rows = "
                    + rows +", numNodes = " + atNodes.length +") -> " + partition + " must not exceed numNodes.");
        }
        return atNodes[partition];
    }

    @Override
    public PartitionShape getPartitionShape() {
        if (atNodes == null)
            return null;
        if(shape == null) {
            double rowsPerNode = (double) rows / atNodes.length;
            long rowOffset = (int) Math.ceil(rowsPerNode * nodeId);
            long numRows = (int) (Math.ceil(rowsPerNode * (nodeId + 1)) - rowOffset);
            shape = new PartitionShape(numRows, cols, rowOffset, 0);
        }
        return shape;
    }

    @Override
    public long translateGlobalToLocalRow(long row) {
        long firstRow = shape.rowOffset;
        long lastRow  = shape.rowOffset + shape.rows - 1;
        if(row < firstRow || row > lastRow) {
            throw new IllegalStateException(String.format("Can not translate row: %d. Not in current partitions row range: [%d, %d]", row, firstRow, lastRow));
        }
        return row - firstRow;
    }

    @Override
    public long translateGlobalToLocalCol(long col) {
        return col;
    }

    @Override
    public long translateLocalToGlobalRow(long row) {
        if(row > shape.rows - 1) {
            throw new IllegalStateException(String.format("Can not translate row: %d. Not in allowed range: [%d, %d]", row, 0, shape.rows - 1));
        }
        return row + shape.rowOffset;
    }

    @Override  public long translateLocalToGlobalCol(long col) { return col; }

    @Override  public int getNumRowPartitions() { return atNodes.length; }

    @Override public int getNumColPartitions() { return 1; }
}
