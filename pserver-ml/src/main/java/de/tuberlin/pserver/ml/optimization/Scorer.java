package de.tuberlin.pserver.ml.optimization;

import com.google.common.base.Preconditions;
import de.tuberlin.pserver.math.matrix.Matrix;
import de.tuberlin.pserver.math.matrix.MatrixBuilder;


public class Scorer {

    private ScoreFunction scoreFunction;
    private PredictionFunction predictionFunction;

    public Scorer(ScoreFunction scoreFunction, PredictionFunction predictionFunction) {
        this.scoreFunction = Preconditions.checkNotNull(scoreFunction);
        this.predictionFunction = Preconditions.checkNotNull(predictionFunction);
    }

    public double score(Matrix X, Matrix y, Matrix W) throws Exception {
        Matrix yPred = new MatrixBuilder().dimension(X.rows(), y.cols()).build();

        for (int i = 0; i < X.rows(); ++i) {
            yPred.set(i, 0, predictionFunction.predict(X.getRow(i), W));
        }

        return scoreFunction.score(y, yPred);
    }
}