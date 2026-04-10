package CommonClasses;
import java.util.ArrayList;
import java.util.List;

public class AuctionManager {
    private static AuctionManager instance;
    private List<User> userList;

    private AuctionManager() {
        userList = new ArrayList<>();
    }
    public static AuctionManager getInstance() {
        if (instance == null) {
            instance = new AuctionManager();
        }
        return instance ;
    }

}