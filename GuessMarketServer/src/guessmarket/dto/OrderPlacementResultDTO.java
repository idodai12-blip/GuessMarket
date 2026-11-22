package guessmarket.dto;

public record OrderPlacementResultDTO(
        int eventId,
        String eventName,
        String userName,
        String optionName,
        String side,
        int requestedQuantity,
        int filledQuantity,
        int remainingQuantity,
        double pricePerShare,
        double totalCost,
        String eventState
) {
}