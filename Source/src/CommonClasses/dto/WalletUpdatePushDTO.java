package CommonClasses.dto;

import java.io.Serializable;

public class WalletUpdatePushDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final WalletDTO wallet;
    private final String reason;

    public WalletUpdatePushDTO(String username, WalletDTO wallet, String reason) {
        this.username = username;
        this.wallet = wallet;
        this.reason = reason;
    }

    public String getUsername() {
        return username;
    }

    public WalletDTO getWallet() {
        return wallet;
    }

    public String getReason() {
        return reason;
    }
}
