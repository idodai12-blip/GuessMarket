package guessmarket.engine.market;

import guessmarket.dto.OrderBookLevelDTO;
import guessmarket.dto.OrderBookParticipantDTO;
import guessmarket.dto.OrderBookStateDTO;
import guessmarket.dto.OrderPlacementResultDTO;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OrderBook implements TradingMethod, Serializable {

    private final boolean allowMint;
    private final int initial;
    private final int d;
    private final List<Option> options;

    private final Map<Option, Deque<Order>> buyOrdersByOption;
    private final Map<Option, Deque<Order>> sellOrdersByOption;
    private final Map<String, Map<Option, Integer>> holdingsByUser;
    private final Map<String, Map<Option, Double>> spentByUser;
    private final Map<String, Double> commissionPaidByUser;
    private final Map<String, Double> reservedCashByUser;
    private final Map<String, Map<Option, Integer>> reservedSharesByUser;

    private final Map<Option, Double> lastTradePriceByOption;
    private Double lastTradePrice;

    public OrderBook(boolean allowMint, int initial, int d, List<Option> options) {
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("options cannot be null or empty");
        }

        if (d <= 0) {
            throw new IllegalArgumentException("d must be greater than zero");
        }

        if (initial < 0) {
            throw new IllegalArgumentException("initial must be non-negative");
        }

        this.allowMint = allowMint;
        this.initial = initial;
        this.d = d;
        this.options = List.copyOf(options);

        this.buyOrdersByOption = new LinkedHashMap<>();
        this.sellOrdersByOption = new LinkedHashMap<>();
        this.holdingsByUser = new LinkedHashMap<>();
        this.spentByUser = new LinkedHashMap<>();
        this.commissionPaidByUser = new LinkedHashMap<>();
        this.reservedCashByUser = new LinkedHashMap<>();
        this.reservedSharesByUser = new LinkedHashMap<>();
        this.lastTradePriceByOption = new LinkedHashMap<>();

        for (Option option : this.options) {
            buyOrdersByOption.put(option, new ArrayDeque<>());
            sellOrdersByOption.put(option, new ArrayDeque<>());
        }

        this.lastTradePrice = null;
    }

    @Override
    public List<Option> getOptions() {
        return options;
    }

    @Override
    public String getMethodName() {
        return "ORDER_BOOK";
    }

    @Override
    public double calculateInitialSubsidy() {
        return initial;
    }

    @Override
    public double getCurrentValue(Option option) {
        validateOption(option);

        Double last = lastTradePriceByOption.get(option);
        if (last != null) {
            return last;
        }

        double bid = getBid(option);
        double ask = getAsk(option);

        if (bid > 0.0 && ask > 0.0) {
            return (bid + ask) / 2.0;
        }

        if (bid > 0.0) {
            return bid;
        }

        if (ask > 0.0) {
            return ask;
        }

        return d / 2.0;
    }

    @Override
    public int getQuantityBought(Option option) {
        validateOption(option);

        int total = 0;
        for (Map<Option, Integer> perOption : holdingsByUser.values()) {
            total += perOption.getOrDefault(option, 0);
        }
        return total;
    }

    @Override
    public double calculatePurchaseCost(Option option, int quantity) {
        throw new UnsupportedOperationException(
                "Order Book does not use calculatePurchaseCost; use placeOrder instead"
        );
    }

    @Override
    public void recordPurchase(Option option, int quantity) {
        throw new UnsupportedOperationException(
                "Order Book does not use recordPurchase; use placeOrder instead"
        );
    }

    public double openInitialPosition(String marketMakerName) {
        validateUserName(marketMakerName);

        int quantityPerOption = initial / d;

        if (quantityPerOption <= 0) {
            return 0.0;
        }

        double costPerOption = (quantityPerOption * (double) d) / 2.0;

        for (Option option : options) {
            addHolding(marketMakerName, option, quantityPerOption);
            addSpent(marketMakerName, option, costPerOption);
        }

        return quantityPerOption * (double) d;
    }

    public OrderPlacementResultDTO placeOrder(
            Event event,
            Map<String, User> usersByName,
            User actingUser,
            User marketMaker,
            Option option,
            OrderSide side,
            int quantity,
            double pricePerShare
    ) {
        validateEvent(event);
        validateUsersMap(usersByName);
        validateUser(actingUser);
        validateUser(marketMaker);
        validateOption(option);
        validateSide(side);
        validateQuantity(quantity);
        validatePrice(pricePerShare);

        if (event.getState() != EventState.ACTIVE) {
            throw new IllegalStateException("Event must be active to place an order");
        }

        if (side == OrderSide.SELL) {
            int available = getHoldingQuantity(actingUser.getName(), option)
                    - getReservedShares(actingUser.getName(), option);

            if (available < quantity) {
                throw new IllegalStateException(
                        "User does not have enough available (unreserved) holdings for this sell order"
                );
            }
        }

        Order incoming = new Order(actingUser.getName(), side, option, quantity, pricePerShare);
        int requestedQuantity = quantity;
        int filledQuantity = 0;
        double totalCashFlow = 0.0;

        Deque<Order> oppositeBook = (side == OrderSide.BUY)
                ? sellOrdersByOption.get(option)
                : buyOrdersByOption.get(option);

        while (incoming.getRemainingQuantity() > 0 && !oppositeBook.isEmpty()) {
            Order resting = oppositeBook.peekFirst();

            boolean crosses = (side == OrderSide.BUY)
                    ? resting.getPricePerShare() <= incoming.getPricePerShare()
                    : resting.getPricePerShare() >= incoming.getPricePerShare();

            if (!crosses) {
                break;
            }

            int tradeQuantity = Math.min(
                    incoming.getRemainingQuantity(),
                    resting.getRemainingQuantity()
            );

            double tradePricePerShare = resting.getPricePerShare();
            double tradeValue = tradeQuantity * tradePricePerShare;

            boolean buyerIsRestingOrder = (side == OrderSide.SELL);
            boolean sellerIsRestingOrder = (side == OrderSide.BUY);

            User buyer = buyerIsRestingOrder
                    ? requireUser(usersByName, resting.getUserName())
                    : actingUser;

            User seller = sellerIsRestingOrder
                    ? requireUser(usersByName, resting.getUserName())
                    : actingUser;

            double commission = 0.0;
            if (event.getCommissionMethod() == CommissionMethod.ON_PURCHASE) {
                commission = tradeValue * event.getCommissionPercentage() / 100.0;
            }

            if (buyerIsRestingOrder) {
                releaseCash(buyer.getName(), tradeValue + commission);
            } else {
                if (buyer.getBalance() < tradeValue + commission) {
                    throw new IllegalStateException(
                            "Buyer does not have enough balance for this trade"
                    );
                }
                buyer.pay(tradeValue + commission);
            }

            int sellerQuantityBeforeTrade = getHoldingQuantity(seller.getName(), option);

            seller.receive(tradeValue);
            removeHolding(seller.getName(), option, tradeQuantity);

            if (sellerIsRestingOrder) {
                releaseShares(seller.getName(), option, tradeQuantity);
            }

            reduceSpentOnSell(seller.getName(), option, tradeQuantity, sellerQuantityBeforeTrade);

            if (commission > 0.0) {
                marketMaker.receive(commission);
                event.addCommissionCollected(commission);
                addCommissionPaid(buyer.getName(), commission);
            }

            addHolding(buyer.getName(), option, tradeQuantity);
            addSpent(buyer.getName(), option, tradeValue);

            totalCashFlow += tradeValue + commission;
            filledQuantity += tradeQuantity;

            incoming.consume(tradeQuantity);
            resting.consume(tradeQuantity);

            lastTradePrice = tradePricePerShare;
            lastTradePriceByOption.put(option, tradePricePerShare);

            if (resting.isFilled()) {
                oppositeBook.pollFirst();
            }
        }

        if (incoming.getRemainingQuantity() > 0 && side == OrderSide.BUY && allowMint) {
            Option otherOption = getOtherOption(option);
            Deque<Order> otherBuyBook = buyOrdersByOption.get(otherOption);

            while (incoming.getRemainingQuantity() > 0 && !otherBuyBook.isEmpty()) {
                Order restingOtherBuy = otherBuyBook.peekFirst();

                double combinedPrice = restingOtherBuy.getPricePerShare() + incoming.getPricePerShare();

                if (combinedPrice < d) {
                    break;
                }

                int mintQuantity = Math.min(
                        incoming.getRemainingQuantity(),
                        restingOtherBuy.getRemainingQuantity()
                );

                double restingPrice = restingOtherBuy.getPricePerShare();
                double incomingPrice = d - restingPrice;

                User incomingBuyer = actingUser;
                User restingBuyer = requireUser(usersByName, restingOtherBuy.getUserName());

                double incomingCost = mintQuantity * incomingPrice;
                double restingCost = mintQuantity * restingPrice;

                if (incomingBuyer.getBalance() < incomingCost) {
                    throw new IllegalStateException(
                            "User does not have enough balance to mint shares"
                    );
                }

                incomingBuyer.pay(incomingCost);
                releaseCash(restingBuyer.getName(), restingCost);

                event.depositToAccount(incomingCost + restingCost);

                addHolding(incomingBuyer.getName(), option, mintQuantity);
                addSpent(incomingBuyer.getName(), option, incomingCost);

                addHolding(restingBuyer.getName(), otherOption, mintQuantity);
                addSpent(restingBuyer.getName(), otherOption, restingCost);

                incoming.consume(mintQuantity);
                restingOtherBuy.consume(mintQuantity);

                totalCashFlow += incomingCost;
                filledQuantity += mintQuantity;

                if (restingOtherBuy.isFilled()) {
                    otherBuyBook.pollFirst();
                }
            }
        }

        if (incoming.getRemainingQuantity() > 0) {
            int remainingQuantity = incoming.getRemainingQuantity();

            if (side == OrderSide.BUY) {
                double reserveAmount = remainingQuantity * pricePerShare;

                if (event.getCommissionMethod() == CommissionMethod.ON_PURCHASE) {
                    reserveAmount += reserveAmount * event.getCommissionPercentage() / 100.0;
                }

                if (actingUser.getBalance() < reserveAmount) {
                    throw new IllegalStateException(
                            "User does not have enough balance to reserve funds for the remaining "
                                    + remainingQuantity + " share(s) at this price"
                    );
                }

                actingUser.pay(reserveAmount);
                reserveCash(actingUser.getName(), reserveAmount);
                insertBuyOrder(incoming);
            } else {
                reserveShares(actingUser.getName(), option, remainingQuantity);
                insertSellOrder(incoming);
            }
        }

        return new OrderPlacementResultDTO(
                event.getId(),
                event.getName(),
                actingUser.getName(),
                option.getName(),
                side.name(),
                requestedQuantity,
                filledQuantity,
                incoming.getRemainingQuantity(),
                pricePerShare,
                totalCashFlow,
                event.getState().name()
        );
    }

    public OrderBookStateDTO toStateDTO(Event event) {
        validateEvent(event);

        List<OrderBookLevelDTO> buyLevels = new ArrayList<>();
        List<OrderBookLevelDTO> sellLevels = new ArrayList<>();
        List<OrderBookParticipantDTO> participants = new ArrayList<>();

        for (Option option : options) {
            for (Order order : buyOrdersByOption.get(option)) {
                buyLevels.add(new OrderBookLevelDTO(
                        option.getName(),
                        order.getUserName(),
                        "BUY",
                        order.getRemainingQuantity(),
                        order.getPricePerShare()
                ));
            }

            for (Order order : sellOrdersByOption.get(option)) {
                sellLevels.add(new OrderBookLevelDTO(
                        option.getName(),
                        order.getUserName(),
                        "SELL",
                        order.getRemainingQuantity(),
                        order.getPricePerShare()
                ));
            }
        }

        for (Map.Entry<String, Map<Option, Integer>> userEntry : holdingsByUser.entrySet()) {
            String userName = userEntry.getKey();

            for (Map.Entry<Option, Integer> holdingEntry : userEntry.getValue().entrySet()) {
                Option option = holdingEntry.getKey();
                int quantity = holdingEntry.getValue();

                if (quantity > 0) {
                    participants.add(new OrderBookParticipantDTO(
                            userName,
                            option.getName(),
                            quantity,
                            quantity * getCurrentValue(option)
                    ));
                }
            }
        }

        double bestBid = 0.0;
        double bestAsk = Double.POSITIVE_INFINITY;

        for (Option option : options) {
            bestBid = Math.max(bestBid, getBid(option));

            double ask = getAsk(option);
            if (ask > 0.0) {
                bestAsk = Math.min(bestAsk, ask);
            }
        }

        if (bestAsk == Double.POSITIVE_INFINITY) {
            bestAsk = 0.0;
        }

        double mid;
        if (bestBid > 0.0 && bestAsk > 0.0) {
            mid = (bestBid + bestAsk) / 2.0;
        } else if (bestBid > 0.0) {
            mid = bestBid;
        } else if (bestAsk > 0.0) {
            mid = bestAsk;
        } else {
            mid = d / 2.0;
        }

        double spread = (bestBid > 0.0 && bestAsk > 0.0) ? (bestAsk - bestBid) : 0.0;

        return new OrderBookStateDTO(
                event.getId(),
                event.getName(),
                event.getState().name(),
                getMethodName(),
                lastTradePrice != null ? lastTradePrice : 0.0,
                bestBid,
                bestAsk,
                mid,
                spread,
                buyLevels,
                sellLevels,
                participants
        );
    }

    public Map<String, Integer> getHoldingsForOption(Option option) {
        validateOption(option);

        Map<String, Integer> snapshot = new LinkedHashMap<>();

        for (Map.Entry<String, Map<Option, Integer>> entry : holdingsByUser.entrySet()) {
            int quantity = entry.getValue().getOrDefault(option, 0);
            if (quantity > 0) {
                snapshot.put(entry.getKey(), quantity);
            }
        }

        return snapshot;
    }

    public double getSpentByUser(String userName, Option option) {
        validateUserName(userName);
        validateOption(option);

        Map<Option, Double> perOption = spentByUser.get(userName);
        if (perOption == null) {
            return 0.0;
        }

        return perOption.getOrDefault(option, 0.0);
    }

    public double getCommissionPaidByUser(String userName) {
        validateUserName(userName);
        return commissionPaidByUser.getOrDefault(userName, 0.0);
    }

    public double getReservedCash(String userName) {
        validateUserName(userName);
        return reservedCashByUser.getOrDefault(userName, 0.0);
    }

    public int getReservedShares(String userName, Option option) {
        validateUserName(userName);
        validateOption(option);

        Map<Option, Integer> perOption = reservedSharesByUser.get(userName);
        if (perOption == null) {
            return 0;
        }

        return perOption.getOrDefault(option, 0);
    }

    public boolean isAllowMint() {
        return allowMint;
    }

    public int getInitial() {
        return initial;
    }

    public int getD() {
        return d;
    }

    private Option getOtherOption(Option option) {
        validateOption(option);

        for (Option candidate : options) {
            if (!candidate.equals(option)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Order book requires exactly two options");
    }

    private void insertBuyOrder(Order order) {
        validateOrder(order);
        if (order.getSide() != OrderSide.BUY) {
            throw new IllegalArgumentException("Expected BUY order");
        }

        Deque<Order> book = buyOrdersByOption.get(order.getOption());
        List<Order> sorted = new ArrayList<>(book);

        int index = 0;
        while (index < sorted.size()
                && sorted.get(index).getPricePerShare() >= order.getPricePerShare()) {
            index++;
        }

        sorted.add(index, order);
        book.clear();
        book.addAll(sorted);
    }

    private void insertSellOrder(Order order) {
        validateOrder(order);
        if (order.getSide() != OrderSide.SELL) {
            throw new IllegalArgumentException("Expected SELL order");
        }

        Deque<Order> book = sellOrdersByOption.get(order.getOption());
        List<Order> sorted = new ArrayList<>(book);

        int index = 0;
        while (index < sorted.size()
                && sorted.get(index).getPricePerShare() <= order.getPricePerShare()) {
            index++;
        }

        sorted.add(index, order);
        book.clear();
        book.addAll(sorted);
    }

    private User requireUser(Map<String, User> usersByName, String userName) {
        User user = usersByName.get(userName);
        if (user == null) {
            throw new IllegalStateException("No user exists with name: " + userName);
        }
        return user;
    }

    private void addHolding(String userName, Option option, int quantity) {
        holdingsByUser.putIfAbsent(userName, new LinkedHashMap<>());
        Map<Option, Integer> perOption = holdingsByUser.get(userName);
        perOption.put(option, perOption.getOrDefault(option, 0) + quantity);
    }

    private void removeHolding(String userName, Option option, int quantity) {
        Map<Option, Integer> perOption = holdingsByUser.get(userName);
        if (perOption == null) {
            throw new IllegalStateException("User has no holdings: " + userName);
        }

        int current = perOption.getOrDefault(option, 0);
        if (current < quantity) {
            throw new IllegalStateException("Insufficient holdings for user: " + userName);
        }

        int updated = current - quantity;
        if (updated == 0) {
            perOption.remove(option);
        } else {
            perOption.put(option, updated);
        }

        if (perOption.isEmpty()) {
            holdingsByUser.remove(userName);
        }
    }

    private int getHoldingQuantity(String userName, Option option) {
        Map<Option, Integer> perOption = holdingsByUser.get(userName);
        if (perOption == null) {
            return 0;
        }

        return perOption.getOrDefault(option, 0);
    }

    private void addSpent(String userName, Option option, double amount) {
        if (amount <= 0.0) {
            return;
        }

        spentByUser.putIfAbsent(userName, new LinkedHashMap<>());
        Map<Option, Double> perOption = spentByUser.get(userName);
        perOption.put(option, perOption.getOrDefault(option, 0.0) + amount);
    }

    private void reduceSpentOnSell(String userName, Option option, int quantitySold, int quantityBeforeRemoval) {
        if (quantityBeforeRemoval <= 0) {
            return;
        }

        Map<Option, Double> perOption = spentByUser.get(userName);
        if (perOption == null) {
            return;
        }

        double currentSpent = perOption.getOrDefault(option, 0.0);
        double ratio = quantitySold / (double) quantityBeforeRemoval;
        double reduceAmount = currentSpent * ratio;
        double updated = currentSpent - reduceAmount;

        if (updated <= 0.0001) {
            perOption.remove(option);
        } else {
            perOption.put(option, updated);
        }

        if (perOption.isEmpty()) {
            spentByUser.remove(userName);
        }
    }

    private void addCommissionPaid(String userName, double amount) {
        if (amount <= 0.0) {
            return;
        }

        commissionPaidByUser.put(userName, commissionPaidByUser.getOrDefault(userName, 0.0) + amount);
    }

    private void reserveCash(String userName, double amount) {
        if (amount <= 0.0) {
            return;
        }

        reservedCashByUser.put(userName, reservedCashByUser.getOrDefault(userName, 0.0) + amount);
    }

    private void releaseCash(String userName, double amount) {
        if (amount <= 0.0) {
            return;
        }

        double current = reservedCashByUser.getOrDefault(userName, 0.0);
        double updated = current - amount;

        if (updated <= 0.0001) {
            reservedCashByUser.remove(userName);
        } else {
            reservedCashByUser.put(userName, updated);
        }
    }

    private void reserveShares(String userName, Option option, int quantity) {
        if (quantity <= 0) {
            return;
        }

        reservedSharesByUser.putIfAbsent(userName, new LinkedHashMap<>());
        Map<Option, Integer> perOption = reservedSharesByUser.get(userName);
        perOption.put(option, perOption.getOrDefault(option, 0) + quantity);
    }

    private void releaseShares(String userName, Option option, int quantity) {
        if (quantity <= 0) {
            return;
        }

        Map<Option, Integer> perOption = reservedSharesByUser.get(userName);
        if (perOption == null) {
            return;
        }

        int current = perOption.getOrDefault(option, 0);
        int updated = current - quantity;

        if (updated <= 0) {
            perOption.remove(option);
        } else {
            perOption.put(option, updated);
        }

        if (perOption.isEmpty()) {
            reservedSharesByUser.remove(userName);
        }
    }

    private double getBid(Option option) {
        validateOption(option);

        double max = 0.0;
        for (Order order : buyOrdersByOption.get(option)) {
            max = Math.max(max, order.getPricePerShare());
        }
        return max;
    }

    private double getAsk(Option option) {
        validateOption(option);

        double min = Double.POSITIVE_INFINITY;
        for (Order order : sellOrdersByOption.get(option)) {
            min = Math.min(min, order.getPricePerShare());
        }

        return min == Double.POSITIVE_INFINITY ? 0.0 : min;
    }

    private void validateEvent(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }
    }

    private void validateUsersMap(Map<String, User> usersByName) {
        if (usersByName == null) {
            throw new IllegalArgumentException("Users map cannot be null");
        }
    }

    private void validateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
    }

    private void validateSide(OrderSide side) {
        if (side == null) {
            throw new IllegalArgumentException("Order side cannot be null");
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
    }

    private void validatePrice(double pricePerShare) {
        if (!Double.isFinite(pricePerShare) || pricePerShare <= 0.0) {
            throw new IllegalArgumentException(
                    "Price per share must be finite and greater than zero"
            );
        }

        if (pricePerShare > d - 0.01) {
            throw new IllegalArgumentException(
                    "Price per share cannot be greater than d minus 0.01"
            );
        }
    }

    private void validateOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        validateOption(order.getOption());
        validateUserName(order.getUserName());
    }

    private void validateOption(Option option) {
        if (option == null || !options.contains(option)) {
            throw new IllegalArgumentException(
                    "Option does not belong to this order book"
            );
        }
    }

    private void validateUserName(String userName) {
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("User name cannot be null or blank");
        }
    }
}