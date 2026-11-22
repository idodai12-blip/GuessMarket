package guessmarket.dto;

public record OptionHoldingDTO(
        String optionName,
        int quantity,
        double amountPaid
) {}