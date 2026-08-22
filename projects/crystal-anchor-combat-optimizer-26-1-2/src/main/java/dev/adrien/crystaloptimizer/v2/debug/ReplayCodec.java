package dev.adrien.crystaloptimizer.v2.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Versioned deterministic JSON codec for combat replay fixtures. */
public final class ReplayCodec {
    private final Gson gson = new GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    public byte[] encode(ReplayFixture fixture) {
        Objects.requireNonNull(fixture, "fixture");
        String json = gson.toJson(ReplaySnapshotSerde.encode(fixture));
        return (json + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public ReplayFixture decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        ReplaySnapshotSerde.RootDto root = gson.fromJson(
            new String(encoded, StandardCharsets.UTF_8),
            ReplaySnapshotSerde.RootDto.class
        );
        return ReplaySnapshotSerde.decode(root);
    }

    public ReplayFixture readResource(String classpathPath) throws IOException {
        if (classpathPath == null || classpathPath.isBlank()) {
            throw new IllegalArgumentException("classpathPath must not be blank");
        }
        String normalized = classpathPath.startsWith("/")
            ? classpathPath.substring(1)
            : classpathPath;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = ReplayCodec.class.getClassLoader();
        }
        try (InputStream input = loader.getResourceAsStream(normalized)) {
            if (input == null) {
                throw new IOException("replay resource not found: " + classpathPath);
            }
            return decode(input.readAllBytes());
        }
    }
}
