package coop.icc.escher.pinedesk.servlets;

import coop.icc.escher.pinedesk.*;

import javax.json.*;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import javax.naming.NamingException;

class ServletUtils {
    static {
        s_wrFactory = Json.createWriterFactory(null);
    }
    
    static User getActiveUser (HttpSession session) throws ServletException {
        Long userId = (Long)session.getAttribute("user");
        User user = null;

        if (userId != null) {
            try {
                user = User.lookup(userId.longValue());
            } catch (SQLException | NamingException e) {
                throw new ServletException (e);
            } catch (NoSuchUserException nsue) {
                user = null;
            }
        }

        return user;
    }

    static void writeJson (HttpServletResponse resp, JsonObject obj,
                              boolean sendEmptyJson) throws ServletException {
        if (obj == null)
            obj = Common.createObjectBuilder().build();

        try {
            if (resp.getStatus() != HttpServletResponse.SC_NO_CONTENT
                || (sendEmptyJson || obj.size() > 0)) {
                setJsonResponse(resp);

                try (JsonWriter writer = s_wrFactory.createWriter(resp.getWriter())) {
                    writer.writeObject(obj);
                }
            }
            
            resp.getWriter().flush();
        } catch (IOException | JsonException | IllegalStateException e) {
            throw new ServletException (e);
        }
    }

    static void writeJson (HttpServletResponse resp, JsonArray arr,
                              boolean sendEmptyJson) throws ServletException {
        if (arr == null)
            arr = Common.createArrayBuilder().build();

        try {
            if (resp.getStatus() != HttpServletResponse.SC_NO_CONTENT
                || (sendEmptyJson || arr.size() > 0)) {
                setJsonResponse(resp);

                try (JsonWriter writer = s_wrFactory.createWriter(resp.getWriter())) {
                    writer.writeArray(arr);
                }
            }
            
            resp.getWriter().flush();
        } catch (IOException | JsonException | IllegalStateException e) {
            throw new ServletException (e);
        }
    }

    static void writeJson (HttpServletResponse resp, JsonObject obj)
                             throws ServletException {
        writeJson(resp, obj, true);
    }

    static void writeJson (HttpServletResponse resp, JsonArray arr)
                             throws ServletException {
        writeJson(resp, arr, true);
    }

    private static void setJsonResponse (HttpServletResponse resp) {
        resp.setHeader("Content-Type", "application/json; charset=utf-8");
    }
    
    private static JsonWriterFactory s_wrFactory;
}
