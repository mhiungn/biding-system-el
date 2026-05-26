package Server.service;

import CommonClasses.dto.AuctionUpdatePushDTO;
import CommonClasses.dto.NotificationPushDTO;
import CommonClasses.dto.WalletDTO;
import CommonClasses.dto.WalletUpdatePushDTO;
import Packets.MessageType;
import Packets.PacketMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushEventRouterReflectionTest {

    @Test
    void routesPushEventsThroughProvidedUiExecutor() throws Exception {
        Class<?> routerType = Class.forName("Client.core.network.PushEventRouter");
        Class<?> listenerType = Class.forName("Client.core.network.PushEventListener");
        Object router = routerType.getDeclaredConstructor().newInstance();
        List<String> handled = new ArrayList<>();
        AtomicBoolean executorUsed = new AtomicBoolean(false);

        Object listener = Proxy.newProxyInstance(
                listenerType.getClassLoader(),
                new Class<?>[]{listenerType},
                (proxy, method, args) -> {
                    if ("onAuctionUpdatePush".equals(method.getName())) {
                        handled.add("auction:" + ((AuctionUpdatePushDTO) args[0]).getAuctionId());
                    } else if ("onNotificationPush".equals(method.getName())) {
                        handled.add("notification:" + ((NotificationPushDTO) args[0]).getUsername());
                    } else if ("onWalletUpdatePush".equals(method.getName())) {
                        handled.add("wallet:" + ((WalletUpdatePushDTO) args[0]).getUsername());
                    }
                    return null;
                });
        routerType.getMethod("register", listenerType).invoke(router, listener);

        route(routerType, router, new PacketMessage(
                MessageType.AUCTION_UPDATE_PUSH,
                new AuctionUpdatePushDTO(7, null, "TEST")),
                runnable -> {
                    executorUsed.set(true);
                    runnable.run();
                });
        route(routerType, router, new PacketMessage(
                MessageType.NOTIFICATION_PUSH,
                new NotificationPushDTO("bidder", 7, "OUTBID", 1)),
                Runnable::run);
        route(routerType, router, new PacketMessage(
                MessageType.WALLET_UPDATE_PUSH,
                new WalletUpdatePushDTO("bidder", WalletDTO.success("bidder", 100L, 10L, 0L), "TEST")),
                Runnable::run);

        assertTrue(executorUsed.get());
        assertEquals(List.of("auction:7", "notification:bidder", "wallet:bidder"), handled);
    }

    @Test
    void routeDoesNotThrowWhenNoPageListenerIsRegistered() throws Exception {
        Class<?> routerType = Class.forName("Client.core.network.PushEventRouter");
        Object router = routerType.getDeclaredConstructor().newInstance();

        assertDoesNotThrow(() -> route(
                routerType,
                router,
                new PacketMessage(MessageType.AUCTION_UPDATE_PUSH, new AuctionUpdatePushDTO(1, null, "TEST")),
                Runnable::run));
    }

    private static void route(Class<?> routerType, Object router, PacketMessage packet,
                              Consumer<Runnable> executor) throws Exception {
        routerType.getMethod("route", PacketMessage.class, Consumer.class).invoke(router, packet, executor);
    }
}
