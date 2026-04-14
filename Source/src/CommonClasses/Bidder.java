package CommonClasses;

public class Bidder extends User {
    public Bidder(String username, String password, String email) {
        super(username, password, email, "BIDDER");
    }
    @Override
    public String getDisplayInfo() {
        return "Người  mua: " + username + " (ID: " + id + ")";
    }
    @Override
    public void showMenu() {
        System.out.println("=== MENU BIDDER ===");
        System.out.println("1. Xem danh sách hàng đang đấu giá");
        System.out.println("2. Đặt giá thầu (Place Bid)");
        System.out.println("3. Xem lịch sử đấu giá của bạn");
    }
}