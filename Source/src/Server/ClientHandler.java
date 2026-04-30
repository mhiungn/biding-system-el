package Server;
import java.io.*;
import java.net.Socket;
import Packets.PacketMessage;
import Packets.MessageType;
import java.io.IOException;
import java.io.ObjectOutputStream;
import Server.dao.UserDAO;
import CommonClasses.User;

/**
 * Handles the communication with a single connected client.
 * <p>
 * Each {@code ClientHandler} runs on its own thread and manages sending/receiving
 * packets to/from the associated {@link Client}.
 * </p>
 */
public class ClientHandler implements Runnable {

    private Client client;
    private Socket socket;
    private ObjectOutputStream outputStream;

    /**
     * Constructs a ClientHandler for the given client and socket.
     *
     * @param client the client this handler manages
     * @param socket the socket connection to the client
     */
    public ClientHandler(Client client, Socket socket) {
        this.client = client;
        this.socket = socket;
        try {
            this.outputStream = new ObjectOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the client managed by this handler.
     *
     * @return the {@link Client}
     */
    public Client getClient() {
        return client;
    }

    /**
     * Sends a packet to the client.
     *
     * @param packet the packet to send
     * @throws IOException if an I/O error occurs
     */
    public void sendPacket(PacketMessage packet) throws IOException {
        if (outputStream != null) {
            outputStream.writeObject(packet);
            outputStream.flush();
        }
    }

    @Override
    public void run() {
        // 1. Khởi tạo luồng nhận dữ liệu (InputStream) từ socket
        // Sử dụng try-with-resources để tự động đóng socket khi gặp lỗi hoặc ngắt kết nối
        try (ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream())) {

            System.out.println(" [Network] Đang lắng nghe từ client: " + (client != null ? client.getUsername() : "Guest"));

            while (true) {
                // 2. Chờ nhận dữ liệu từ Client gửi lên
                Object received = inputStream.readObject();

                if (received instanceof PacketMessage) {
                    PacketMessage request = (PacketMessage) received;

                    // --- KHU VỰC XỬ LÝ LOGIC ---

                    // Xử lý yêu cầu ĐĂNG NHẬP (LOGIN_REQUEST)
                    if (request.getMessageType() == MessageType.LOGIN_REQUEST) {
                        User loginInfo = (User) request.getPayload();

                        // Gọi UserDAO để xác thực tài khoản
                        User userResult = UserDAO.getInstance().authenticate(loginInfo.getUsername(), loginInfo.getPassword());

                        if (userResult != null) {
                            // Cập nhật tên thật cho Client thay vì "Guest"
                            this.client.setUsername(userResult.getUsername());

                            // Đưa Handler này vào Map của Server để có thể gửi tin cho user này
                            Server.getInstance().getClientHandlers().put(userResult.getUsername(), this);

                            System.out.println(" [Network] User '" + userResult.getUsername() + "' đã đăng nhập thành công.");
                        }

                        // Gửi gói tin trả lời (Response) về cho Client
                        sendPacket(new PacketMessage(MessageType.LOGIN_RESPONSE, userResult));
                    }

                    // Sau này nếu có thêm logic khác như xem danh sách hay đặt giá, viết tiếp else if ở đây
                }
            }
        } catch (EOFException e) {
            System.out.println(" [Network] Client đã ngắt kết nối chủ động.");
        } catch (Exception e) {
            System.err.println(" [Network] Lỗi kết nối hoặc xử lý dữ liệu: " + e.getMessage());
        } finally {
            // Khi ngắt kết nối, dọn dẹp danh sách trong Server (nếu cần)
            if (client != null && client.getUsername() != null) {
                Server.getInstance().getClientHandlers().remove(client.getUsername());
            }
        }
    }
}
