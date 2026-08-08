package fr.epistudio.epysia.editor.export;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class GameTemplateRepository {

    private static final int DOWNLOAD_BUFFER_SIZE = 1 << 16;
    private static final int STATUS_OK = 200;

    private final Path cacheRoot;

    public GameTemplateRepository() {
        this(Path.of(System.getProperty("user.home"), ".epysia", "templates"));
    }

    public GameTemplateRepository(Path cacheRoot) {
        this.cacheRoot = cacheRoot;
    }

    public Path resolve(TargetPlatform platform, String version, String repository, ExportProgress progress)
            throws IOException {
        Path destination = cacheRoot.resolve(platform.identifier()).resolve(version);
        if (containsFiles(destination)) {
            progress.report(ExportStage.UNPACKING_TEMPLATE, 1.0f);
            return destination;
        }
        Path archive = download(platform, version, repository, progress);
        unzip(archive, destination, progress);
        Files.deleteIfExists(archive);
        return destination;
    }

    private static boolean containsFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isPresent();
        }
    }

    private Path download(TargetPlatform platform, String version, String repository, ExportProgress progress)
            throws IOException {
        URI uri = URI.create("https://github.com/" + repository + "/releases/download/v" + version
                + "/epysia-template-" + platform.identifier() + "-" + version + ".zip");
        Files.createDirectories(cacheRoot);
        Path archive = Files.createTempFile(cacheRoot, "template-", ".zip");
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
            HttpResponse<InputStream> response = send(client, HttpRequest.newBuilder(uri).GET().build());
            requireSuccess(response, archive, uri);
            writeBody(response, archive, progress);
        }
        return archive;
    }

    private static HttpResponse<InputStream> send(HttpClient client, HttpRequest request) throws IOException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Template download interrupted", error);
        }
    }

    private static void requireSuccess(HttpResponse<InputStream> response, Path archive, URI uri) throws IOException {
        if (response.statusCode() == STATUS_OK) {
            return;
        }
        try (InputStream body = response.body()) {
            body.readAllBytes();
        }
        Files.deleteIfExists(archive);
        throw new IOException("Template download failed (" + response.statusCode() + ") from " + uri);
    }

    private static void writeBody(HttpResponse<InputStream> response, Path archive, ExportProgress progress)
            throws IOException {
        long expected = response.headers().firstValueAsLong("content-length").orElse(0L);
        try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(archive)) {
            transfer(input, output, expected, progress);
        }
    }

    private static void transfer(InputStream input, OutputStream output, long expected, ExportProgress progress)
            throws IOException {
        byte[] buffer = new byte[DOWNLOAD_BUFFER_SIZE];
        long written = 0L;
        for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
            output.write(buffer, 0, read);
            written += read;
            progress.report(ExportStage.DOWNLOADING_TEMPLATE, expected > 0L ? written / (float) expected : 0.0f);
        }
    }

    private static void unzip(Path archive, Path destination, ExportProgress progress) throws IOException {
        Files.createDirectories(destination);
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            List<? extends ZipEntry> entries = zip.stream().toList();
            for (int index = 0; index < entries.size(); index++) {
                extractEntry(zip, entries.get(index), destination);
                progress.report(ExportStage.UNPACKING_TEMPLATE, (index + 1) / (float) entries.size());
            }
        }
    }

    private static void extractEntry(ZipFile zip, ZipEntry entry, Path destination) throws IOException {
        Path target = destination.resolve(entry.getName()).normalize();
        if (!target.startsWith(destination)) {
            throw new IOException("Template entry escapes destination: " + entry.getName());
        }
        if (entry.isDirectory()) {
            Files.createDirectories(target);
            return;
        }
        Files.createDirectories(target.getParent());
        try (InputStream source = zip.getInputStream(entry)) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
