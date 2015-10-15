package de.tuberlin.pserver.test.core.programs;

import de.tuberlin.pserver.compiler.Program;
import de.tuberlin.pserver.dsl.state.annotations.State;
import de.tuberlin.pserver.dsl.state.properties.Scope;
import de.tuberlin.pserver.dsl.unit.annotations.Unit;
import de.tuberlin.pserver.dsl.unit.controlflow.lifecycle.Lifecycle;
import de.tuberlin.pserver.math.matrix.Matrix;
import de.tuberlin.pserver.runtime.partitioning.partitioner.RowPartitioner;

public class ReplicatedMatrixTestJob extends Program {

    @State(scope = Scope.PARTITIONED,
            rows = 21, cols = 20,
            path = "datasets/rowcolval_dataset.csv",
            partitioner = RowPartitioner.class)

    public Matrix X;

    @Unit
    public void main(final Lifecycle lifecycle) {

        lifecycle.process(() -> {


            if (programContext.node(3)) {

                final Matrix.RowIterator it = X.rowIterator();
                while (it.hasNext()) {
                    it.next();
                    System.out.println(it.value(0));
                }
            }
        });
    }
}