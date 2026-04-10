package CommonClasses;

public class Seller extends User {

    public Seller(String username, String password, String email) {
        super(username, password, email, "SELLER");
    }
    @Override
    public String getDisplayInfo() {
        return "Người bán: " + username + " (ID: " + id + ")";
    }
    @Override
    public void showMenu() {
        System.out.println("=== MENU SELLER ===");
        System.out.println("1. Đăng sản phẩm mới");
        System.out.println("2. Quản lý danh sách hàng đang bán");
        System.out.println("3. Kết thúc phiên đấu giá sớm");
    }
}