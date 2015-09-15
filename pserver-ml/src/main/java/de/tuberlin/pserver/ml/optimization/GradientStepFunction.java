package de.tuberlin.pserver.ml.optimization;

import de.tuberlin.pserver.commons.unsafe.UnsafeOp;
import de.tuberlin.pserver.math.matrix.Matrix;

public interface GradientStepFunction {

    public abstract Matrix takeStep(final Matrix weights, final Matrix gradients, final double alpha);

    // ---------------------------------------------------

    class SimpleGradientStep implements GradientStepFunction {

        @Override
        public Matrix takeStep(final Matrix weights, final Matrix gradients, final double alpha) {
            return weights.scale(-alpha).add(gradients);
        }
    }

    class AtomicGradientStep implements GradientStepFunction {

        private static final int base = UnsafeOp.unsafe.arrayBaseOffset(long[].class);

        private static final int shift;

        static {
            int scale = UnsafeOp.unsafe.arrayIndexScale(long[].class);

            if ((scale & (scale - 1)) != 0)
                throw new Error();

            shift = 31 - Integer.numberOfLeadingZeros(scale);
        }

        @Override
        public Matrix takeStep(final Matrix weights, final Matrix gradients, final double alpha) {
            for( int i = 0; i < weights.cols(); i++ ) {

                final long value = Double.doubleToRawLongBits(weights.get(i) + (-alpha) * gradients.get(i));

                UnsafeOp.unsafe.putLongVolatile(weights.toArray(), ((long) i << shift) + base, value);
            }
            return weights;
        }
    }
}
