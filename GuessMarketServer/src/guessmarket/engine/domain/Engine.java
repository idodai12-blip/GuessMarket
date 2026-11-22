package guessmarket.engine.domain;

import guessmarket.dto.*;
import guessmarket.engine.market.OrderSide;

import java.util.List;

public interface Engine {

    void loadMarketFromXml(String xmlFilePath);

    List<EventDTO> getEventSummaries();

    EventStateDTO getEventState(int eventId);

    PurchaseResultDTO purchaseShares(int eventId, int optionIndex, int quantity, String userName);

    OrderPlacementResultDTO placeOrder(
            int eventId,
            String userName,
            int optionIndex,
            OrderSide side,
            int quantity,
            double pricePerShare
    );

    OrderBookStateDTO getOrderBookState(int eventId);

    void openEvent(int eventId, String userName);

    EventStateDTO closeEvent(int eventId, int winningOptionIndex, String userName);

    boolean isEngineLoaded();

    void saveState(String filePath);

    void loadState(String filePath);

    List<UserDTO> getUserSummaries();

    UserStateDTO getUserState(String userName);
}