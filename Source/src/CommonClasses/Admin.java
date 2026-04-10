package CommonClasses;

public class Admin extends User {
    public Admin(String username, String password, String email) {
        super(username, password, email, "ADMIN");
    }
    @Override
    public String getDisplayInfo() {
        return "Quản trị viên: " + username + " (Quyền cao nhất)";
    }
    @Override
    public void showMenu() {
        System.out.println("=== MENU ADMIN ===");
        System.out.println("1. Kiểm duyệt người dùng");
        System.out.println("2. Xóa các phiên đấu giá vi phạm");
        System.out.println("3. Thống kê doanh thu sàn");
    }
}