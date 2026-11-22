package guessmarket.engine.market;

import java.io.Serializable;

public final class User implements Serializable {
    private final String name;
    private final Account account;
    private boolean blocked;

    public User(String name, Account account) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "User name cannot be null or blank"
            );
        }

        if (account == null) {
            throw new IllegalArgumentException(
                    "User account cannot be null"
            );
        }

        this.name = name;
        this.account = account;
        this.blocked = false;
    }

    public String getName() {
        return name;
    }

    public double getBalance() {
        return account.getBalance();
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void pay(double amount) {
        if (blocked) {
            throw new IllegalStateException(
                    "User '" + name + "' is blocked due to a negative balance and cannot perform actions"
            );
        }

        account.withdraw(amount);

        if (account.getBalance() < 0.0) {
            blocked = true;
        }
    }

    public void receive(double amount) {
        account.deposit(amount);

        if (blocked && account.getBalance() >= 0.0) {
            blocked = false;
        }
    }

    Account getAccount() {
        return account;
    }
}