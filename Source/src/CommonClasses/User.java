package CommonClasses;
import java.io.Serializable;

public class User extends Entity {
    protected String username;
    protected String email;
    protected String password;
    protected String role;
    protected String phone;
    protected String location;

    public User(String username, String password, String email, String role) {
        this(username, password, email, role, null, null);
    }

    public User(String username, String password, String email, String role, String phone, String location) {
        super();
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role;
        this.phone = phone;
        this.location = location;
    }
    public String getUsername() {
    	return username; 
    }
    public String getPassword() {
    	return password;
    }
    public String getEmail() {
    	return email ; 
    }
    public String getRole() {
    	return role; 
    }
    public String getPhone() {
        return phone;
    }
    public String getLocation() {
        return location;
    }

    @Override
    public String getDisplayInfo() {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return "Quản trị viên: " + username + " (Quyền cao nhất)";
        }
        return "Người dùng: " + username + " (ID: " + id + ")";
    }

}
