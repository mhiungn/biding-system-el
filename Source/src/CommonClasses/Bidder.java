package CommonClasses;

public class Bidder extends User {
    public Bidder(String username, String password, String email) {
        super(username, password, email, "BIDDER");
    }
    @Override
    public String getDisplayInfo() {
        return "Người  mua: " + username + " (ID: " + id + ")";
    }
}