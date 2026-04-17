package CommonClasses;

public class Seller extends User {

    public Seller(String username, String password, String email) {
        super(username, password, email, "SELLER");
    }
    @Override
    public String getDisplayInfo() {
        return "Người bán: " + username + " (ID: " + id + ")";
    }
}