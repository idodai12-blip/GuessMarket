package guessmarket.dto;

public record OrderBookParticipantDTO(
        String userName,
        String optionName,
        int quantity,
        double totalValue
) {
}