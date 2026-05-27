package Client.features.search;

import Client.core.network.NetworkRequestClient;
import CommonClasses.dto.DashboardAuctionRow;
import Packets.MessageType;
import Packets.PacketMessage;
import Server.dao.AuctionDAO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchService {

    public List<DashboardAuctionRow> searchAuctions(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return new ArrayList<>();
        }

        if (NetworkRequestClient.isEnabled()) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("keyword", keyword);
                payload.put("limit", limit);
                PacketMessage response = NetworkRequestClient.request(
                        MessageType.SEARCH_AUCTIONS_REQUEST,
                        (HashMap<String, Object>) payload,
                        MessageType.SEARCH_AUCTIONS_RESPONSE);
                if (response.getPayload() instanceof List<?>) {
                    return (List<DashboardAuctionRow>) response.getPayload();
                }
            } catch (IOException e) {
                System.err.println("[SearchService] Network search unavailable, using DAO fallback: "
                        + e.getMessage());
            }
        }

        try {
            return AuctionDAO.getInstance().searchAuctionsByName(keyword, limit);
        } catch (Exception e) {
            System.err.println("[SearchService] Error searching auctions: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}
