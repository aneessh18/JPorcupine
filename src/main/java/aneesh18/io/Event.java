package aneesh18.io;

public record Event(
        int clientId,
        EventKind kind,
        Object value,
        int id
) {
}
