package guessmarket.engine.domain;

import java.io.Serializable;

public record OrderBookXmlData(
        boolean allowMint,
        int initial,
        int d
) implements TradingMethodXmlData, Serializable {
}