package de.tuberlin.pserver.dsl.unit;


import com.google.common.base.Preconditions;

import java.lang.reflect.Method;

public final class UnitDeclaration {

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    public final Method method;

    public final int[] atNodes;

    public final String name;

    // ---------------------------------------------------
    // Constructors.
    // ---------------------------------------------------

    public UnitDeclaration(final Method method, final int[] atNodes) {

        this.method  = Preconditions.checkNotNull(method);

        this.atNodes = Preconditions.checkNotNull(atNodes);

        this.name    = method.getName();
    }
}