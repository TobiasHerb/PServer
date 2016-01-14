package de.tuberlin.pserver.new_core_runtime.events;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;


public abstract class EventHandler implements IEventHandler {

    // ---------------------------------------------------
    // Annotations.
    // ---------------------------------------------------

    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Handle {

        Class<? extends Event> event();

        String type() default "";
    }

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private final Map<Class<?>, Method> eventHandlerMap;

    private final Map<Class<?>, Map<String, Method>> multiTypeEventHandlerMap;

    // ---------------------------------------------------
    // Constructors.
    // ---------------------------------------------------

    public EventHandler() {
        this.eventHandlerMap = new HashMap<>();
        this.multiTypeEventHandlerMap = new HashMap<>();
        register(this.getClass());
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    @Override
    public void handleEvent(final Event event) {
        Method m = eventHandlerMap.get(event.getClass());
        if (m != null) {
            m.setAccessible(true);
            try {
                m.invoke(this, event);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        } else {
            final Map<String, Method> handlerTable = multiTypeEventHandlerMap.get(event.getClass());

            m = (handlerTable != null) ? handlerTable.get(event.type) : null;
            if (m != null) {
                m.setAccessible(true);
                try {
                    m.invoke(this, event);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    throw new IllegalStateException(e);
                }
            } else {
                handleUnknownEvent(event);
            }
        }
    }

    // ---------------------------------------------------
    // Protected Methods.
    // ---------------------------------------------------

    protected void handleUnknownEvent(final Event e) {
    }

    // ---------------------------------------------------
    // Private Methods.
    // ---------------------------------------------------

    private void register(final Class<?> clazz) {
        for (final Method m : clazz.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Handle.class)) {
                final Class<?>[] argumentTypes = m.getParameterTypes();
                if (argumentTypes.length == 1 && Event.class.isAssignableFrom(argumentTypes[0])) {
                    final Handle handle = m.getAnnotation(Handle.class);
                    if ("".equals(handle.type())) {

                        // if (eventHandlerMap.containsKey(handle.primitiveType()))
                        // throw new IllegalStateException("event already registered");

                        eventHandlerMap.put(handle.event(), m);

                    } else {
                        Map<String, Method> handlerTable = multiTypeEventHandlerMap.get(handle.event());
                        if (handlerTable == null) {
                            handlerTable = new Hashtable<>();
                            multiTypeEventHandlerMap.put(handle.event(), handlerTable);
                        }

                        if (handlerTable.containsKey(handle.type()))
                            throw new IllegalStateException("event already registered");

                        handlerTable.put(handle.type(), m);
                    }
                } else {
                    throw new IllegalStateException(m + " is not a event handler method");
                }
            }
        }
    }
}
