package Client.core.ui;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AvatarService {
    private static final String DEFAULT_AVATAR_RESOURCE = "/client/images/user.png";
    private static final String ERROR_LISTENER_IMAGE_KEY = "client.avatar.errorListenerImage";
    private static final int AVATAR_MAX_WIDTH = 128;
    private static final int MAX_CACHE_SIZE = 64;

    private static final AvatarService INSTANCE = new AvatarService();

    private final Image defaultAvatar = new Image(DEFAULT_AVATAR_RESOURCE, AVATAR_MAX_WIDTH, AVATAR_MAX_WIDTH,
            false, true, true);
    private final Map<String, Image> cache = new LinkedHashMap<>(16, 0.75f, true);

    public static AvatarService getInstance() {
        return INSTANCE;
    }

    private AvatarService() {
    }

    public synchronized Image getAvatarImage(String userId, String imageUrl) {
        String normalizedUrl = normalizeImageUrl(imageUrl);
        if (normalizedUrl == null) {
            return defaultAvatar;
        }

        String key = cacheKey(userId, normalizedUrl);
        Image cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        Image image = new Image(normalizedUrl, AVATAR_MAX_WIDTH, AVATAR_MAX_WIDTH, false, true, true);
        cache.put(key, image);
        trimCache();
        return image;
    }

    public Image getDefaultAvatar() {
        return defaultAvatar;
    }

    public void applyAvatarImage(ImageView imageView, Image image) {
        if (imageView == null) {
            return;
        }

        Image avatar = image == null || image.isError() ? defaultAvatar : image;
        imageView.setImage(avatar);
        if (avatar == defaultAvatar) {
            return;
        }

        if (imageView.getProperties().get(ERROR_LISTENER_IMAGE_KEY) == avatar) {
            return;
        }

        imageView.getProperties().put(ERROR_LISTENER_IMAGE_KEY, avatar);
        avatar.errorProperty().addListener((observable, wasError, isError) -> {
            if (isError && imageView.getImage() == avatar) {
                imageView.setImage(defaultAvatar);
            }
        });
    }

    public synchronized void invalidateAvatar(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        String prefix = userId.trim() + "|";
        cache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public synchronized void clear() {
        cache.clear();
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        String trimmed = imageUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("file:")) {
            return ItemImageUrl.cssUrl(trimmed, AVATAR_MAX_WIDTH);
        }
        return Path.of(trimmed).toUri().toString();
    }

    private String cacheKey(String userId, String normalizedUrl) {
        String normalizedUserId = userId == null || userId.isBlank() ? "unknown" : userId.trim();
        return normalizedUserId + "|" + normalizedUrl;
    }

    private void trimCache() {
        Iterator<Map.Entry<String, Image>> iterator = cache.entrySet().iterator();
        while (cache.size() > MAX_CACHE_SIZE && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }
}
