package guessmarket.engine.market;

import java.io.Serializable;

public final class Order implements Serializable {
    private final String userName;
    private final OrderSide side;
    private final Option option;
    private final int quantity;
    private final double pricePerShare;
    private int remainingQuantity;

    public Order(String userName, OrderSide side, Option option, int quantity, double pricePerShare) {
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("User name cannot be null or blank");
        }
        if (side == null) {
            throw new IllegalArgumentException("Order side cannot be null");
        }
        if (option == null) {
            throw new IllegalArgumentException("Order option cannot be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (!Double.isFinite(pricePerShare) || pricePerShare <= 0.0) {
            throw new IllegalArgumentException("Price per share must be finite and greater than zero");
        }

        this.userName = userName;
        this.side = side;
        this.option = option;
        this.quantity = quantity;
        this.pricePerShare = pricePerShare;
        this.remainingQuantity = quantity;
    }

    public String getUserName() {
        return userName;
    }

    public OrderSide getSide() {
        return side;
    }

    public Option getOption() {
        return option;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getRemainingQuantity() {
        return remainingQuantity;
    }

    public double getPricePerShare() {
        return pricePerShare;
    }

    public void consume(int amount) {
        if (amount <= 0 || amount > remainingQuantity) {
            throw new IllegalArgumentException("Invalid consume amount");
        }
        remainingQuantity -= amount;
    }

    public boolean isFilled() {
        return remainingQuantity == 0;
    }
}