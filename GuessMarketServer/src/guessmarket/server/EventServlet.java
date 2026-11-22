package guessmarket.server;

import guessmarket.dto.EventStateDTO;
import guessmarket.dto.OrderBookStateDTO;
import guessmarket.engine.domain.GuessMarketEngine;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet(name = "EventServlet", urlPatterns = {"/api/event"})
public class EventServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        String username = session == null ? null : (String) session.getAttribute("username");

        if (username == null || !ServerState.getInstance().isLoggedIn(username)) {
            JsonUtil.writeError(resp, 401, "You must log in first.");
            return;
        }

        String idParam = req.getParameter("id");
        if (idParam == null || idParam.isBlank()) {
            JsonUtil.writeError(resp, 400, "Missing parameters. Required: id.");
            return;
        }

        int id;
        try {
            id = Integer.parseInt(idParam.trim());
        } catch (NumberFormatException ex) {
            JsonUtil.writeError(resp, 400, "Parameter id must be an integer.");
            return;
        }

        GuessMarketEngine engine = ServerState.getInstance().getEngine();
        synchronized (engine) {
            try {
                EventStateDTO event = engine.getEventState(id);
                Map<String, Object> data = new HashMap<>();
                data.put("status", "ok");
                data.put("event", event);
                try {
                    OrderBookStateDTO orderBook = engine.getOrderBookState(id);
                    data.put("orderBook", orderBook);
                } catch (IllegalStateException ignored) {
                    // LMSR event, no order book part
                }
                JsonUtil.writeJson(resp, 200, data);
            } catch (IllegalArgumentException ex) {
                if (ex.getMessage() != null && ex.getMessage().contains("No event exists")) {
                    JsonUtil.writeError(resp, 404, ex.getMessage());
                } else {
                    JsonUtil.writeError(resp, 400, ex.getMessage());
                }
            }
        }
    }
}