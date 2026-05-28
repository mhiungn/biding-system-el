package Server.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Service xử lý lưu trữ ảnh sản phẩm lên Cloudinary.
 * <p>
 * Thay thế logic lưu file cục bộ bằng cách upload ảnh lên Cloudinary
 * và lưu URL dạng {@code https://res.cloudinary.com/...} vào database.
 * Điều này giúp mọi máy đều hiển thị được ảnh mà không cần chia sẻ file vật lý.
 * </p>
 *
 * <h3>Cấu hình:</h3>
 * Đọc từ file {@code Source/resources/cloudinary.properties}:
 * <pre>
 *   cloudinary.cloud_name=YOUR_CLOUD_NAME
 *   cloudinary.api_key=YOUR_API_KEY
 *   cloudinary.api_secret=YOUR_API_SECRET
 * </pre>
 */
public class ImageStorageService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    private static final String CONFIG_FILE = "/cloudinary.properties";

    private static volatile ImageStorageService instance;
    private final Cloudinary cloudinary;

    // ========================== Singleton ==========================

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
        this.cloudinary = buildCloudinary();
    }

    // ========================== Public API ==========================

    /**
     * Upload ảnh sản phẩm lên Cloudinary và trả về URL công khai.
     *
     * @param itemId ID sản phẩm — được dùng làm tên thư mục trên Cloudinary
     * @param source File ảnh cần upload
     * @return HTTPS URL của ảnh trên Cloudinary (ví dụ: {@code https://res.cloudinary.com/...})
     * @throws RuntimeException nếu upload thất bại
     */
    public String saveItemImage(String itemId, File source) {
        validateImageFile(source);
        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    source,
                    ObjectUtils.asMap(
                            "folder", "auction-items/" + itemId,
                            "resource_type", "image"
                    )
            );
            String url = (String) uploadResult.get("secure_url");
            if (url == null || url.isBlank()) {
                throw new RuntimeException("Cloudinary không trả về URL hợp lệ sau khi upload.");
            }
            System.out.println("[ImageStorageService] Da upload anh len Cloudinary: " + url);
            return url;
        } catch (IOException e) {
            throw new RuntimeException("Khong the upload anh len Cloudinary: " + e.getMessage(), e);
        }
    }

    public String saveProfileImage(String username, File source) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required for profile image upload.");
        }
        validateImageFile(source);
        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    source,
                    ObjectUtils.asMap(
                            "folder", "user-profiles/" + safeCloudinaryFolderName(username),
                            "resource_type", "image"
                    )
            );
            String url = (String) uploadResult.get("secure_url");
            if (url == null || url.isBlank()) {
                throw new RuntimeException("Cloudinary khong tra ve URL hop le sau khi upload.");
            }
            System.out.println("[ImageStorageService] Da upload anh profile len Cloudinary: " + url);
            return url;
        } catch (IOException e) {
            throw new RuntimeException("Khong the upload anh profile len Cloudinary: " + e.getMessage(), e);
        }
    }

    /**
     * Kiểm tra tính hợp lệ của file ảnh trước khi upload.
     *
     * @param file File ảnh cần kiểm tra
     * @throws IllegalArgumentException nếu file không hợp lệ
     */
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

    private String safeCloudinaryFolderName(String value) {
        String safe = value.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        return safe.isEmpty() ? "unknown-user" : safe;
    }

    // ========================== Private ==========================

    /**
     * Đọc file cloudinary.properties và khởi tạo Cloudinary client.
     */
    private Cloudinary buildCloudinary() {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream(CONFIG_FILE)) {
            if (in == null) {
                throw new RuntimeException(
                        "[ImageStorageService] Khong tim thay file cloudinary.properties. "
                        + "Hay tao file Source/resources/cloudinary.properties voi noi dung:\n"
                        + "  cloudinary.cloud_name=YOUR_CLOUD_NAME\n"
                        + "  cloudinary.api_key=YOUR_API_KEY\n"
                        + "  cloudinary.api_secret=YOUR_API_SECRET"
                );
            }
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("[ImageStorageService] Loi khi doc cloudinary.properties", e);
        }

        String cloudName = props.getProperty("cloudinary.cloud_name", "").trim();
        String apiKey    = props.getProperty("cloudinary.api_key", "").trim();
        String apiSecret = props.getProperty("cloudinary.api_secret", "").trim();

        if (cloudName.isEmpty() || cloudName.equals("YOUR_CLOUD_NAME")
                || apiKey.isEmpty() || apiKey.equals("YOUR_API_KEY")
                || apiSecret.isEmpty() || apiSecret.equals("YOUR_API_SECRET")) {
            throw new RuntimeException(
                    "[ImageStorageService] Cloudinary chua duoc cau hinh. "
                    + "Hay dien cloud_name, api_key, api_secret vao file Source/resources/cloudinary.properties."
            );
        }

        System.out.println("[ImageStorageService] Khoi tao Cloudinary voi cloud_name=" + cloudName);
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key",    apiKey,
                "api_secret", apiSecret,
                "secure",     true
        ));
    }
}
