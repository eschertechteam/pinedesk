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
        Map<String,String[]> params = request.getParameterMap();
        String path = request.getPathInfo();
        JsonObjectBuilder info = m_bldFactory.createObjectBuilder();
        Long currentUser = (Long)session.getAttribute("user");

        if (path == null) path = "";

        response.setStatus(HttpServletResponse.SC_OK);

        switch (path) {
        case "/me":         //Get user information *****************************
            try {
                if (currentUser == null) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    info.add("reason", "Must be logged in to access this resource");
                    return;
                }

                User user = User.lookup(currentUser.longValue());

                try (JsonWriter writer = m_wrFactory.createWriter(response.getWriter())) {
                    setJsonResponse(response);
                    writer.writeObject(getUserInfo(user).build());
                }
            } catch (NoSuchUserException nsue) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                info.add("reason", "Invalid session -- current user does not exist");
            } catch (IOException | SQLException | NamingException | JsonException | IllegalStateException e) {
                throw new ServletException (e);
            }
            
            break;
        case "/exists":     //Check if user exists *****************************
            try {
                if (!params.containsKey("email")) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    info.add("reason", "`email` parameter missing");
                    return;
                }

                JsonArrayBuilder respArr = m_bldFactory.createArrayBuilder();
                
                for (String email : params.get("email")) {
                    if (!user.matches(EMAIL_PATTERN)) continue;

                    respArr.add(m_bldFactory.createObjectBuilder()
                                    .add(user, User.exists(email)));
                }

                try (JsonWriter writer = m_wrFactory.createWriter(response.getWriter())) {
                    setJsonResponse(response);
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
                    info.add("reason", "Must be logged in to access this resource");
                    return;
                }

                List<User> matches = User.matchPrefix(params.get("prefix")[0]);
                JsonArrayBuilder respArr = m_bldFactory.createArrayBuilder();

                for (User user : matches) respArr.add(getUserInfo(user, false));

                try (JsonWriter writer = m_wrFactory.createWriter(response.getWriter())) {
                    setJsonResponse(response);
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
                response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            } catch (IOException ioe) {
                throw new ServletException (ioe);
            }
        default:            //Unknown resources ********************************
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

        if (response.getStatus() != HttpServletResponse.SC_OK)
            sendStatusMessage(response, info);
    }

    @Override
    protected void doPost (HttpServletRequest request,
                           HttpServletResponse response)
                           throws ServletException {
        HttpSession session = request.getSession();
        Map<String,String[]> params = request.getParameterMap();
        String path = request.getPathInfo();
        JsonObjectBuilder info = m_bldFactory.createObjectBuilder();
        Long currentUser = (Long)session.getAttribute("user");

        if (path == null) path = "";

        switch (path) {
        case "/new":        //New user *****************************************
            response.setStatus(addUser(params));

            switch (response.getStatus()) {
            case HttpServletResponse.SC_CONFLICT:
                info.add("reason", "User exists");
                break;
            case HttpServletResponse.SC_BAD_REQUEST:
                info.add("reason", "Invalid room number");
                break;
            }
            break;
        case "/edit":       //Edit existing user *******************************
            if (currentUser == null) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                info.add("reason", "Must be logged in");
                break;
            }

            response.setStatus(editUser(params, currentUser.longValue()));

            switch (response.getStatus()) {
            case HttpServletResponse.SC_FORBIDDEN:
                info.add("reason", "Invalid or missing verification password");
                break;
            case HttpServletResponse.SC_BAD_REQUEST:
                info.add("reason", "Invalid room number");
                break;
            }
            break;
        case "/login":      //Log in *******************************************
            if (!(params.containsKey("email") && params.containsKey("verify"))) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                info.add("reason", "Missing email or verification password");

                if (params.containsKey("passwd"))
                    info.add("extra", "Use the `verify` parameter instead of `passwd`");
                break;
            }

            currentUser = verifyUser(params.get("email"), params.get("verify"));

            if (currentUser.longValue() == -1L) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                info.add("reason", "Incorrect email or password");
            } else {
                session.setAttribute("user", currentUser);
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }

            break;
        case "/logout":     //Log out ******************************************
            if (currentUser == null) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                info.add("reason", "Must be logged in");
                break;
            }

            session.removeAttribute("user");
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            break;
        case "/me":         //GET-only actions *********************************
        case "/exists":
        case "/matchPrefix":
            response.setHeader("Allow", "GET");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);

            break;
        default:            //Unknown resources ********************************
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

        sendStatusMessage(response, info);
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
        } catch (UserExistsException uee) {
            return HttpServletResponse.SC_CONFLICT;
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

    private static void setJsonResponse (HttpServletResponse response) {
        response.setHeader("Content-Type", "application/json; charset=utf-8");
    }

    private void sendStatusMessage (HttpServletResponse response,
                                    JsonObjectBuilder info)
                                   throws ServletException {
        try {
            JsonObject builtInfo = info.build();

            if (response.getStatus() != HttpServletResponse.SC_NO_CONTENT || builtInfo.size() == 0) {
                setJsonResponse(response);

                try (JsonWriter writer = m_wrFactory.createWriter(response.getWriter())) {
                    writer.writeObject(builtInfo);
                }
            }
            else response.getWriter().flush();   //commit empty response
        } catch (IOException | JsonException | IllegalStateException e) {
            throw new ServletException (e);
        }
    }

    private JsonBuilderFactory m_bldFactory;
    private JsonWriterFactory m_wrFactory;

    private static final String EMAIL_PATTERN =
        "[A-Za-z0-9!#$%&'*+-/=?^_`{|}~.]@[A-Za-z0-9.]";
}
