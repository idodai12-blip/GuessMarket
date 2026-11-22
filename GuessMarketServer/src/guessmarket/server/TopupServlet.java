package guessmarket.server;

import guessmarket.engine.domain.GuessMarketEngine;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;

@WebServlet(name = "TopupServlet", urlPatterns = {"/api/topup"})
public class TopupServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        String username = session == null ? null : (String) session.getAttribute("username");

        if (username == null || !ServerState.getInstance().isLoggedIn(username)) {
            JsonUtil.writeError(resp, 401, "You must log in first.");
            return;
        }

        String amountParam = req.getParameter("amount");
        if (amountParam == null || amountParam.isBlank()) {
            JsonUtil.writeError(resp, 400, "Missing amount.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountParam.trim());
        } catch (NumberFormatException ex) {
            JsonUtil.writeError(resp, 400, "Amount must be a number.");
            return;
        }

        GuessMarketEngine engine = ServerState.getInstance().getEngine();
        synchronized (engine) {
            try {
                engine.topUp(username, amount);
                JsonUtil.writeJson(resp, 200, Map.of("status", "ok", "user", engine.getUserState(username)));
            } catch (IllegalArgumentException ex) {
                JsonUtil.writeError(resp, 400, ex.getMessage());
            }
        }
    }
}