package guessmarket.server;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@WebServlet(name = "LoginServlet", urlPatterns = {"/api/login"})
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String username = req.getParameter("username");

        if (username == null || username.trim().isEmpty()) {
            JsonUtil.writeError(resp, 400, "Username must not be empty.");
            return;
        }

        String clean = username.trim();

        ServerState state = ServerState.getInstance();
        synchronized (state) {
            if (state.isLoggedIn(clean)) {
                JsonUtil.writeError(resp, 409, "Username '" + clean + "' is already in use.");
                return;
            }
            state.login(clean);
        }

        req.getSession(true).setAttribute("username", clean);
        JsonUtil.writeJson(resp, 200, Map.of("status", "ok", "username", clean));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        doPost(req, resp);
    }
}