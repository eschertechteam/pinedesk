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
        case "/me":         //Get user information *****************************
            try {
                if (currentUser == null) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }

                User user = User.lookup(currentUser.longValue());

                try (JsonWriter writer = m_wrFactory.createWriter(response.getWriter())) {
                    writer.writeObject(getUserInfo(user).build());
                }
            } catch (NoSuchUserException nsue) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } catch (IOException | SQLException | NamingException | JsonException | IllegalStateException e) {
                throw new ServletException (e);
            }
            
            break;
        case "/exists":     //Check if user exists *****************************
            try {
                if (!params.containsKey("user")) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }

                JsonArrayBuilder respArr = m_bldFactory.createArrayBuilder();
                
                for (String email : params.get("user")) {
                    if (!user.matches(EMAIL_PATTERN)) continue;

                    respArr.add(m_bldFactory.createObjectBuilder()
                                    .add(user, User.exists(email)));
                }

                try (JsonWriter writer = m_wrFactory.createWriter(response.getWriter())) {
                    writer.writeArray(respArr.build());
                }
            } catch (IOException | SQLException | NamingException | JsonException | IllegalStateException e) {
                throw new ServletException (e);
            }

            break;
        case "/matchPrefix":    //Find users matching prefix *******************
            try {
                if (currentUser == null) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }

                List<User> matches = User.matchPrefix(params.get("prefix")[0]);
                JsonArrayBuilder respArr = m_bldFactory.createArrayBuilder();

                for (User user : matches) respArr.add(getUserInfo(user, false));

                try (JsonWriter writer = m_wrFactory.createWriter(response.getWriter())) {
                    writer.writeArray(respArr.build());
                }
            } catch (IOException | SQLException | NamingException | JsonException | IllegalStateException e) {
                throw new ServletException (e);
            }
        case "/new":        //POST-only actions ********************************
        case "/edit":
        case "/login":
        case "/logout":
            try {
                response.setHeader("Allow", "POST");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            } catch (IOException ioe) {
                throw new ServletException (ioe);
            }
        default:            //Unknown resources ********************************
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } catch (IOException ioe) {
                throw new ServletException (ioe);
            }
        }
    }

    @Override
    protected void doPost (HttpServletRequest request,
                           HttpServletResponse response)
                           throws ServletException {
        HttpSession session = request.getSession();
        String path = request.getPathInfo();
        Map<String,String[]> params = request.getParameterMap();
        Long currentUser = (Long)session.getAttribute("user");
        int sc = 404;

        if (path == null) path = "";

        switch (path) {
        case "/new":        //New user *****************************************
            sc = addUser(params);
            break;
        case "/edit":       //Edit existing user *******************************
            if (currentUser == null) {
                sc = HttpServletResponse.SC_FORBIDDEN;
                break;
            }

            sc = editUser(params, currentUser.longValue());
            break;
        case "/login":      //Log in *******************************************
            if (!(params.containsKey("email") && params.containsKey("verify"))) {
                sc = HttpServletResponse.SC_BAD_REQUEST;
                break;
            }

            currentUser = verifyUser(params.get("email"), params.get("verify"));

            if (currentUser.longValue() == -1L) {
                sc = HttpServletResponse.SC_FORBIDDEN;
            } else {
                session.setAttribute("user", currentUser);
                sc = HttpServletResponse.SC_NO_CONTENT;
            }

            break;
        case "/logout":     //Log out ******************************************
            if (currentUser == null) {
                sc = HttpServletResponse.SC_FORBIDDEN;
                break;
            }

            session.removeAttribute("user");
            sc = HttpServletResponse.SC_NO_CONTENT;
            break;
        case "/me":         //GET-only actions *********************************
        case "/exists":
        case "/matchPrefix":
            try {
                response.setHeader("Allow", "GET");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            } catch (IOException ioe) {
                throw new ServletException (ioe);
            }

            return;
        default:            //Unknown resources ********************************
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } catch (IOException ioe) {
                throw new ServletException (ioe);
            }
            return;
        }

        try {
            if (sc >= 300) response.sendError(sc);
            else {
                response.setStatus(sc);
                response.getWriter().flush();   //commit empty response
            }
        } catch (IOError ioe) {
            throw new ServletException (ioe);
        }
    }

    JsonObjectBuilder getUserInfo (User user, boolean full) {
        JsonObjectBuilder userInfo = m_bldFactory.createObjectBuilder()
            .add("id", user.getId())
            .add("email", user.getEmail())
            .add("firstName", user.getFirstName())
            .add("lastName", user.getLastName());

        if (full) userInfo.add("room", user.getRoom());

        return userInfo;
    }

    JsonObjectBuilder getUserInfo (User user) {
        return getUserInfo (user, true);
    }

    long verifyUser (String email, String passwd) throws ServletException {
        User user = null;

        try {
            user = User.lookup(email);

            if (!user.verifyPassword(passwd))
                user = null;
        } catch (NoSuchUserException nsue) {
            user = null;
        } catch (SQLException | NamingException e) {
            throw new ServletException (e);
        }

        return (user == null ? -1L : user.getId());
    }

    int addUser (Map<String,String[]> params) throws ServletException {
        if (!(params.containsKey("email") && params.containsKey("passwd")
              && params.containsKey("room"))
            || !Common.isValidRoom(params.get("room"))) {
            return HttpServletResponse.SC_BAD_REQUEST;
        }

        User newUser = new User ();

        try {
            newUser.setEmail(params.get("email"));
            newUser.setPassword(params.get("password"));
            newUser.setRoom(params.get("room"));

            if (params.containsKey("firstName"))
                newUser.setFirstName(params.get("firstName"));
            if (params.containsKey("lastName"))
                newUser.setLastName(params.get("lastName"));

            User.add(newUser);
        } catch (SQLException | NamingException e) {
            throw new ServletException (e);
        }

        return HttpServletResponse.SC_NO_CONTENT;
    }

    int editUser (Map<String,String[]> params, long userid) throws ServletException {
        User user = null;
        ServletException except = null;

        try {
            user = User.lookup(userid);
        } catch (SQLException | NamingException e) {
            throw new ServletException (e);
        }

        try {
            if (params.containsKey("room") && !Common.isValidRoom(params.get("room")))
                return HttpServletResponse.SC_BAD_REQUEST;

            //Setting sensitive data - require password verification
            if ((params.containsKey("email") || params.containsKey("passwd")) &&
                (!params.containsKey("verify") || !user.verifyPassword(params.get("verify"))) {
                return HttpServletResponse.SC_FORBIDDEN;
            }
            
            if (params.containsKey("firstName"))
                user.setFirstName(params.get("firstName"));

            if (params.containsKey("lastName"))
                user.setLastName(params.get("lastName"));

            if (params.containsKey("room"))
                user.setRoom(params.get("room"));

            if (params.containsKey("email"))
                user.setEmail(params.get("email"));

            if (params.containsKey("passwd"))
                user.setPassword(params.get("passwd"));

        } catch (SQLException | NamingException e) {
            throw new ServletException (e);
        }

        return HttpServletResponse.SC_NO_CONTENT;
    }

    private JsonBuilderFactory m_bldFactory;
    private JsonWriterFactory m_wrFactory;

    private static final String EMAIL_PATTERN =
        "[A-Za-z0-9!#$%&'*+-/=?^_`{|}~.]@[A-Za-z0-9.]";
}
