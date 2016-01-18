package de.tuberlin.pserver.dsl.transaction.annotations;

import de.tuberlin.pserver.dsl.transaction.properties.TransactionType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Transaction {

    public String state() default "";

    public String src() default "";

    public String dst() default "";

    public TransactionType type();

    public String at() default "";

    public boolean cache() default false;
}
