package guessmarket.dto;

import java.util.List;

public record UserEventInvolvementDTO(
        int eventId,
        String eventName,
        String tradingMethod,      // "LMSR" or "ORDER_BOOK"
        double totalCommissionPaid,
        List<TradeDTO> trades,              // populated for LMSR
        List<OptionHoldingDTO> holdings,    // populated for ORDER_BOOK - shares actually owned
        List<RestingOrderDTO> restingOrders,// populated for ORDER_BOOK - pending unfilled orders
        double reservedCash,                // cash currently held back for this user's pending buy orders
        Double profitOrLoss,                // non-null only for a closed ORDER_BOOK event
        String eventState,
        String winningOption        // null if not closed
) {}