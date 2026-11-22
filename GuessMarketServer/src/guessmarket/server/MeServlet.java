package guessmarket.server;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;

@WebServlet(name = "MeServlet", urlPatterns = {"/api/me"})
public class MeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        String username = session == null ? null : (String) session.getAttribute("username");

        if (username == null || !ServerState.getInstance().isLoggedIn(username)) {
            JsonUtil.writeError(resp, 401, "You must log in first.");
            return;
        }

        try {
            JsonUtil.writeJson(resp, 200, Map.of("status", "ok",
                    "user", ServerState.getInstance().getEngine().getUserState(username)));
        } catch (IllegalArgumentException ex) {
            JsonUtil.writeError(resp, 404, ex.getMessage());
        }
    }
}