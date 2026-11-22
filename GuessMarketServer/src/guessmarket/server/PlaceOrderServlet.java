package guessmarket.server;

import guessmarket.dto.OrderPlacementResultDTO;
import guessmarket.engine.domain.GuessMarketEngine;
import guessmarket.engine.market.OrderSide;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;

@WebServlet(name = "PlaceOrderServlet", urlPatterns = {"/api/placeOrder"})
public class PlaceOrderServlet extends HttpServlet {

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
        String sideParam = req.getParameter("side");
        String quantityParam = req.getParameter("quantity");
        String priceParam = req.getParameter("price");

        if (idParam == null || idParam.isBlank()
                || optionParam == null || optionParam.isBlank()
                || sideParam == null || sideParam.isBlank()
                || quantityParam == null || quantityParam.isBlank()
                || priceParam == null || priceParam.isBlank()) {
            JsonUtil.writeError(resp, 400, "Missing parameters. Required: id, option, side, quantity, price.");
            return;
        }

        int id;
        int option;
        int quantity;
        double price;
        OrderSide side;
        try {
            id = Integer.parseInt(idParam.trim());
            option = Integer.parseInt(optionParam.trim());
            quantity = Integer.parseInt(quantityParam.trim());
            price = Double.parseDouble(priceParam.trim());
        } catch (NumberFormatException ex) {
            JsonUtil.writeError(resp, 400, "Parameters id, option, quantity must be integers and price must be a number.");
            return;
        }

        try {
            side = OrderSide.valueOf(sideParam.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            JsonUtil.writeError(resp, 400, "Side must be BUY or SELL.");
            return;
        }

        GuessMarketEngine engine = ServerState.getInstance().getEngine();
        synchronized (engine) {
            try {
                OrderPlacementResultDTO result = engine.placeOrder(id, username, option, side, quantity, price);
                JsonUtil.writeJson(resp, 200, Map.of("status", "ok", "result", result));
            } catch (IllegalArgumentException ex) {
                if (ex.getMessage() != null && ex.getMessage().contains("No event exists")) {
                    JsonUtil.writeError(resp, 404, ex.getMessage());
                } else {
                    JsonUtil.writeError(resp, 400, ex.getMessage());
                }
            } catch (IllegalStateException | UnsupportedOperationException ex) {
                JsonUtil.writeError(resp, 400, ex.getMessage());
            }
        }
    }

    private String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}