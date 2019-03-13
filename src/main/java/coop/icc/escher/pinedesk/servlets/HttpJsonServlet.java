package coop.icc.escher.pinedesk.servlets;

import javax.json.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class HttpJsonServlet extends HttpServlet {
    static {
        s_bldFactory = Json.createBuilderFactory(null);
        s_wrFactory = Json.createWriterFactory(null)
    }

    protected void writeJson (HttpServletResponse resp, JsonObject obj,
                              boolean sendEmptyJson) throws ServletException {
        if (obj == null)
            obj = s_bldFactory.createObjectBuilder().build();

        try {
            if (response.getStatus() != HttpServletResponse.SC_NO_CONTENT
                || (sendEmptyJson || obj.size() > 0)) {
                setJsonResponse(response);

                try (JsonWriter writer = s_wrFactory.createWriter(response.getWriter())) {
                    writer.writeObject(obj);
                }
            }
            
            response.getWriter().flush();
        } catch (IOException | JsonException | IllegalStateException e) {
            throw new ServletException (e);
        }
    }

    protected void writeJson (HttpServletResponse resp, JsonArray arr,
                              boolean sendEmptyJson) throws ServletException {
        if (arr == null)
            arr = s_bldFactory.createArrayBuilder().build();

        try {
            if (response.getStatus() != HttpServletResponse.SC_NO_CONTENT
                || (sendEmptyJson || arr.size() > 0)) {
                setJsonResponse(response);

                try (JsonWriter writer = s_wrFactory.createWriter(response.getWriter())) {
                    writer.writeArray(arr);
                }
            }
            
            response.getWriter().flush();
        } catch (IOException | JsonException | IllegalStateException e) {
            throw new ServletException (e);
        }
    }

    protected void writeJson (HttpServletResponse resp, JsonObject obj)
                             throws ServletException {
        writeJson(resp, obj, true);
    }

    protected void writeJson (HttpServletResponse resp, JsonArray arr)
                             throws ServletException {
        writeJson(resp, arr, true);
    }

    protected JsonObjectBuilder createObjectBuilder () {
        return s_bldFactory.createObjectBuilder();
    }

    protected JsonArrayBuilder createArrayBuilder () {
        return s_bldFactory.createArrayBuilder();
    }

    private static void setJsonResponse (HttpServletResponse response) {
        response.setHeader("Content-Type", "application/json; charset=utf-8");
    }

    private static JsonBuilderFactory s_bldFactory;
    private static JsonWriterFactory s_wrFactory;
}
