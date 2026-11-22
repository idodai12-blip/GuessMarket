package guessmarket.server;

import guessmarket.dto.PurchaseResultDTO;
import guessmarket.engine.domain.GuessMarketEngine;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;

@WebServlet(name = "PurchaseServlet", urlPatterns = {"/api/purchase"})
public class PurchaseServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        String username = session == null ? null : (String) session.getAttribute("username");

        if (username == null || !ServerState.getInstance().isLoggedIn(username)) {
            JsonUtil.writeError(resp, 401, "You must log in first.");
            return;
        }

        String idParam = firstNonNull(req.getParameter("id"), req.getParameter("eventId"));
        String optionParam = firstNonNull(req.getParameter("option"), req.getParameter("optionIndex"));
        String quantityParam = req.getParameter("quantity");

        if (idParam == null || idParam.isBlank()
                || optionParam == null || optionParam.isBlank()
                || quantityParam == null || quantityParam.isBlank()) {
            JsonUtil.writeError(resp, 400, "Missing parameters. Required: id, option, quantity.");
            return;
        }

        int id;
        int option;
        int quantity;
        try {
            id = Integer.parseInt(idParam.trim());
            option = Integer.parseInt(optionParam.trim());
            quantity = Integer.parseInt(quantityParam.trim());
        } catch (NumberFormatException ex) {
            JsonUtil.writeError(resp, 400, "Parameters id, option and quantity must be integers.");
            return;
        }

        GuessMarketEngine engine = ServerState.getInstance().getEngine();
        synchronized (engine) {
            try {
                PurchaseResultDTO result = engine.purchaseShares(id, option, quantity, username);
                JsonUtil.writeJson(resp, 200, Map.of("status", "ok", "result", result));
            } catch (IllegalArgumentException ex) {
                if (ex.getMessage() != null && ex.getMessage().contains("No event exists")) {
                    JsonUtil.writeError(resp, 404, ex.getMessage());
                } else {
                    JsonUtil.writeError(resp, 400, ex.getMessage());
                }
            } catch (IllegalStateException ex) {
                JsonUtil.writeError(resp, 400, ex.getMessage());
            }
        }
    }

    private String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}