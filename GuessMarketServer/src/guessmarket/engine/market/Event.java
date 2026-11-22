package guessmarket.engine.market;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Event implements Serializable {
    private final int id;
    private final String name;
    private final String description;
    private final int commissionPercentage;
    private final CommissionMethod commissionMethod;
    private final List<Option> options;
    private final TradingMethod tradingMethod;
    private final Account account;
    private final List<Trade> tradeHistory;

    private EventState state;
    private Option winningOption;
    private double totalCommissionCollected;

    public Event(
            int id,
            String name,
            String description,
            int commissionPercentage,
            CommissionMethod commissionMethod,
            TradingMethod tradingMethod,
            Account account) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Event name cannot be null or blank"
            );
        }

        if (description == null) {
            throw new IllegalArgumentException(
                    "Event description cannot be null"
            );
        }

        if (commissionPercentage < 0 || commissionPercentage > 90) {
            throw new IllegalArgumentException(
                    "Commission percentage must be between 0 and 90"
            );
        }

        if (commissionMethod == null) {
            throw new IllegalArgumentException(
                    "Commission method cannot be null"
            );
        }

        if (tradingMethod == null) {
            throw new IllegalArgumentException(
                    "Trading method cannot be null"
            );
        }

        if (account == null) {
            throw new IllegalArgumentException(
                    "Event account cannot be null"
            );
        }

        List<Option> eventOptions = List.copyOf(tradingMethod.getOptions());

        if (eventOptions.size() != 2) {
            throw new IllegalArgumentException(
                    "An Ex01 event must contain exactly two options"
            );
        }

        if (eventOptions.get(0) == eventOptions.get(1)) {
            throw new IllegalArgumentException(
                    "An event must contain two distinct options"
            );
        }

        this.id = id;
        this.name = name;
        this.description = description;
        this.commissionPercentage = commissionPercentage;
        this.commissionMethod = commissionMethod;
        this.options = eventOptions;
        this.tradingMethod = tradingMethod;
        this.account = account;
        this.tradeHistory = new ArrayList<>();
        this.state = EventState.NOT_ACTIVE;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getCommissionPercentage() {
        return commissionPercentage;
    }

    public CommissionMethod getCommissionMethod() {
        return commissionMethod;
    }

    public EventState getState() {
        return state;
    }

    public double getAccountBalance() {
        return account.getBalance();
    }

    public double getTotalCommissionCollected() {
        return totalCommissionCollected;
    }

    public Optional<Option> getWinningOption() {
        return Optional.ofNullable(winningOption);
    }

    public List<Option> getOptions() {
        return List.copyOf(options);
    }

    public List<Trade> getTradeHistory() {
        return List.copyOf(tradeHistory);
    }

    public double getRequiredSubsidy() {
        return tradingMethod.calculateInitialSubsidy();
    }

    public void activate(double subsidyAmount) {
        if (state != EventState.NOT_ACTIVE) {
            throw new IllegalStateException(
                    "Event must be not-active to be opened"
            );
        }

        if (!Double.isFinite(subsidyAmount) || subsidyAmount < 0.0) {
            throw new IllegalArgumentException(
                    "Subsidy amount must be finite and non-negative"
            );
        }

        if (subsidyAmount > 0.0) {
            account.deposit(subsidyAmount);
        }

        state = EventState.ACTIVE;
    }

    public Trade purchase(Option option, int quantity, String userName) {
        validateEventIsActive();
        validateOption(option);
        validateQuantity(quantity);

        double purchaseCost = tradingMethod.calculatePurchaseCost(option, quantity);

        if (!Double.isFinite(purchaseCost) || purchaseCost <= 0.0) {
            throw new IllegalStateException(
                    "LMSR produced an invalid purchase cost"
            );
        }

        double commissionPaid = 0.0;

        if (commissionMethod == CommissionMethod.ON_PURCHASE) {
            commissionPaid = purchaseCost * commissionPercentage / 100.0;
        }

        double totalPayment = purchaseCost + commissionPaid;
        double updatedCommissionTotal =
                totalCommissionCollected + commissionPaid;

        if (!Double.isFinite(totalPayment)
                || !Double.isFinite(updatedCommissionTotal)) {
            throw new IllegalStateException(
                    "Purchase produced an invalid monetary value"
            );
        }

        Trade trade = new Trade(
                userName,
                option,
                quantity,
                purchaseCost,
                commissionPaid
        );

        account.deposit(totalPayment);
        tradingMethod.recordPurchase(option, quantity);
        tradeHistory.add(trade);
        totalCommissionCollected = updatedCommissionTotal;

        return trade;
    }

    public CloseSettlement close(Option winningOption) {
        validateEventIsActive();
        validateOption(winningOption);

        double winningQuantity = tradingMethod.getQuantityBought(winningOption);

        double payoutPerShare = (tradingMethod instanceof OrderBook orderBook)
                ? orderBook.getD()
                : 1.0;

        double grossPayout = winningQuantity * payoutPerShare;

        double commissionAmount = 0.0;

        if (commissionMethod == CommissionMethod.ON_CLOSE) {
            commissionAmount = grossPayout * commissionPercentage / 100.0;
        }

        double netPayoutTotal = grossPayout - commissionAmount;

        Map<String, Double> payoutsByUser = new LinkedHashMap<>();

        if (winningQuantity > 0.0) {
            double payoutRatio = netPayoutTotal / winningQuantity;

            if (tradingMethod instanceof OrderBook orderBook) {
                Map<String, Integer> holders = orderBook.getHoldingsForOption(winningOption);

                for (Map.Entry<String, Integer> entry : holders.entrySet()) {
                    double userPayout = entry.getValue() * payoutRatio;
                    payoutsByUser.merge(entry.getKey(), userPayout, Double::sum);
                }
            } else {
                for (Trade trade : tradeHistory) {
                    if (trade.getOption() == winningOption) {
                        double userPayout = trade.getQuantity() * payoutRatio;
                        payoutsByUser.merge(trade.getUserName(), userPayout, Double::sum);
                    }
                }
            }
        }

        double totalDistributedToWinners = 0.0;

        for (double payout : payoutsByUser.values()) {
            totalDistributedToWinners += payout;
        }

        double mmAmount = account.getBalance() - totalDistributedToWinners;

        if (!Double.isFinite(mmAmount)) {
            throw new IllegalStateException(
                    "Settlement produced an invalid monetary value"
            );
        }

        double currentBalance = account.getBalance();

        if (currentBalance > 0.0) {
            account.withdraw(currentBalance);
        }

        totalCommissionCollected += commissionAmount;
        this.winningOption = winningOption;
        state = EventState.CLOSED;

        return new CloseSettlement(payoutsByUser, mmAmount);
    }

    private void validateOption(Option option) {

        if (!options.contains(option)) {
            throw new IllegalArgumentException("Option does not belong to this event");
        }
    }

    private void validateQuantity(int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }
    }

    private void validateEventIsActive() {

        if (state != EventState.ACTIVE) {
            throw new IllegalStateException(
                    "Event must be active to perform this operation"
            );
        }
    }

    public int getQuantityBought(Option option) {
        validateOption(option);
        return tradingMethod.getQuantityBought(option);
    }

    public double getOptionPrice(Option option) {
        validateOption(option);
        return tradingMethod.getCurrentValue(option);
    }

    public Option getOptionByChoice(int optionChoice) {
        if (optionChoice < 1 || optionChoice > options.size()) {
            throw new IllegalArgumentException(
                    "Option choice must be between 1 and " + options.size()
            );
        }

        return options.get(optionChoice - 1);
    }

    public String getTradingMethodName() {
        return tradingMethod.getMethodName();
    }

    public TradingMethod getTradingMethod() {
        return tradingMethod;
    }

    void addCommissionCollected(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("Commission amount must be finite and non-negative");
        }

        totalCommissionCollected += amount;
    }

    void depositToAccount(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("Deposit amount must be finite and non-negative");
        }

        if (amount > 0.0) {
            account.deposit(amount);
        }
    }
}