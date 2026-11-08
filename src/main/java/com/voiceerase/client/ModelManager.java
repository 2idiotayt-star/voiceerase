package com.voiceerase.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Baixa (uma unica vez) o modelo de voz em portugues do Vosk. */
public final class ModelManager {
    private ModelManager() { }

    private static final String URL = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip";

    public static Path ensureModel(Path dir, Consumer<String> say) throws Exception {
        Path model = dir.resolve("model");
        if (Files.isDirectory(model.resolve("am"))) return model;

        Files.createDirectories(dir);
        Path zip = dir.resolve("model.zip");
        say.accept("Baixando o modelo de voz em portugues (~30 MB). So na primeira vez...");

        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpResponse<Path> resp = client.send(HttpRequest.newBuilder(URI.create(URL)).build(),
                HttpResponse.BodyHandlers.ofFile(zip));
        if (resp.statusCode() != 200) throw new IOException("Download falhou: HTTP " + resp.statusCode());

        say.accept("Extraindo modelo...");
        try (ZipInputStream z = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String name = e.getName();
                int slash = name.indexOf('/');
                String rel = slash >= 0 ? name.substring(slash + 1) : name; // tira a pasta raiz do zip
                if (rel.isEmpty()) continue;
                Path out = model.resolve(rel).normalize();
                if (!out.startsWith(model)) continue; // seguranca contra zip-slip
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(z, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Files.deleteIfExists(zip);
        return model;
    }
}
