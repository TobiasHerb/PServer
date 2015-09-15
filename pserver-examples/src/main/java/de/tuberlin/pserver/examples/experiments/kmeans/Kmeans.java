package de.tuberlin.pserver.examples.experiments.kmeans;

import de.tuberlin.pserver.client.PServerExecutor;
import de.tuberlin.pserver.dsl.controlflow.annotations.Unit;
import de.tuberlin.pserver.dsl.controlflow.loop.Loop;
import de.tuberlin.pserver.dsl.controlflow.program.Program;
import de.tuberlin.pserver.dsl.state.annotations.State;
import de.tuberlin.pserver.dsl.state.annotations.StateMerger;
import de.tuberlin.pserver.dsl.state.properties.GlobalScope;
import de.tuberlin.pserver.dsl.state.properties.RemoteUpdate;
import de.tuberlin.pserver.math.Format;
import de.tuberlin.pserver.math.matrix.Matrix;
import de.tuberlin.pserver.math.matrix.dense.Dense64Matrix;
import de.tuberlin.pserver.runtime.MLProgram;
import de.tuberlin.pserver.runtime.filesystem.record.config.RowRecordFormatConfig;
import de.tuberlin.pserver.runtime.state.merger.MatrixUpdateMerger;

import java.util.Random;

public class Kmeans extends MLProgram {

    private static final long ROWS = 1000;
    private static final long COLS = 2;
    private static final int K = 3;

    // loaded by pserver
    private static final String FILE = "datasets/stripes3.csv";

    @State(
            globalScope = GlobalScope.PARTITIONED,
            rows = ROWS,
            cols = COLS,
            path = FILE,
            format = Format.DENSE_FORMAT,
            recordFormat = RowRecordFormatConfig.class
    )
    public Matrix matrix;

    @State(
            globalScope = GlobalScope.REPLICATED,
            rows = K,
            cols = COLS + 1,
            remoteUpdate = RemoteUpdate.SIMPLE_MERGE_UPDATE
    )
    public Matrix centroidsUpdate;

    @StateMerger(stateObjects = "centroidsUpdate")
    public final MatrixUpdateMerger centroidsUpdateMerger = (i, j, val, remoteVal) -> val + remoteVal;


    @Unit
    public void main(final Program program) {

//        Random rand = new Random(42);
//        double[] data = new double[(int)(K * COLS)];
//        for (int i = 0; i < K * COLS; i++) {
//            data[i] = rand.nextDouble();
//        }
        final Matrix centroids = new Dense64Matrix(K, COLS, new double[] {0, -1, 0, 0, 0, 1});


        program.initialize(() -> {

            for (int i = 0; i < K; i++) {
                int nodeId = slotContext.runtimeContext.nodeID;
                System.out.println("centroid[node:"+nodeId+",row:"+i+"]="+centroids.getRow(i));
            }

        }).process(() -> {

            CF.loop().sync(Loop.GLOBAL).exe(10, (iteration) -> {

                Matrix.RowIterator iter = matrix.rowIterator();
                while (iter.hasNext()) {
                    Matrix point = iter.get();
                    iter.next();
                    double closestDistance = Double.MAX_VALUE;
                    long closestCentroidId = -1;
                    for (long centroidId = 0; centroidId < K; centroidId++) {
                        Matrix centroid = centroids.getRow(centroidId);
                        Matrix diff = centroid.sub(point);
                        double distance = diff.norm(2);
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closestCentroidId = centroidId;
                        }
                    }
                    Matrix updateDelta = point.copy(1, COLS + 1);
                    updateDelta.set(1, COLS, 1);
                    centroidsUpdate.assignRow(closestCentroidId, centroidsUpdate.getRow(closestCentroidId).add(updateDelta));
                }
                //DF.publishUpdate();
                //DF.pullUpdate();
                for (int i = 0; i < K; i++) {
                    if (centroidsUpdate.get(i, COLS) > 0) {
                        System.out.println("centroidsUpdate(" + i + ")=" + centroidsUpdate.getRow(i));
                        Matrix update = centroidsUpdate.getRow(i, 0, COLS);
                        centroids.assignRow(i, update.scale(1. / centroidsUpdate.get(i, COLS), update));
                    }
                }

                for (int i = 0; i < K; i++) {
                    int nodeId = slotContext.runtimeContext.nodeID;
                    System.out.println("centroid[node:" + nodeId + ",row:" + i + "]=" + centroids.getRow(i));
                }
                centroidsUpdate.assign(0);
            });

        }).postProcess(() -> {

            for (int i = 0; i < K; i++) {
                int nodeId = slotContext.runtimeContext.nodeID;
                System.out.println("centroid[node:" + nodeId + ",row:" + i + "]=" + centroids.getRow(i));
            }

        });

    }

    // ---------------------------------------------------
    // Entry Point.
    // ---------------------------------------------------
    public static void main(String[] args) {
        local();
    }

    public static void cluster() {
        System.setProperty("pserver.profile", "wally");
        PServerExecutor.DISTRIBUTED
                .run(Kmeans.class, 1)
                .done();
    }

    public static void local() {
        System.setProperty("simulation.numNodes", "1");
        PServerExecutor.LOCAL
                .run(Kmeans.class, 1)
                .done();
    }
}
