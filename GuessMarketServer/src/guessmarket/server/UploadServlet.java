package guessmarket.server;

import guessmarket.dto.EventDTO;
import guessmarket.engine.domain.GuessMarketEngine;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@WebServlet(name = "UploadServlet", urlPatterns = {"/api/upload"})
@MultipartConfig
public class UploadServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        String username = session == null ? null : (String) session.getAttribute("username");

        if (username == null || !ServerState.getInstance().isLoggedIn(username)) {
            JsonUtil.writeError(resp, 401, "You must log in first.");
            return;
        }

        List<XmlParser.ParsedEvent> parsed;
        try (InputStream in = openUploadStream(req)) {
            if (in == null) {
                JsonUtil.writeError(resp, 400, "No file part named 'file' was uploaded.");
                return;
            }
            parsed = XmlParser.parse(in);
        } catch (IllegalArgumentException ex) {
            JsonUtil.writeError(resp, 400, ex.getMessage());
            return;
        }

        GuessMarketEngine engine = ServerState.getInstance().getEngine();
        List<EventDTO> added = new ArrayList<>();

        synchronized (engine) {
            for (XmlParser.ParsedEvent p : parsed) {
                if (engine.eventNameExists(p.name())) {
                    JsonUtil.writeError(resp, 400, "Event name already exists on server: '" + p.name() + "'. File was not added.");
                    return;
                }
            }
            for (XmlParser.ParsedEvent p : parsed) {
                added.add(engine.addEvent(
                        p.name(), p.description(), p.commission(), p.commissionType(),
                        p.options(), p.tradingMethod(), username));
            }
        }

        JsonUtil.writeJson(resp, 200, Map.of("status", "ok", "added", added.size(), "events", added));
    }

    private InputStream openUploadStream(HttpServletRequest req) throws IOException, ServletException {
        try {
            Part part = req.getPart("file");
            if (part != null && part.getSize() > 0) {
                return part.getInputStream();
            }
        } catch (Exception ignored) {
        }
        if (req.getContentLength() > 0 && req.getContentType() != null && !req.getContentType().startsWith("multipart")) {
            return req.getInputStream();
        }
        return null;
    }
}