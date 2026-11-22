package guessmarket.dto;

import java.util.List;

public record UserStateDTO(
        String name,
        double balance,
        List<UserEventInvolvementDTO> activeInvolvements
) {}
