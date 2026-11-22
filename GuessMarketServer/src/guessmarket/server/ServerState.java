package guessmarket.server;

import guessmarket.engine.domain.GuessMarketEngine;
import java.util.HashMap;
import java.util.Map;

public class ServerState {
    private static final ServerState INSTANCE = new ServerState();

    private final Map<String, String> loggedInUsers = new HashMap<>();
    private final GuessMarketEngine engine = new GuessMarketEngine();

    private ServerState() {
    }

    public static ServerState getInstance() {
        return INSTANCE;
    }

    public GuessMarketEngine getEngine() {
        return engine;
    }

    public synchronized boolean isLoggedIn(String username) {
        if (username == null) {
            return false;
        }
        return loggedInUsers.containsKey(username.trim().toLowerCase());
    }

    public synchronized boolean login(String username) {
        String clean = username.trim();
        String key = clean.toLowerCase();
        if (loggedInUsers.containsKey(key)) {
            return false;
        }
        loggedInUsers.put(key, clean);
        engine.ensureUser(clean);
        return true;
    }

    public synchronized void logout(String username) {
        if (username == null) {
            return;
        }
        loggedInUsers.remove(username.trim().toLowerCase());
    }

    public synchronized int loggedInCount() {
        return loggedInUsers.size();
    }
}