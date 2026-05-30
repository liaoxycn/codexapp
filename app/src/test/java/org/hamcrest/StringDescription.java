package org.hamcrest;

public class StringDescription implements Description {
    private final StringBuilder out = new StringBuilder();

    public static String asString(SelfDescribing value) {
        StringDescription description = new StringDescription();
        value.describeTo(description);
        return description.toString();
    }

    @Override
    public Description appendText(String text) {
        out.append(text);
        return this;
    }

    @Override
    public Description appendDescriptionOf(SelfDescribing value) {
        value.describeTo(this);
        return this;
    }

    @Override
    public Description appendValue(Object value) {
        out.append(String.valueOf(value));
        return this;
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
