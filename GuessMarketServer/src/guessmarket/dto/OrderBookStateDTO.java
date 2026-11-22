package guessmarket.dto;

import java.util.List;

public record OrderBookStateDTO(
        int eventId,
        String eventName,
        String eventState,
        String tradingMethod,
        double lastPrice,
        double bid,
        double ask,
        double mid,
        double spread,
        List<OrderBookLevelDTO> buyLevels,
        List<OrderBookLevelDTO> sellLevels,
        List<OrderBookParticipantDTO> participants
) {
}