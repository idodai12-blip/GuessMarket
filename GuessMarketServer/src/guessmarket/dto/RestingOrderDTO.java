package guessmarket.dto;

public record RestingOrderDTO(
        String optionName,
        String side,
        int quantity,
        double pricePerShare
) {}