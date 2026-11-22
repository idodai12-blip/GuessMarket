package guessmarket.dto;

public record UserDTO(
        String name,
        double balance,
        boolean isMarketMaker
) {}