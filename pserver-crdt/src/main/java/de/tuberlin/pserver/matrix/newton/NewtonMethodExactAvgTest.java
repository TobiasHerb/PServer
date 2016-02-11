package de.tuberlin.pserver.matrix.newton;


import de.tuberlin.pserver.client.PServerExecutor;
import de.tuberlin.pserver.compiler.Program;
import de.tuberlin.pserver.dsl.unit.annotations.Unit;
import de.tuberlin.pserver.dsl.unit.controlflow.lifecycle.Lifecycle;
import de.tuberlin.pserver.matrix.crdt.ExactAvgDenseMatrix64F;

import static org.junit.Assert.assertEquals;

public class NewtonMethodExactAvgTest extends Program {
    private final long ROWS = 5;
    private final long COLS = 5;
    private static final String NUM_NODES = "2";
    private static final String ID = "one";


    @Unit
    public void test(Lifecycle lifecycle) {
        lifecycle.process(() -> {
            ExactAvgDenseMatrix64F m = new ExactAvgDenseMatrix64F(ROWS, COLS, ID, Integer.parseInt(NUM_NODES), programContext);

            System.out.println("*");
            new NewtonMethod(m).newton("Newton" + programContext.runtimeContext.nodeID + ".csv");
            System.out.println("**");

            m.finish();

            System.out.println("[DEBUG] Node " + programContext.runtimeContext.nodeID + " Matrix: ");
            for (int i = 0; i < m.rows(); i++) {
                System.out.println();
                for (int k = 0; k < m.cols(); k++) {
                    System.out.print(m.get(i, k) + " ");
                }
            }
            System.out.println();

            System.out.println("[DEBUG] Buffer of node " + programContext.runtimeContext.nodeID + ": " + m.getBuffer());
            //System.out.println("[DEBUG] Queue of node " + programContext.runtimeContext.nodeID + ": " + m.getQueue().size());

        });
    }

    public static void main(String[] args) {

        // Set the number of simulated nodes, can also be
        // configured via 'pserver/pserver-core/src/main/resources/reference.simulation.conf'
        System.setProperty("simulation.numNodes", NUM_NODES);
        // Set the memory each simulated node gets.
        System.setProperty("jvmOptions", "[\"-Xmx256m\"]");

        PServerExecutor.LOCAL
                // Second param is number of slots (threads executing the job) per node,
                // should be 1 at the beginning.
                .run(NewtonMethodExactAvgTest.class)
                .done();
    }
}