package az.ingress.ai_exprementation.blog.model;

import java.util.List;
import java.util.Objects;
final class Checks {
    private Checks() {}
    static String text(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.strip();
    }
    static <T> List<T> list(List<T> value) { return List.copyOf(Objects.requireNonNull(value)); }
}
