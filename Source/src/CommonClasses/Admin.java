package CommonClasses;

public class Admin extends User {
    public Admin(String username, String password, String email) {
        super(username, password, email, "ADMIN");
    }
    @Override
    public String getDisplayInfo() {
        return "Quản tri viên: " + username + " (Quyền cao nhất)";
    }
}