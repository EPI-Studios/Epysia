package fr.epistudio.epysia.editor.langpack;

import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectLanguagePacks;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class LanguagePackStore {

    public static final String REPOSITORY = "EPI-Studios/Epysia";
    private static final String CATALOGUE_URL =
            "https://github.com/" + REPOSITORY + "/releases/latest/download/language-packs.json";
    private static final String ASSET_URL_PATTERN =
            "https://github.com/" + REPOSITORY + "/releases/download/v%s/%s";
    private static final String ARCHIVE_BUNDLE_SUFFIX = ".zip";
    private static final String CHECKSUM_ALGORITHM = "SHA-256";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int STATUS_OK = 200;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build();

    public LanguagePackCatalogue fetchCatalogue() throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(requestFor(CATALOGUE_URL),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != STATUS_OK) {
            throw new IOException("The catalogue answered " + response.statusCode() + ".");
        }
        return LanguagePackCatalogue.parse(response.body());
    }

    public void install(Project project, LanguagePack pack) throws IOException, InterruptedException {
        Path staging = project.languagePacksDirectory().resolve(pack.identifier() + ".part");
        deleteTree(staging);
        Files.createDirectories(staging);
        fetchInto(staging, pack.archiveName(), urlOf(pack), pack.checksum());
        if (pack.hasRuntimeArchive()) {
            fetchInto(staging, pack.runtimeArchiveName(),
                    urlOf(pack, pack.runtimeUrl(), pack.runtimeArchiveName()), pack.runtimeChecksum());
        }
        Path installed = directoryOf(project, pack.identifier());
        deleteTree(installed);
        Files.move(staging, installed);
        pinsOf(project).with(pack.identifier(), pack.version()).writeTo(project.languagePacksFile());
    }

    public void remove(Project project, String identifier) throws IOException {
        deleteTree(directoryOf(project, identifier));
        pinsOf(project).without(identifier).writeTo(project.languagePacksFile());
    }

    public Optional<String> installedVersion(Project project, String identifier) {
        return pinsOf(project).versionOf(identifier)
                .filter(version -> Files.isDirectory(directoryOf(project, identifier)));
    }

    private static Path directoryOf(Project project, String identifier) {
        return project.languagePacksDirectory().resolve(identifier);
    }

    private void fetchInto(Path directory, String archiveName, String url, String checksum)
            throws IOException, InterruptedException {
        Path downloaded = downloadInto(directory, archiveName, url, checksum);
        Path target = directory.resolve(archiveName);
        Files.move(downloaded, target, StandardCopyOption.REPLACE_EXISTING);
        if (archiveName.endsWith(ARCHIVE_BUNDLE_SUFFIX)) {
            unpack(target, directory);
            Files.delete(target);
        }
    }

    private static void unpack(Path bundle, Path directory) throws IOException {
        try (ZipFile archive = new ZipFile(bundle.toFile())) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path target = directory.resolve(Path.of(entry.getName()).getFileName().toString());
                if (entry.isDirectory() || !target.getFileName().toString().endsWith(".jar")) {
                    continue;
                }
                try (InputStream content = archive.getInputStream(entry)) {
                    Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path entry : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private static ProjectLanguagePacks pinsOf(Project project) {
        return ProjectLanguagePacks.read(project.languagePacksFile());
    }

    private static String urlOf(LanguagePack pack) {
        return urlOf(pack, pack.downloadUrl(), pack.archiveName());
    }

    private static String urlOf(LanguagePack pack, String declared, String archiveName) {
        if (!declared.isBlank()) {
            return declared;
        }
        return ASSET_URL_PATTERN.formatted(pack.version(), archiveName);
    }

    private Path downloadInto(Path directory, String archiveName, String url, String checksum)
            throws IOException, InterruptedException {
        Path pending = directory.resolve(archiveName + ".part");
        transfer(url, pending);
        verify(pending, checksum);
        return pending;
    }

    private void transfer(String url, Path destination) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = client.send(requestFor(url),
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != STATUS_OK) {
            throw new IOException("The download answered " + response.statusCode() + ".");
        }
        try (InputStream body = response.body()) {
            Files.copy(body, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static HttpRequest requestFor(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
    }

    private static void verify(Path archive, String expected) throws IOException {
        if (expected.isBlank()) {
            return;
        }
        String actual = checksumOf(archive);
        if (!actual.equalsIgnoreCase(expected)) {
            Files.deleteIfExists(archive);
            throw new IOException("The download does not match its checksum.");
        }
    }

    private static String checksumOf(Path archive) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(CHECKSUM_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(archive)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IOException(CHECKSUM_ALGORITHM + " is unavailable.", unavailable);
        }
    }

}
