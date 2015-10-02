package de.tuberlin.pserver.dsl.state.annotations;

import de.tuberlin.pserver.dsl.state.properties.GlobalScope;
import de.tuberlin.pserver.math.Format;
import de.tuberlin.pserver.math.Layout;
import de.tuberlin.pserver.runtime.filesystem.record.config.AbstractRecordFormatConfig;
import de.tuberlin.pserver.runtime.filesystem.record.config.RowColValRecordFormatConfig;
import de.tuberlin.pserver.types.PartitionType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface State {;

    GlobalScope globalScope() default GlobalScope.REPLICATED;

    String at() default "";

    PartitionType partitionType() default PartitionType.ROW_PARTITIONED;

    long rows() default 0;

    long cols() default 0;

    Layout layout() default Layout.ROW_LAYOUT;

    Format format() default Format.DENSE_FORMAT;

    Class<? extends AbstractRecordFormatConfig> recordFormat() default RowColValRecordFormatConfig.class;

    String path() default "";
}