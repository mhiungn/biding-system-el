package Server.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

public class ImageStorageService {
    public static final String UPLOAD_DIR_PROPERTY = "auction.upload.dir";
    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;

    private static volatile ImageStorageService instance;

    public static ImageStorageService getInstance() {
        if (instance == null) {
            synchronized (ImageStorageService.class) {
                if (instance == null) {
                    instance = new ImageStorageService();
                }
            }
        }
        return instance;
    }

    private ImageStorageService() {
    }

    public String saveItemImage(String itemId, File source) {
        validateImageFile(source);
        try {
            Path itemUploadDir = uploadRoot().resolve(itemId);
            Files.createDirectories(itemUploadDir);

            String extension = extensionOf(source.getName());
            Path target = itemUploadDir.resolve(UUID.randomUUID() + extension);
            Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Could not copy uploaded picture.", e);
        }
    }

    public void validateImageFile(File file) {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("Selected image file is not valid.");
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".png")) {
            throw new IllegalArgumentException("Only JPG and PNG images are supported.");
        }
        if (file.length() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Each picture must be 5 MB or smaller.");
        }
    }

    private Path uploadRoot() {
        String configured = System.getProperty(UPLOAD_DIR_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return Path.of(System.getProperty("user.dir"), "uploads", "items");
    }

    private String extensionOf(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase(Locale.ROOT) : "";
    }
}
