package CommonClasses.dto;

import java.io.Serializable;

public class WalletDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final long balance;
    private final long heldAmount;
    private final long availableBalance;
    private final long totalSpent;
    private final boolean success;
    private final String message;

    public WalletDTO(String username, long balance, long heldAmount, long availableBalance, long totalSpent,
                     boolean success, String message) {
        this.username = username;
        this.balance = balance;
        this.heldAmount = heldAmount;
        this.availableBalance = availableBalance;
        this.totalSpent = totalSpent;
        this.success = success;
        this.message = message;
    }

    public static WalletDTO success(String username, long balance, long heldAmount, long totalSpent) {
        return new WalletDTO(username, balance, heldAmount, balance - heldAmount, totalSpent, true, null);
    }

    public static WalletDTO failure(String username, String message) {
        return new WalletDTO(username, 0, 0, 0, 0, false, message);
    }

    public String getUsername() {
        return username;
    }

    public long getBalance() {
        return balance;
    }

    public long getHeldAmount() {
        return heldAmount;
    }

    public long getAvailableBalance() {
        return availableBalance;
    }

    public long getTotalSpent() {
        return totalSpent;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
