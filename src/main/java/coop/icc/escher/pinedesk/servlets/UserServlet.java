package coop.icc.escher.pinedesk.servlets;

import coop.icc.escher.pinedesk.*;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.json.*;

import java.sql.SQLException;
import javax.naming.NamingException;

@WebServlet(name="UserServlet", urlPatterns={ "/api/user/*" })
public class UserServlet extends HttpServlet {
    UserServlet () {
        m_bldFactory = Json.createBuilderFactory(null);
        m_wrFactory = Json.createWriterFactory(null);
    }

    @Override
    protected void doGet (HttpServletRequest request,
                          HttpServletResponse response)
                          throws ServletException {
        HttpSession session = request.getSession();
        String path = request.getPathInfo();
        Map<String,String[]> params = request.getParameterMap();
        Long currentUser = (Long)session.getAttribute("user");

        if (path == null) path = "";

        switch (path) {
        case "/me":
            if (currentUser == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            try {
                User user = User.lookup(currentUser.longValue());

                try (JsonWriter writer = m_wrFactory.createWriter(response.getWriter())) {
                    writer.writeObject(getUserInfo(user).build());
                }
            } catch (NoSuchUserException nsue) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
            } catch (IOException | SQLException | NamingException | JsonException | IllegalStateException e) {
                throw new ServletException (e);
            }
            
            break;
        case "/exists":
            if (!params.containsKey("user")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            try {
                JsonArrayBuilder respArr = m_bldFactory.createArrayBuilder();
                
                for (String email : params.get("user")) {
                    if (!user.matches(EMAIL_PATTERN)) continue;

                    respArr.add(m_bldFactory.createObjectBuilder()
                                    .add(user, userExists(email)));
                }

                try (JsonWriter writer = m_wrFactory.createWriter(response.getWriter()) {
                    writer.writeArray(respArr.build());
                }
            } catch (IOException | SQLException | NamingException | JsonException | IllegalStateException e) {
                throw new ServletException (e);
            }

            break;
        case "/matchPrefix":
            if (currentUser == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
    }

    @Override
    protected void doPost (HttpServletRequest request,
                           HttpServletResponse response)
                           throws ServletException {
    }

    JsonObjectBuilder getUserInfo (User user) {
        return m_bldFactory.createObjectBuilder()
            .add("id", user.getId())
            .add("email", user.getEmail())
            .add("firstName", user.getFirstName())
            .add("lastName", user.getLastName())
            .add("room", user.getRoom());
    }

    private boolean userExists (String email) throws SQLException, NamingException {
        User user = null;

        try {
            user = User.lookup(email);
        } catch (NoSuchUserException nsue) {
            return false;
        }

        return true;
    }

    private JsonBuilderFactory m_bldFactory;
    private JsonWriterFactory m_wrFactory;

    private static final String EMAIL_PATTERN =
        "[A-Za-z0-9!#$%&'*+-/=?^_`{|}~.]@[A-Za-z0-9.]";
}
