package org.hamcrest;

public interface Description {
    Description appendText(String text);

    Description appendDescriptionOf(SelfDescribing value);

    Description appendValue(Object value);
}
