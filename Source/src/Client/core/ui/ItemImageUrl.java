package Client.core.ui;

import java.nio.file.Path;

public final class ItemImageUrl {
    public static final int DETAIL_MAX_WIDTH = 900;
    public static final int THUMBNAIL_MAX_WIDTH = 400;

    private static final String CLOUDINARY_UPLOAD_MARKER = "/image/upload/";

    private ItemImageUrl() {
    }

    public static String detail(String path) {
        return cssUrl(path, DETAIL_MAX_WIDTH);
    }

    public static String thumbnail(String path) {
        return cssUrl(path, THUMBNAIL_MAX_WIDTH);
    }

    public static String cssUrl(String path, int maxWidth) {
        if (path == null || path.isBlank()) {
            return path;
        }
        String trimmed = path.trim();
        String transformed = cloudinaryUrl(trimmed, maxWidth);
        if (isRemoteOrFileUrl(transformed)) {
            return transformed;
        }
        return Path.of(transformed).toUri().toString();
    }

    static String cloudinaryUrl(String url, int maxWidth) {
        if (url == null || url.isBlank() || maxWidth <= 0 || !url.contains(CLOUDINARY_UPLOAD_MARKER)) {
            return url;
        }
        if (hasCloudinaryTransformation(url)) {
            return url;
        }
        String transformation = "c_limit,w_" + maxWidth + ",q_auto:good,f_auto";
        return url.replace(CLOUDINARY_UPLOAD_MARKER, CLOUDINARY_UPLOAD_MARKER + transformation + "/");
    }

    private static boolean hasCloudinaryTransformation(String url) {
        int markerIndex = url.indexOf(CLOUDINARY_UPLOAD_MARKER);
        if (markerIndex < 0) {
            return false;
        }
        int segmentStart = markerIndex + CLOUDINARY_UPLOAD_MARKER.length();
        int segmentEnd = url.indexOf('/', segmentStart);
        if (segmentEnd < 0) {
            return false;
        }
        String firstSegment = url.substring(segmentStart, segmentEnd);
        return !firstSegment.matches("v\\d+");
    }

    private static boolean isRemoteOrFileUrl(String path) {
        return path.startsWith("http://") || path.startsWith("https://") || path.startsWith("file:");
    }
}
