package Server.service;

import Server.dao.DatabaseConnection;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Tiện ích hỗ trợ tự động di chuyển (migrate) ảnh cũ từ local lên Cloudinary.
 * <p>
 * Script này sẽ quét bảng `item_images` trong database Clever Cloud để tìm các ảnh
 * có đường dẫn cục bộ (không bắt đầu bằng http/https). Nếu file vật lý vẫn tồn tại
 * trong folder `uploads/items/` của máy, nó sẽ tự động upload lên Cloudinary và
 * cập nhật lại đường dẫn mới trong database để tất cả máy khách khác đều xem được.
 * </p>
 */
public class ImageMigrationUtil {

    public static void runMigration() {
        System.out.println("[ImageMigration] Bat dau kiem tra va migrate anh cu len Cloudinary...");
        
        String selectSql = "SELECT image_id, item_id, image_path FROM item_images "
                + "WHERE image_path NOT LIKE 'http://%' AND image_path NOT LIKE 'https://%'";

        String updateSql = "UPDATE item_images SET image_path = ? WHERE image_id = ?";

        int countSuccess = 0;
        int countFailed = 0;
        int countTotalFound = 0;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement selectPs = conn.prepareStatement(selectSql);
             PreparedStatement updatePs = conn.prepareStatement(updateSql);
             ResultSet rs = selectPs.executeQuery()) {

            ImageStorageService storageService = ImageStorageService.getInstance();

            while (rs.next()) {
                countTotalFound++;
                String imageId = rs.getString("image_id");
                String itemId = rs.getString("item_id");
                String imagePath = rs.getString("image_path");

                System.out.println("[ImageMigration] Tim thay anh local can migrate: ID=" + imageId + ", ItemID=" + itemId + ", Path=" + imagePath);

                // Thử tìm file ảnh cục bộ
                File imageFile = findLocalFile(itemId, imagePath);

                if (imageFile != null && imageFile.exists() && imageFile.isFile()) {
                    try {
                        System.out.println("[ImageMigration] Dang upload file: " + imageFile.getAbsolutePath());
                        // Upload lên Cloudinary
                        String cloudinaryUrl = storageService.saveItemImage(itemId, imageFile);

                        // Cập nhật database
                        updatePs.setString(1, cloudinaryUrl);
                        updatePs.setString(2, imageId);
                        updatePs.executeUpdate();

                        System.out.println("[ImageMigration] -> Migrate thanh cong! Url moi: " + cloudinaryUrl);
                        countSuccess++;
                    } catch (Exception e) {
                        System.err.println("[ImageMigration] -> ERROR khi upload/update: " + e.getMessage());
                        countFailed++;
                    }
                } else {
                    System.out.println("[ImageMigration] -> Warning: Khong tim thay file anh cuc bo tren o dia. Bo qua.");
                    countFailed++;
                }
            }

        } catch (SQLException e) {
            System.err.println("[ImageMigration] Loi ket noi hoac truy van database: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("[ImageMigration] Hoan tat qua trinh di chuyen anh.");
        System.out.println("  - Tong so anh local tim thay: " + countTotalFound);
        System.out.println("  - Di chuyen thanh cong: " + countSuccess);
        System.out.println("  - Bo qua hoac that bai: " + countFailed);
    }

    /**
     * Tìm file ảnh cục bộ dựa trên itemId và đường dẫn cũ lưu trong DB.
     */
    private static File findLocalFile(String itemId, String originalPath) {
        // 1. Thử dùng đường dẫn gốc trong DB
        File f1 = new File(originalPath);
        if (f1.exists() && f1.isFile()) {
            return f1;
        }

        // 2. Thử tìm theo cấu trúc thư mục uploads/items/{itemId}/{filename}
        String filename = originalPath.substring(Math.max(originalPath.lastIndexOf('/'), originalPath.lastIndexOf('\\')) + 1);
        File f2 = new File("uploads/items/" + itemId + "/" + filename);
        if (f2.exists() && f2.isFile()) {
            return f2;
        }

        // 3. Thử tìm trong thư mục cha (nếu filename chứa tên thư mục con)
        File f3 = new File("uploads/items/" + filename);
        if (f3.exists() && f3.isFile()) {
            return f3;
        }

        return null;
    }
}
