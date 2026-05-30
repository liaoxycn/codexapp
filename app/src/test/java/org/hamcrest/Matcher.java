package org.hamcrest;

public interface Matcher<T> extends SelfDescribing {
    boolean matches(Object item);
}
