package guessmarket.engine.domain;

import guessmarket.dto.*;
import guessmarket.engine.market.*;

import java.io.*;
import java.util.*;


public class GuessMarketEngine implements Engine, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final double INITIAL_CASH_ON_LOGIN = 1000.0;
    private Map<Integer, Event> eventsById = new LinkedHashMap<>();
    private Map<String, User> usersByName = new LinkedHashMap<>();
    private Map<Integer, String> marketMakerByEventId = new LinkedHashMap<>();
    private boolean isLoaded = false;

    @Override
    public void saveState(String filePath) {
        String actualPath = getSaveFilePath(filePath);

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(actualPath))) {

            out.writeObject(this);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to save system state: " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public void loadState(String filePath) {
        filePath = getSaveFilePath(filePath);

        File saveFile = new File(filePath);

        if (!saveFile.exists()) {
            throw new IllegalArgumentException(
                    "Save file does not exist: " + filePath
            );
        }

        try (ObjectInputStream in =
                     new ObjectInputStream(new FileInputStream(filePath))) {

            GuessMarketEngine loadedEngine =
                    (GuessMarketEngine) in.readObject();

            eventsById.clear();
            eventsById.putAll(loadedEngine.eventsById);
            usersByName.clear();
            usersByName.putAll(loadedEngine.usersByName);
            marketMakerByEventId.clear();
            marketMakerByEventId.putAll(loadedEngine.marketMakerByEventId);
            isLoaded = true;

        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Failed to load system state: " + e.getMessage(),
                    e
            );
        }
    }

    public boolean isEngineLoaded(){
        return isLoaded;
    }

    @Override
    public void loadMarketFromXml(String xmlFilePath) {
        throw new UnsupportedOperationException(
                "Exercise 3 loads events through XmlParser and addEvent, not XMLLoader."
        );
    }

    @Override
    public List<EventDTO> getEventSummaries() {
        List<EventDTO> eventSummaries = new ArrayList<>();
        for (Event e:eventsById.values()) {

            List<String> optionNames = convertOptionsToStrings(e.getOptions());
            String mmName = marketMakerByEventId.get(e.getId());

            EventDTO eventDTO = new EventDTO(
                    e.getId(),
                    e.getName(),
                    e.getDescription(),
                    e.getCommissionPercentage(),
                    e.getCommissionMethod().name(),
                    e.getTradingMethodName(),
                    optionNames,
                    e.getState().name(),
                    mmName
            );
            eventSummaries.add(eventDTO);
        }

        return eventSummaries;
    }

    @Override
    public EventStateDTO getEventState(int eventId) {
        Event requestedEvent = eventsById.get(eventId);

        validateEvent(eventId, requestedEvent);

        List<OptionStateDTO> optionStateDTOs = createOptionStateDtoList(requestedEvent);
        List<TradeDTO> tradeDTOs = createTradeDTOList(requestedEvent);

        return new EventStateDTO(
                requestedEvent.getId(),
                requestedEvent.getName(),
                requestedEvent.getAccountBalance(),
                requestedEvent.getTotalCommissionCollected(),
                optionStateDTOs,
                tradeDTOs,
                requestedEvent.getState().name(),
                requestedEvent
                        .getWinningOption()
                        .map(Option::getName)
                        .orElse(null)
        );
    }

    private static List<TradeDTO> createTradeDTOList(Event requestedEvent) {
        List<TradeDTO> tradeDTOs = new ArrayList<>();

        for (Trade trade : requestedEvent.getTradeHistory().reversed()) {
            TradeDTO tradeDTO = new TradeDTO(
                    trade.getOption().getName(),
                    trade.getQuantity(),
                    trade.getPurchaseCost()
            );

            tradeDTOs.add(tradeDTO);
        }
        return tradeDTOs;
    }

    private List<OptionStateDTO> createOptionStateDtoList(Event requestedEvent) {
        List<OptionStateDTO> optionStateDTOs = new ArrayList<>();

        for (Option option : requestedEvent.getOptions()) {
            OptionStateDTO optionStateDTO = new OptionStateDTO(
                    option.getName(),
                    requestedEvent.getOptionPrice(option),
                    requestedEvent.getQuantityBought(option)
            );

            optionStateDTOs.add(optionStateDTO);
        }

        return optionStateDTOs;
    }

    private void validateEvent(int eventId, Event requestedEvent) {
        if (requestedEvent == null) {
            throw new IllegalArgumentException("No event exists with id: " + eventId);
        }
    }

    private void validateMarketMaker(Event event, String userName) {
        String assignedMm = marketMakerByEventId.get(event.getId());

        if (assignedMm == null || !assignedMm.equals(userName)) {
            throw new IllegalArgumentException(
                    "User '" + userName + "' is not the market maker for event '"
                            + event.getName() + "'. The assigned market maker is: "
                            + (assignedMm != null ? assignedMm : "none")
            );
        }
    }

    private User requireExistingUser(String userName) {
        User user = usersByName.get(userName);
        if (user == null) {
            throw new IllegalArgumentException("No user exists with name: " + userName);
        }
        return user;
    }

    private void validateUserNotBlocked(User user) {
        if (user.isBlocked()) {
            throw new IllegalStateException(
                    "User '" + user.getName()
                            + "' has a negative balance and is blocked from performing any actions."
            );
        }
    }

    private String getSaveFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException(
                    "Save file path cannot be empty."
            );
        }

        return filePath + ".sav";
    }

    public synchronized void topUp(String userName, double amount) {
        User user = requireExistingUser(userName);
        if (!Double.isFinite(amount) || amount <= 0.0) {
            throw new IllegalArgumentException("Topup amount must be greater than zero.");
        }
        user.receive(amount);
    }

    @Override
    public PurchaseResultDTO purchaseShares(int eventId, int optionChoice, int quantity, String userName) {
        Event requestedEvent = eventsById.get(eventId);
        validateEvent(eventId, requestedEvent);

        User user = requireExistingUser(userName);
        validateUserNotBlocked(user);

        Option option = requestedEvent.getOptionByChoice(optionChoice);

        double previewCost = requestedEvent.getTradingMethod().calculatePurchaseCost(option, quantity);
        double previewCommission = 0.0;
        if (requestedEvent.getCommissionMethod() == CommissionMethod.ON_PURCHASE) {
            previewCommission = previewCost * requestedEvent.getCommissionPercentage() / 100.0;
        }
        double previewTotal = previewCost + previewCommission;
        if (user.getBalance() < previewTotal) {
            throw new IllegalStateException(
                    "User '" + userName + "' does not have enough balance. Required: " + previewTotal + ", Available: " + user.getBalance());
        }

        Trade trade = requestedEvent.purchase(option, quantity, userName);

        double totalPricePaid = trade.getPurchaseCost() + trade.getCommissionPaid();
        user.pay(totalPricePaid);

        return new PurchaseResultDTO(
                requestedEvent.getId(),
                requestedEvent.getName(),
                totalPricePaid,
                trade.getPurchaseCost(),
                trade.getCommissionPaid(),
                requestedEvent.getState().name()
        );
    }

    @Override
    public void openEvent(int eventId, String userName) {
        Event requestedEvent = eventsById.get(eventId);
        validateEvent(eventId, requestedEvent);
        validateMarketMaker(requestedEvent, userName);

        User mm = requireExistingUser(userName);
        validateUserNotBlocked(mm);

        TradingMethod tradingMethod = requestedEvent.getTradingMethod();

        double paymentAmount;

        if (tradingMethod instanceof OrderBook orderBook) {
            int quantityPerOption = orderBook.getInitial() / orderBook.getD();
            paymentAmount = quantityPerOption * (double) orderBook.getD();
        } else {
            paymentAmount = requestedEvent.getRequiredSubsidy();
        }

        if (mm.getBalance() < paymentAmount) {
            throw new IllegalStateException(
                    "Market maker '" + userName + "' does not have enough balance to open event '"
                            + requestedEvent.getName() + "'. Required: " + paymentAmount
                            + ", Available: " + mm.getBalance());
        }

        if (tradingMethod instanceof OrderBook orderBook) {
            orderBook.openInitialPosition(userName);
        }

        mm.pay(paymentAmount);
        requestedEvent.activate(paymentAmount);
    }

    @Override
    public EventStateDTO closeEvent(int eventId, int winningOptionChoice, String userName) {
        Event requestedEvent = eventsById.get(eventId);
        validateEvent(eventId, requestedEvent);
        validateMarketMaker(requestedEvent, userName);

        User mm = requireExistingUser(userName);
        validateUserNotBlocked(mm);

        Option winningOption = requestedEvent.getOptionByChoice(winningOptionChoice);
        CloseSettlement settlement = requestedEvent.close(winningOption);

        for (Map.Entry<String, Double> entry : settlement.payoutsByUser().entrySet()) {
            User winner = usersByName.get(entry.getKey());
            if (winner != null && entry.getValue() > 0.0) {
                winner.receive(entry.getValue());
            }
        }

        if (settlement.mmAmount() > 0.0) {
            mm.receive(settlement.mmAmount());
        }

        return getEventState(eventId);
    }

    private Map<Integer, Event> createEvents(List<EventXmlData> eventXmlData){

        Map<Integer, Event> events = new LinkedHashMap<>();

        for (EventXmlData e:eventXmlData) {

            int id = e.id();
            int commissionPercentage = e.commission();
            CommissionMethod commissionMethod = createCommissionMethod(e.commissionMethod());
            List<Option> options = createOptions(e.options());
            TradingMethod tradingMethod = createTradingMethod(e.tradingMethod(),options);
            Account account = new Account(0.0);

            Event event = new Event(id,e.name(),e.description(),commissionPercentage,commissionMethod,tradingMethod,account);
            events.put(event.getId(),event);

        }

        return events;
    }

    private Map<String, User> createUsers(List<UserXmlData> usersXmlData) {
        Map<String, User> users = new LinkedHashMap<>();

        for (UserXmlData u : usersXmlData) {
            Account account = new Account(u.initialCash());
            users.put(u.name(), new User(u.name(), account));
        }

        return users;
    }

    private Map<Integer, String> createMmMapping(List<UserXmlData> usersXmlData) {
        Map<Integer, String> mapping = new LinkedHashMap<>();

        for (UserXmlData u : usersXmlData) {
            for (int eventId : u.marketMakerEventIds()) {
                mapping.put(eventId, u.name());
            }
        }

        return mapping;
    }

    private TradingMethod createTradingMethod(TradingMethodXmlData tradingMethodXmlData, List<Option> options) {
        return switch (tradingMethodXmlData) {
            case LmsrXmlData(int liquidityParameter) ->
                    new LMSR(liquidityParameter, options);

            case OrderBookXmlData(boolean allowMint, int initial, int d) ->
                    new OrderBook(allowMint, initial, d, options);

            default -> throw new IllegalArgumentException(
                    "Unsupported trading method XML data: "
                            + tradingMethodXmlData.getClass().getSimpleName()
            );
        };
    }

    private List<Option> createOptions(List<String> optionNames) {
        List<Option> options = new ArrayList<>();

        for (String optionName : optionNames) {
            options.add(new Option(optionName));
        }

        return options;
    }

    private List<String> convertOptionsToStrings(List<Option> options) {
        List<String> optionNames = new ArrayList<>();

        for (Option option : options) {
            optionNames.add(option.getName());
        }

        return optionNames;
    }

    private CommissionMethod createCommissionMethod(String commissionMethod) {
        if ("on-purchase".equals(commissionMethod)) {
            return CommissionMethod.ON_PURCHASE;
        }

        if ("on-close".equals(commissionMethod)) {
            return CommissionMethod.ON_CLOSE;
        }

        throw new IllegalArgumentException(
                "Unsupported commission method: " + commissionMethod
        );
    }

    @Override
    public List<UserDTO> getUserSummaries() {
        List<UserDTO> summaries = new ArrayList<>();

        for (User user : usersByName.values()) {
            boolean isMm = marketMakerByEventId.containsValue(user.getName());

            summaries.add(new UserDTO(
                    user.getName(),
                    user.getBalance(),
                    isMm
            ));
        }

        return summaries;
    }

    @Override
    public UserStateDTO getUserState(String userName) {
        User user = requireExistingUser(userName);

        List<UserEventInvolvementDTO> involvements = new ArrayList<>();

        for (Event event : eventsById.values()) {
            if (event.getTradingMethod() instanceof OrderBook orderBook) {
                UserEventInvolvementDTO involvement =
                        buildOrderBookInvolvement(event, orderBook, userName);

                if (involvement != null) {
                    involvements.add(involvement);
                }
            } else {
                UserEventInvolvementDTO involvement =
                        buildLmsrInvolvement(event, userName);

                if (involvement != null) {
                    involvements.add(involvement);
                }
            }
        }

        return new UserStateDTO(
                user.getName(),
                user.getBalance(),
                involvements
        );
    }

    private UserEventInvolvementDTO buildLmsrInvolvement(Event event, String userName) {
        List<TradeDTO> userTrades = new ArrayList<>();
        double userCommissionPaid = 0.0;

        for (Trade trade : event.getTradeHistory()) {
            if (trade.getUserName().equals(userName)) {
                userTrades.add(new TradeDTO(
                        trade.getOption().getName(),
                        trade.getQuantity(),
                        trade.getPurchaseCost()
                ));
                userCommissionPaid += trade.getCommissionPaid();
            }
        }

        if (userTrades.isEmpty()) {
            return null;
        }

        return new UserEventInvolvementDTO(
                event.getId(),
                event.getName(),
                "LMSR",
                userCommissionPaid,
                userTrades.reversed(),
                List.of(),
                List.of(),
                0.0,
                null,
                event.getState().name(),
                event.getWinningOption().map(Option::getName).orElse(null)
        );
    }

    private UserEventInvolvementDTO buildOrderBookInvolvement(Event event, OrderBook orderBook, String userName) {
        List<OptionHoldingDTO> holdings = new ArrayList<>();

        for (Option option : event.getOptions()) {
            Map<String, Integer> holders = orderBook.getHoldingsForOption(option);
            Integer quantity = holders.get(userName);

            if (quantity != null && quantity > 0) {
                double amountPaid = orderBook.getSpentByUser(userName, option);
                holdings.add(new OptionHoldingDTO(option.getName(), quantity, amountPaid));
            }
        }

        double commissionPaid = orderBook.getCommissionPaidByUser(userName);
        double reservedCash = orderBook.getReservedCash(userName);

        List<RestingOrderDTO> restingOrders = getRestingOrdersForUser(event, orderBook, userName);

        if (holdings.isEmpty() && commissionPaid == 0.0 && restingOrders.isEmpty() && reservedCash == 0.0) {
            return null;
        }

        Double profitOrLoss = null;

        if (event.getState() == EventState.CLOSED) {
            Optional<Option> winningOption = event.getWinningOption();
            double totalReceived = 0.0;
            double totalSpent = 0.0;

            for (OptionHoldingDTO holding : holdings) {
                totalSpent += holding.amountPaid();

                if (winningOption.isPresent()
                        && winningOption.get().getName().equals(holding.optionName())) {
                    totalReceived += holding.quantity() * orderBook.getD();
                }
            }

            profitOrLoss = totalReceived - totalSpent - commissionPaid;
        }

        return new UserEventInvolvementDTO(
                event.getId(),
                event.getName(),
                "ORDER_BOOK",
                commissionPaid,
                List.of(),
                holdings,
                restingOrders,
                reservedCash,
                profitOrLoss,
                event.getState().name(),
                event.getWinningOption().map(Option::getName).orElse(null)
        );
    }

    private List<RestingOrderDTO> getRestingOrdersForUser(Event event, OrderBook orderBook, String userName) {
        OrderBookStateDTO state = orderBook.toStateDTO(event);
        List<RestingOrderDTO> result = new ArrayList<>();

        for (OrderBookLevelDTO level : state.buyLevels()) {
            if (level.userName().equals(userName)) {
                result.add(new RestingOrderDTO(
                        level.optionName(),
                        "BUY",
                        level.quantity(),
                        level.pricePerShare()
                ));
            }
        }

        for (OrderBookLevelDTO level : state.sellLevels()) {
            if (level.userName().equals(userName)) {
                result.add(new RestingOrderDTO(
                        level.optionName(),
                        "SELL",
                        level.quantity(),
                        level.pricePerShare()
                ));
            }
        }

        return result;
    }

    @Override
    public OrderPlacementResultDTO placeOrder(
            int eventId,
            String userName,
            int optionIndex,
            OrderSide side,
            int quantity,
            double pricePerShare
    ) {
        Event requestedEvent = eventsById.get(eventId);
        validateEvent(eventId, requestedEvent);

        User user = requireExistingUser(userName);
        validateUserNotBlocked(user);

        String marketMakerName = marketMakerByEventId.get(eventId);
        if (marketMakerName == null) {
            throw new IllegalStateException("No market maker assigned for event: " + requestedEvent.getName());
        }

        User marketMaker = requireExistingUser(marketMakerName);

        if (!(requestedEvent.getTradingMethod() instanceof OrderBook orderBook)) {
            throw new IllegalStateException("Event does not use Order Book trading");
        }

        Option option = requestedEvent.getOptionByChoice(optionIndex);

        return orderBook.placeOrder(
                requestedEvent,
                usersByName,
                user,
                marketMaker,
                option,
                side,
                quantity,
                pricePerShare
        );
    }

    @Override
    public OrderBookStateDTO getOrderBookState(int eventId) {
        Event requestedEvent = eventsById.get(eventId);
        validateEvent(eventId, requestedEvent);

        if (!(requestedEvent.getTradingMethod() instanceof OrderBook orderBook)) {
            throw new IllegalStateException("Event does not use Order Book trading");
        }

        return orderBook.toStateDTO(requestedEvent);
    }

    public synchronized void ensureUser(String userName) {
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("User name cannot be null or blank");
        }
        if (!usersByName.containsKey(userName)) {
            usersByName.put(userName, new User(userName, new Account(INITIAL_CASH_ON_LOGIN)));
        }
    }

    public synchronized boolean eventNameExists(String name) {
        if (name == null) {
            return false;
        }
        String key = name.trim().toLowerCase();
        for (Event e : eventsById.values()) {
            if (e.getName().trim().toLowerCase().equals(key)) {
                return true;
            }
        }
        return false;
    }

    public synchronized EventDTO addEvent(String name, String description, int commissionPercentage, String commissionMethodStr, List<String> optionNames, TradingMethodXmlData tradingMethodXmlData, String marketMakerName) {
        if (eventNameExists(name)) {
            throw new IllegalArgumentException("Event name already exists: '" + name + "'.");
        }
        CommissionMethod commissionMethod = createCommissionMethod(commissionMethodStr);
        List<Option> options = createOptions(optionNames);
        TradingMethod tradingMethod = createTradingMethod(tradingMethodXmlData, options);
        Account account = new Account(0.0);
        int newId = eventsById.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        Event event = new Event(newId, name, description, commissionPercentage, commissionMethod, tradingMethod, account);
        eventsById.put(newId, event);
        marketMakerByEventId.put(newId, marketMakerName);
        ensureUser(marketMakerName);
        isLoaded = true;
        return new EventDTO(event.getId(), event.getName(), event.getDescription(), event.getCommissionPercentage(), event.getCommissionMethod().name(), event.getTradingMethodName(), convertOptionsToStrings(event.getOptions()), event.getState().name(), marketMakerName);
    }
}