package guessmarket.dto;

public record OrderBookLevelDTO(
        String optionName,
        String userName,
        String side,
        int quantity,
        double pricePerShare
) {
}