package aneesh18.io;

public record Operation(int clientId,
                        Object input,
                        long start,
                        Object output,
                        long end
) {
}
