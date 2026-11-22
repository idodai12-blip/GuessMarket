package guessmarket.engine.domain;

import java.io.Serializable;

public record LmsrXmlData(
        int liquidityParameter
) implements TradingMethodXmlData, Serializable {
}