package guessmarket.engine.market;

import java.io.Serializable;

public final class Trade implements Serializable {
    private final String userName;
    private final Option option;
    private final int quantity;
    private final double purchaseCost;
    private final double commissionPaid;

    Trade(String userName, Option option, int quantity, double pricePaid, double commissionPaid) {
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException(
                    "Trade user name cannot be null or blank"
            );
        }

        if (option == null) {
            throw new IllegalArgumentException(
                    "Trade option cannot be null"
            );
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Trade quantity must be greater than zero"
            );
        }

        if (!Double.isFinite(pricePaid) || pricePaid < 0.0) {
            throw new IllegalArgumentException(
                    "Purchase cost must be finite and non-negative"
            );
        }

        if (!Double.isFinite(commissionPaid) || commissionPaid < 0.0) {
            throw new IllegalArgumentException(
                    "Commission paid must be finite and non-negative"
            );
        }

        this.userName = userName;
        this.option = option;
        this.quantity = quantity;
        this.purchaseCost = pricePaid;
        this.commissionPaid = commissionPaid;
    }

    public String getUserName() {
        return userName;
    }

    public Option getOption() {
        return option;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getPurchaseCost() {
        return purchaseCost;
    }

    public double getCommissionPaid() {
        return commissionPaid;
    }
}