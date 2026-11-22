package guessmarket.server;

import guessmarket.dto.EventStateDTO;
import guessmarket.engine.domain.GuessMarketEngine;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;

@WebServlet(name = "OpenServlet", urlPatterns = {"/api/open"})
public class OpenServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        String username = session == null ? null : (String) session.getAttribute("username");

        if (username == null || !ServerState.getInstance().isLoggedIn(username)) {
            JsonUtil.writeError(resp, 401, "You must log in first.");
            return;
        }

        String idParam = req.getParameter("id");
        if (idParam == null) {
            idParam = req.getParameter("eventId");
        }
        if (idParam == null || idParam.isBlank()) {
            JsonUtil.writeError(resp, 400, "Missing event id.");
            return;
        }

        int id;
        try {
            id = Integer.parseInt(idParam.trim());
        } catch (NumberFormatException ex) {
            JsonUtil.writeError(resp, 400, "Event id must be an integer.");
            return;
        }

        GuessMarketEngine engine = ServerState.getInstance().getEngine();
        synchronized (engine) {
            try {
                engine.openEvent(id, username);
                EventStateDTO state = engine.getEventState(id);
                JsonUtil.writeJson(resp, 200, Map.of("status", "ok", "event", state));
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
}