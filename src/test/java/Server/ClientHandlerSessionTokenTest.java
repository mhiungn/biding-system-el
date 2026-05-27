package Server;

import CommonClasses.Items.Electronics;
import CommonClasses.Items.Item;
import CommonClasses.User;
import CommonClasses.dto.AuthResponse;
import CommonClasses.dto.NotificationDTO;
import CommonClasses.dto.WalletDTO;
import Packets.MessageType;
import Packets.NetworkConfig;
import Packets.NetworkErrorPayload;
import Packets.PacketMessage;
import Server.ClientHandler;
import Server.dao.AuctionDAO;
import Server.dao.AuctionSnapshot;
import Server.dao.ItemDAO;
import Server.dao.NotificationDAO;
import Server.dao.TestDatabaseHelper;
import Server.dao.UserDAO;
import Server.dao.WalletDAO;
import Server.service.AuthenticationService;
import Server.service.BiddingApplicationService;
import Server.service.NotificationApplicationService;
import Server.service.ProfileApplicationService;
import Server.service.SessionRegistry;
import Server.service.WalletApplicationService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientHandlerSessionTokenTest {
    private static TestRequestServer requestServer;
    private static String oldNetworkEnabled;
    private static String oldPort;
    private static String oldHost;

    private UserDAO userDAO;
    private ItemDAO itemDAO;
    private AuctionDAO auctionDAO;
    private WalletApplicationService walletService;
    private NotificationApplicationService notificationService;
    private TestRequestClient requestClient;

    @BeforeAll
    static void setUpAll() throws Exception {
        oldNetworkEnabled = System.getProperty(NetworkConfig.NETWORK_ENABLED_PROPERTY);
        oldPort = System.getProperty(NetworkConfig.PORT_PROPERTY);
        oldHost = System.getProperty(NetworkConfig.HOST_PROPERTY);

        TestDatabaseHelper.redirectToH2();
        TestDatabaseHelper.createAllTables();
        resetSingletons();

        requestServer = new TestRequestServer();
        System.setProperty(NetworkConfig.HOST_PROPERTY, NetworkConfig.DEFAULT_HOST);
        System.setProperty(NetworkConfig.PORT_PROPERTY, String.valueOf(requestServer.getPort()));
        System.setProperty(NetworkConfig.NETWORK_ENABLED_PROPERTY, "true");
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        if (requestServer != null) {
            requestServer.close();
        }
        TestDatabaseHelper.dropAllTables();
        resetSingletons();
        restoreProperty(NetworkConfig.NETWORK_ENABLED_PROPERTY, oldNetworkEnabled);
        restoreProperty(NetworkConfig.PORT_PROPERTY, oldPort);
        restoreProperty(NetworkConfig.HOST_PROPERTY, oldHost);
    }

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty(NetworkConfig.NETWORK_ENABLED_PROPERTY, "true");
        TestDatabaseHelper.createAllTables();
        TestDatabaseHelper.clearAllTables();
        resetSingletons();
        clearServerClientHandlers();

        userDAO = UserDAO.getInstance();
        itemDAO = ItemDAO.getInstance();
        auctionDAO = AuctionDAO.getInstance();
        walletService = WalletApplicationService.getInstance();
        notificationService = NotificationApplicationService.getInstance();
        requestClient = new TestRequestClient();
    }

    @AfterEach
    void tearDown() {
        clearServerClientHandlers();
        SessionRegistry.getInstance().clearAll();
    }

    private void clearServerClientHandlers() {
        try {
            Object serverInstance = getServerInstance();
            Object handlers = serverInstance.getClass().getMethod("getClientHandlers").invoke(serverInstance);
            handlers.getClass().getMethod("clear").invoke(handlers);
        } catch (Exception e) {
            throw new RuntimeException("Cannot clear server client handlers", e);
        }
    }

    @Test
    void loginAndRegisterResponsesReturnSessionTokens() throws Exception {
        createUser("login_user");

        PacketMessage loginResponse = requestClient.request(
                MessageType.LOGIN_REQUEST,
                new User("login_user", "pass", null, "USER"),
                MessageType.LOGIN_RESPONSE);

        AuthResponse login = requireAuthResponse(loginResponse);
        assertTrue(login.isSuccess());
        assertEquals("login_user", login.getUser().getUsername());
        assertNull(login.getUser().getPassword());
        assertNotNull(login.getToken());
        assertFalse(login.getToken().isBlank());
        assertNotNull(login.getExpiresAt());

        requestClient.clearToken();
        PacketMessage registerResponse = requestClient.request(
                MessageType.REGISTER_REQUEST,
                new User("registered_user", "pass", "registered_user@mail.com", "USER"),
                MessageType.REGISTER_RESPONSE);

        AuthResponse register = requireAuthResponse(registerResponse);
        assertTrue(register.isSuccess());
        assertEquals("registered_user", register.getUser().getUsername());
        assertNull(register.getUser().getPassword());
        assertNotNull(register.getToken());
        assertFalse(register.getToken().isBlank());
    }

    @Test
    void requestClientStoresLoginTokenAndSendsItOnNextRequest() throws Exception {
        createUser("bidder");

        requestClient.login("bidder");
        assertNotNull(requestClient.getToken());

        PacketMessage response = requestClient.request(
                MessageType.WALLET_BALANCE_REQUEST,
                null,
                MessageType.WALLET_BALANCE_RESPONSE);

        WalletDTO wallet = (WalletDTO) response.getPayload();
        assertEquals("bidder", wallet.getUsername());
    }

    @Test
    void protectedRequestWithValidTokenSucceeds() throws Exception {
        createUser("bidder");
        requestClient.login("bidder");

        PacketMessage response = requestClient.request(
                MessageType.WALLET_BALANCE_REQUEST,
                null,
                MessageType.WALLET_BALANCE_RESPONSE);

        assertTrue(response.getPayload() instanceof WalletDTO);
        WalletDTO wallet = (WalletDTO) response.getPayload();
        assertEquals("bidder", wallet.getUsername());
        assertEquals(100_000L, wallet.getBalance());
    }

    @Test
    void protectedRequestWithoutTokenFailsClearly() {
        createUser("bidder");
        requestClient.clearToken();

        IOException exception = assertThrows(IOException.class, () -> requestClient.request(
                MessageType.WALLET_BALANCE_REQUEST,
                null,
                MessageType.WALLET_BALANCE_RESPONSE));

        assertTrue(exception.getMessage().contains("AUTH_REQUIRED"));
    }

    @Test
    void protectedRequestWithInvalidTokenFailsClearly() {
        createUser("bidder");
        requestClient.setToken("invalid-token");

        IOException exception = assertThrows(IOException.class, () -> requestClient.request(
                MessageType.WALLET_BALANCE_REQUEST,
                null,
                MessageType.WALLET_BALANCE_RESPONSE));

        assertTrue(exception.getMessage().contains("AUTH_INVALID"));
    }

    @Test
    void placeBidUsesTokenIdentityAndIgnoresPayloadUsername() throws Exception {
        createAuctionFixture(1);
        requestClient.login("bidder");

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("auctionId", 1);
        payload.put("bid", 1_100f);
        payload.put("username", "seller");

        PacketMessage response = requestClient.request(
                MessageType.PLACE_BID,
                payload,
                MessageType.PLACE_BID);

        assertEquals(Boolean.TRUE, response.getPayload());
        assertEquals("bidder", auctionDAO.getHighestBidderUsername(1));
        assertEquals(1_100L, walletService.getWallet("bidder").getHeldAmount());
        assertEquals(0L, walletService.getWallet("seller").getHeldAmount());
    }

    @Test
    void depositUsesTokenIdentityAndIgnoresPayloadUsername() throws Exception {
        createUser("bidder");
        createUser("other");
        requestClient.login("bidder");

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("amount", 5_000L);
        payload.put("username", "other");

        PacketMessage response = requestClient.request(
                MessageType.WALLET_DEPOSIT_REQUEST,
                payload,
                MessageType.WALLET_DEPOSIT_RESPONSE);

        assertTrue(response.getPayload() instanceof WalletDTO);
        WalletDTO wallet = (WalletDTO) response.getPayload();
        assertTrue(wallet.isSuccess());
        assertEquals("bidder", wallet.getUsername());
        assertEquals(105_000L, walletService.getWallet("bidder").getBalance());
        assertEquals(100_000L, walletService.getWallet("other").getBalance());
    }

    @Test
    void notificationListOnlyReturnsCurrentTokenUsersNotifications() throws Exception {
        createUser("bidder");
        createUser("other");
        notificationService.notifyAuctionLost("bidder", 1, "Laptop");
        notificationService.notifyAuctionLost("other", 2, "Phone");
        requestClient.login("bidder");

        PacketMessage response = requestClient.request(
                MessageType.NOTIFICATION_LIST_REQUEST,
                "other",
                MessageType.NOTIFICATION_LIST_RESPONSE);

        assertTrue(response.getPayload() instanceof List<?>);
        List<?> notifications = (List<?>) response.getPayload();
        assertEquals(1, notifications.size());
        NotificationDTO notification = (NotificationDTO) notifications.get(0);
        assertEquals("bidder", notification.getUsername());
        assertEquals(Integer.valueOf(1), notification.getAuctionId());
    }

    @Test
    void logoutInvalidatesToken() throws Exception {
        createUser("bidder");
        requestClient.login("bidder");
        String token = requestClient.getToken();

        PacketMessage response = requestClient.request(
                MessageType.LOGOUT_REQUEST,
                null,
                MessageType.LOGOUT_RESPONSE);
        assertEquals(Boolean.TRUE, response.getPayload());

        requestClient.setToken(token);
        IOException exception = assertThrows(IOException.class, () -> requestClient.request(
                MessageType.WALLET_BALANCE_REQUEST,
                null,
                MessageType.WALLET_BALANCE_RESPONSE));

        assertTrue(exception.getMessage().contains("AUTH_INVALID"));
    }

    @Test
    void daoFallbackStillWorksWhenNetworkDisabled() {
        createUser("bidder");
        System.setProperty(NetworkConfig.NETWORK_ENABLED_PROPERTY, "false");

        WalletDTO wallet = walletService.deposit("bidder", 5_000L);

        assertTrue(wallet.isSuccess());
        assertEquals(105_000L, wallet.getBalance());
    }

    private AuthResponse requireAuthResponse(PacketMessage response) {
        assertTrue(response.getPayload() instanceof AuthResponse);
        return (AuthResponse) response.getPayload();
    }

    private void createUser(String username) {
        userDAO.save(username, new User(username, "pass", username + "@mail.com", "USER"));
        walletService.ensureWallet(username);
    }

    private void createAuctionFixture(int auctionId) {
        createUser("seller");
        createUser("bidder");

        Item item = new Electronics(1_000f, "Laptop " + auctionId, "New");
        itemDAO.save("item-" + auctionId, item);

        AuctionSnapshot snapshot = new AuctionSnapshot(
                auctionId,
                "seller",
                new Date(),
                new Date(System.currentTimeMillis() + 86_400_000L),
                "Time_Fixed",
                "OPEN",
                item,
                new LinkedList<>(),
                new ArrayList<>(),
                false);
        snapshot.setMinimumBidIncrement(50f);
        auctionDAO.save(String.valueOf(auctionId), snapshot);
    }

    private static void resetSingletons() throws Exception {
        TestDatabaseHelper.resetSingleton(UserDAO.class);
        TestDatabaseHelper.resetSingleton(ItemDAO.class);
        TestDatabaseHelper.resetSingleton(AuctionDAO.class);
        TestDatabaseHelper.resetSingleton(WalletDAO.class);
        TestDatabaseHelper.resetSingleton(NotificationDAO.class);
        TestDatabaseHelper.resetSingleton(WalletApplicationService.class);
        TestDatabaseHelper.resetSingleton(NotificationApplicationService.class);
        TestDatabaseHelper.resetSingleton(BiddingApplicationService.class);
        TestDatabaseHelper.resetSingleton(AuthenticationService.class);
        TestDatabaseHelper.resetSingleton(ProfileApplicationService.class);
        TestDatabaseHelper.resetSingleton(SessionRegistry.class);
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static Object getServerInstance() {
        try {
            Class<?> serverClass = Class.forName("Server.Server");
            return serverClass.getMethod("getInstance").invoke(null);
        } catch (Exception e) {
            throw new RuntimeException("Cannot get Server instance", e);
        }
    }

    private static final class TestRequestClient {
        private String token;

        private PacketMessage request(MessageType type, Serializable payload, MessageType expectedResponse)
                throws IOException {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(NetworkConfig.host(), NetworkConfig.port()),
                        NetworkConfig.DEFAULT_CONNECT_TIMEOUT_MS);
                socket.setSoTimeout(NetworkConfig.DEFAULT_READ_TIMEOUT_MS);
                try (ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
                        ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream())) {
                    outputStream.writeObject(new PacketMessage(type, payload, token));
                    outputStream.flush();
                    outputStream.reset();

                    while (true) {
                        Object received = inputStream.readObject();
                        if (!(received instanceof PacketMessage)) {
                            throw new IOException("Unexpected response from server: " + received);
                        }
                        PacketMessage response = (PacketMessage) received;
                        if (response.getMessageType() == expectedResponse) {
                            return response;
                        }
                        if (response.getMessageType() == MessageType.NETWORK_ERROR) {
                            throw new IOException(formatNetworkError(response));
                        }
                    }
                } catch (ClassNotFoundException e) {
                    throw new IOException("Cannot deserialize server response", e);
                }
            }
        }

        private AuthResponse login(String username) throws IOException {
            PacketMessage response = request(
                    MessageType.LOGIN_REQUEST,
                    new User(username, "pass", null, "USER"),
                    MessageType.LOGIN_RESPONSE);
            AuthResponse auth = (AuthResponse) response.getPayload();
            assertTrue(auth.isSuccess());
            token = auth.getToken();
            return auth;
        }

        private String getToken() {
            return token;
        }

        private void setToken(String token) {
            this.token = token;
        }

        private void clearToken() {
            token = null;
        }

        private String formatNetworkError(PacketMessage response) {
            if (response.getPayload() instanceof NetworkErrorPayload) {
                NetworkErrorPayload error = (NetworkErrorPayload) response.getPayload();
                return error.getCode() + ": " + error.getMessage();
            }
            return "Network request failed";
        }
    }

    private static final class TestRequestServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private volatile boolean running = true;

        private TestRequestServer() throws IOException {
            serverSocket = new ServerSocket(0);
            executor.submit(this::acceptLoop);
        }

        private int getPort() {
            return serverSocket.getLocalPort();
        }

        private void acceptLoop() {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setSoTimeout(NetworkConfig.DEFAULT_CLIENT_READ_TIMEOUT_MS);
                    Client clientObj = new Client("Guest_" + socket.getPort());
                    ClientHandler handler = new ClientHandler(clientObj, socket);
                    executor.submit(handler);
                } catch (IOException e) {
                    if (running) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        @Override
        public void close() throws IOException, InterruptedException {
            running = false;
            serverSocket.close();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
