package fr.epistudio.epysia.editor.export;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class GameTemplateRepository {

    private final Path cacheRoot;

    public GameTemplateRepository() {
        this(Path.of(System.getProperty("user.home"), ".epysia", "templates"));
    }

    public GameTemplateRepository(Path cacheRoot) {
        this.cacheRoot = cacheRoot;
    }

    public Path resolve(TargetPlatform platform, String version, String repository) throws IOException {
        Path destination = cacheRoot.resolve(platform.identifier()).resolve(version);
        if (containsFiles(destination)) {
            return destination;
        }
        Path archive = download(platform, version, repository);
        unzip(archive, destination);
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

    private Path download(TargetPlatform platform, String version, String repository) throws IOException {
        URI uri = URI.create("https://github.com/" + repository + "/releases/download/v" + version
                + "/epysia-template-" + platform.identifier() + "-" + version + ".zip");
        Files.createDirectories(cacheRoot);
        Path archive = Files.createTempFile(cacheRoot, "template-", ".zip");
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        HttpResponse<Path> response = send(request, archive);
        if (response.statusCode() != 200) {
            Files.deleteIfExists(archive);
            throw new IOException("Template download failed (" + response.statusCode() + ") from " + uri);
        }
        return archive;
    }

    private static HttpResponse<Path> send(HttpRequest request, Path archive) throws IOException {
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofFile(archive));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Template download interrupted", error);
        }
    }

    private static void unzip(Path archive, Path destination) throws IOException {
        Files.createDirectories(destination);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                extractEntry(zip, entry, destination);
            }
        }
    }

    private static void extractEntry(InputStream source, ZipEntry entry, Path destination) throws IOException {
        Path target = destination.resolve(entry.getName()).normalize();
        if (!target.startsWith(destination)) {
            throw new IOException("Template entry escapes destination: " + entry.getName());
        }
        if (entry.isDirectory()) {
            Files.createDirectories(target);
            return;
        }
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
