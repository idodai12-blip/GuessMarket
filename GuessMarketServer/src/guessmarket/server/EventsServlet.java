package guessmarket.server;

import guessmarket.dto.EventDTO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@WebServlet(name = "EventsServlet", urlPatterns = {"/api/events"})
public class EventsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        String username = null;

        if (session != null) {
            Object attr = session.getAttribute("username");
            if (attr == null) {
                attr = session.getAttribute("user");
            }
            if (attr instanceof String) {
                username = (String) attr;
            }
        }

        if (username == null || !ServerState.getInstance().isLoggedIn(username)) {
            JsonUtil.writeError(resp, HttpServletResponse.SC_UNAUTHORIZED, "You must log in first.");
            return;
        }

        List<EventDTO> events = ServerState.getInstance().getEngine().getEventSummaries();
        JsonUtil.writeJson(resp, HttpServletResponse.SC_OK, Map.of("status", "ok", "events", events));
    }
}