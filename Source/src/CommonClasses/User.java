package CommonClasses;
import java.io.Serializable;

public abstract class User extends Entity {
    protected String username;
    protected String email;
    protected String password;
    protected String role;

    public User(String username, String password, String email, String role) {
        super();
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role;
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

    @Override
    public String getDisplayInfo() {
        return String.format("[%s] ID: %s | User: %s | Email: %s", role, id, username, email);
    }

}