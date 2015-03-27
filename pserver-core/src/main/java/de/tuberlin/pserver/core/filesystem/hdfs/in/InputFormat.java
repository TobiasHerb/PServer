package de.tuberlin.pserver.core.filesystem.hdfs.in;

import de.tuberlin.pserver.core.filesystem.hdfs.InputSplit;
import de.tuberlin.pserver.core.filesystem.hdfs.InputSplitAssigner;
import org.apache.hadoop.conf.Configuration;

import java.io.IOException;
import java.io.Serializable;


public interface InputFormat<OT, T extends InputSplit> extends Serializable {

    void configure(Configuration parameters);

    public abstract T[] createInputSplits(int minNumSplits) throws IOException;

    public abstract InputSplitAssigner getInputSplitAssigner(T[] inputSplits);

    public abstract void open(T split) throws IOException;

    public abstract boolean reachedEnd() throws IOException;

    public abstract OT nextRecord(OT reuse) throws IOException;

    public abstract void close() throws IOException;
}