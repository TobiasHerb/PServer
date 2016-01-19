package de.tuberlin.pserver.runtime.filesystem.records;

import de.tuberlin.pserver.math.tuples.Tuple2;
import de.tuberlin.pserver.runtime.filesystem.FileFormat;
import de.tuberlin.pserver.runtime.state.entries.Entry;
import de.tuberlin.pserver.runtime.state.entries.ImmutableEntryImpl;
import de.tuberlin.pserver.runtime.state.entries.ReusableEntry;
import org.apache.commons.lang.ArrayUtils;

import java.util.*;


public class SVMRecord<V extends Number> implements Record {

    // ---------------------------------------------------
    // Inner Classes.
    // ---------------------------------------------------

    static class SVMParser {

        private static FileFormat fileFormat = FileFormat.SVM_FORMAT;

        public static void setFileFormat(FileFormat fileFormat) {
            SVMParser.fileFormat = fileFormat;
        }

        protected static <V extends Number> Tuple2<Integer, Map<Long, V>> parse(String line, Optional<long[]> projection) {

            StringTokenizer tokenizer = new StringTokenizer(line, fileFormat.SVM_FORMAT.getDelimiter());
            Integer target = Integer.parseInt(tokenizer.nextToken());

            List<Long> projectionList = null;
            if (projection.isPresent())
                projectionList = Arrays.asList(ArrayUtils.toObject(projection.get()));

            Map<Long, V> attributes = new TreeMap<>();
            while(tokenizer.hasMoreTokens()) {
                String column = tokenizer.nextToken();
                Long index = Long.parseLong(column.substring(0, column.indexOf(":")));
                // We subtract one because array starts at [0, 0] and SVM [1,1]
                index--;
                if (projectionList == null || projectionList.contains(index)) {
                    if (fileFormat.getValueType() == FileFormat.ValueType.FLOAT)
                        attributes.put(index, (V) (Float) Float.parseFloat(column.substring(column.indexOf(":") + 1)));
                    else
                        attributes.put(index, (V) (Double) Double.parseDouble(column.substring(column.indexOf(":") + 1)));
                }
            }

            return new Tuple2<>(target, attributes);
        }

    }

    // ---------------------------------------------------
    // State.
    // ---------------------------------------------------

    private long row;
    private Tuple2<Integer, Map<Long, V>> data;
    private Iterator<Long> attributeIterator;

    // ---------------------------------------------------
    // Constructor.
    // ---------------------------------------------------

    public SVMRecord(long row, String line) {
        this(row, line, Optional.empty());
    }

    public SVMRecord(long row, String line, Optional<long[]> projection) {
        this.row = row;
        this.data = SVMParser.parse(line, projection);
        this.attributeIterator = data._2.keySet().iterator();
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    public long getRow() {
        return this.row;
    }

    public int getTarget() {
        return this.data._1;
    }

    @Override
    public int size() {
        return this.data._2.size();
    }

    @Override
    public boolean hasNext() {
        return this.attributeIterator.hasNext();
    }

    @Override
    public Entry<V> next() {
        return this.next(null);
    }

    @Override
    public Entry<V> next(ReusableEntry reusableEntry) {
        Long index = this.attributeIterator.next();
        V value = this.data._2.get(index);
        if (reusableEntry == null)
            return new ImmutableEntryImpl(this.row, index, value);
        return reusableEntry.set(this.row, index, value);
    }

    @Override
    public SVMRecord<V> set(long row, String line, Optional<long[]> projection) {
        this.row = row;
        this.data = SVMParser.parse(line, projection);
        this.attributeIterator = data._2.keySet().iterator();
        return this;
    }
}