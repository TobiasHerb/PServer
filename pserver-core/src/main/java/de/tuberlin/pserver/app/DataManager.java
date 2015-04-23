package de.tuberlin.pserver.app;

import com.google.common.base.Preconditions;
import de.tuberlin.pserver.app.dht.DHT;
import de.tuberlin.pserver.app.dht.Key;
import de.tuberlin.pserver.app.dht.Value;
import de.tuberlin.pserver.app.dht.valuetypes.DBufferValue;
import de.tuberlin.pserver.app.dht.valuetypes.PagedDBufferValue;
import de.tuberlin.pserver.app.filesystem.FileDataIterator;
import de.tuberlin.pserver.app.filesystem.FileSystemManager;
import de.tuberlin.pserver.app.memmng.MemoryManager;
import de.tuberlin.pserver.app.types.DMatrix;
import de.tuberlin.pserver.core.config.IConfig;
import de.tuberlin.pserver.core.infra.InfrastructureManager;
import de.tuberlin.pserver.core.net.NetManager;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.*;

public final class DataManager {

    // ---------------------------------------------------
    // Inner Classes.
    // ---------------------------------------------------

    public interface MatrixMerger<T> {

        public abstract void merge(final T s, final T[] m);
    }

    // ---------------------------------------------------
    // Constants.
    // ---------------------------------------------------

    public static enum DataEventType {

        MATRIX_EVENT("MATRIX_EVENT"),

        VECTOR_EVENT("VECTOR_EVENT");

        public final String eventType;

        DataEventType(final String eventType) { this.eventType = eventType; }

        @Override public String toString() { return eventType; }
    }

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private static final Logger LOG = LoggerFactory.getLogger(DataManager.class);

    private final IConfig config;

    private final InfrastructureManager infraManager;

    private final NetManager netManager;

    private final FileSystemManager fileSystemManager;

    private final DHT dht;

    private final int instanceID;

    private final List<FileDataIterator<CSVRecord>> filesToLoad;

    // ---------------------------------------------------
    // Constructor.
    // ---------------------------------------------------

    public DataManager(final IConfig config,
                       final InfrastructureManager infraManager,
                       final NetManager netManager,
                       final FileSystemManager fileSystemManager,
                       final DHT dht) {

        this.config             = Preconditions.checkNotNull(config);
        this.infraManager       = Preconditions.checkNotNull(infraManager);
        this.netManager         = Preconditions.checkNotNull(netManager);
        this.fileSystemManager  = fileSystemManager;
        this.dht                = Preconditions.checkNotNull(dht);
        this.instanceID         = infraManager.getInstanceID();
        this.filesToLoad        = new ArrayList<>();
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    public IConfig getConfig() { return config; }

    // ---------------------------------------------------

    public <T> FileDataIterator<T> createFileIterator(final String filePath, final Class<T> recordType) {
        return fileSystemManager != null ? fileSystemManager.createFileIterator(filePath, recordType) : null;
    }

    // ---------------------------------------------------

    public void loadDMatrix(final String filePath) {
        filesToLoad.add(createFileIterator(Preconditions.checkNotNull(filePath), null));
    }

    // ---------------------------------------------------

    public final Value[] globalPull(final String name) {
        Preconditions.checkNotNull(name);
        int idx = 0;
        final Set<Key> keys = dht.getKey(name);
        final Value[] values = new Value[keys.size()];
        for (final Key key : keys) {
            values[idx] = dht.get(key)[0];
            values[idx].setKey(key);
            ++idx;
        }
        return values;
    }

    // ---------------------------------------------------

    public void mergeMatrix(final DMatrix localMtx, final MatrixMerger<DMatrix> merger) {
        final Key k = ((Value)localMtx).getKey();
        final List<Value> matrices = Arrays.asList(globalPull(k.name));
        Collections.sort(matrices,
                (Value o1, Value o2) -> ((Integer)o1.getValueMetadata()).compareTo(((Integer)o2.getValueMetadata()))
        );
        final DBufferValue[] ms = new DBufferValue[matrices.size()];
        for (int i = 0; i < matrices.size(); ++i)
            ms[i] = (DBufferValue)matrices.get(i);
        merger.merge(localMtx, ms);
    }

    // ---------------------------------------------------

    public DMatrix createLocalMatrix(final String name, final int rows, final int cols)
    { return createLocalMatrix(name, rows, cols, DMatrix.MemoryLayout.ROW_LAYOUT); }
    public DMatrix createLocalMatrix(final String name, final int rows, final int cols, final DMatrix.MemoryLayout layout) {
        Preconditions.checkNotNull(name);
        final Key key = createLocalKeyWithName(name);
        final DBufferValue m = new DBufferValue(rows, cols, false, layout);
        m.setValueMetadata(instanceID);
        dht.put(key, m);
        return m;
    }

    // ---------------------------------------------------

    public DMatrix getLocalMatrix(final String name) {
        Preconditions.checkNotNull(name);
        Key localKey = null;
        final Set<Key> keys = dht.getKey(name);
        for (final Key k : keys) {
            if (k.getPartitionDescriptor(instanceID) != null) {
                localKey = k;
                break;
            }
        }
        final Value value = dht.get(localKey)[0];
        if (value instanceof DMatrix)
            return (DMatrix)dht.get(localKey)[0];
        else
            throw new IllegalStateException();
    }

    // ---------------------------------------------------

    public void postProloguePhase() {
        if (fileSystemManager != null) {
            fileSystemManager.computeInputSplitsForRegisteredFiles();
            loadFilesIntoDHT();
        }
    }

    // ---------------------------------------------------
    // Private Methods.
    // ---------------------------------------------------

    private void loadFilesIntoDHT() {
        for (final FileDataIterator<CSVRecord> fileIterator : filesToLoad) {
            double[] currentSegment = (double[]) MemoryManager.getMemoryManager().allocSegmentAs(double[].class);
            List<double[]> buffers = new ArrayList<>();
            buffers.add(currentSegment);
            int rows = 0, cols = -1, localIndex = 0;
            while (fileIterator.hasNext()) {
                final CSVRecord record = fileIterator.next();

                if (cols == -1)
                    cols = record.size();
                if (record.size() != cols)
                    throw new IllegalStateException("cols must always have size: " + cols + " but it has record.size = " + record.size());

                for (int i = 0; i < record.size(); ++i) {
                    if (localIndex == currentSegment.length - 1) {
                        currentSegment = (double[]) MemoryManager.getMemoryManager().allocSegmentAs(double[].class);
                        buffers.add(currentSegment);
                        localIndex = 0;
                    }
                    currentSegment[localIndex] = Double.parseDouble(record.get(i));
                    ++localIndex;
                }
                ++rows;
            }
            final String filename = Paths.get(fileIterator.getFilePath()).getFileName().toString();
            final Key key = createLocalKeyWithName(filename);
            final PagedDBufferValue dBuf = new PagedDBufferValue(rows, cols, buffers);
            dht.put(key, dBuf);
        }
    }

    private UUID createLocalUID() {
        int id; UUID uid;
        do {
            uid = UUID.randomUUID();
            id = (uid.hashCode() & Integer.MAX_VALUE) % infraManager.getMachines().size();
        } while (id != infraManager.getInstanceID());
        return uid;
    }

    private Key createLocalKeyWithName(final String name) {
        final UUID localUID = createLocalUID();
        final Key key = Key.newKey(localUID, name, Key.DistributionMode.DISTRIBUTED);
        return key;
    }
}