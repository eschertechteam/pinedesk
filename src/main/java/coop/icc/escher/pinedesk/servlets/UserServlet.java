package coop.icc.escher.pinedesk.servlets;

import coop.icc.escher.pinedesk.*;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.json.*;

import java.util.Map;
import java.util.List;
import java.sql.SQLException;
import javax.naming.NamingException;

@WebServlet(name="UserServlet", urlPatterns={ "/api/user/*" })
public class UserServlet extends HttpServlet {
    @Override
    protected void doGet (HttpServletRequest req, HttpServletResponse resp)
                         throws ServletException {
        Map<String,String[]> params = req.getParameterMap();
        String path = req.getPathInfo();
        JsonObjectBuilder info = Common.createObjectBuilder();
        User user = ServletUtils.getActiveUser(req.getSession());

        if (path == null) path = "";

        switch (path) {
        case "/me":         //Get user information *****************************
            if (user == null) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                info.add("reason", "Must be logged in to access this resource");
                return;
            }

            ServletUtils.writeJson(resp, user.jsonify().build());
            
            break;
        case "/exists":     //Check if user exists *****************************
            try {
                if (!params.containsKey("email")) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    info.add("reason", "`email` parameter missing");
                    return;
                }

                JsonArrayBuilder respArr = Common.createArrayBuilder();
                
                for (String email : params.get("email")) {
                    if (!email.matches(EMAIL_PATTERN)) continue;

                    respArr.add(Common.createObjectBuilder()
                                    .add(email, User.exists(email)));
                }

                ServletUtils.writeJson(resp, respArr.build());
            } catch (SQLException | NamingException e) {
                throw new ServletException (e);
            }

            break;
        case "/matchPrefix":    //Find users matching prefix *******************
            try {
                if (user == null) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    info.add("reason", "Must be logged in to access this resource");
                    return;
                }

                List<User> matches = User.matchPrefix(params.get("prefix")[0]);
                JsonArrayBuilder respArr = Common.createArrayBuilder();

                for (User match : matches) respArr.add(match.jsonify(false));

                ServletUtils.writeJson(resp, respArr.build());
            } catch (SQLException | NamingException e) {
                throw new ServletException (e);
            }
            break;
        case "/new":        //POST-only actions ********************************
        case "/edit":
        case "/login":
        case "/logout":
            resp.setHeader("Allow", "POST");
            resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            break;
        default:            //Unknown resources ********************************
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

        if (!resp.isCommitted())
            ServletUtils.writeJson(resp, info.build(), false);
    }

    @Override
    protected void doPost (HttpServletRequest req, HttpServletResponse resp)
                          throws ServletException {
        HttpSession session = req.getSession();
        Map<String,String[]> params = req.getParameterMap();
        String path = req.getPathInfo();
        JsonObjectBuilder info = Common.createObjectBuilder();
        User user = ServletUtils.getActiveUser(session);

        if (path == null) path = "";

        switch (path) {
        case "/new":        //New user *****************************************
            resp.setStatus(addUser(params));

            switch (resp.getStatus()) {
            case HttpServletResponse.SC_CONFLICT:
                info.add("reason", "User exists");
                break;
            case HttpServletResponse.SC_BAD_REQUEST:
                info.add("reason", "Invalid room number");
                break;
            }
            break;
        case "/edit":       //Edit existing user *******************************
            if (user == null) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                info.add("reason", "Must be logged in");
                break;
            }

            resp.setStatus(editUser(params, user));

            switch (resp.getStatus()) {
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
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                info.add("reason", "Missing email or verification password");

                if (params.containsKey("passwd"))
                    info.add("extra", "Use the `verify` parameter instead of `passwd`");
                break;
            }

            {
                long userId = verifyUser(params.get("email")[0], params.get("verify")[0]);

                if (userId == -1L) {
                    resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    info.add("reason", "Incorrect email or password");
                } else {
                    session.setAttribute("user", userId);
                    resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }
            }

            break;
        case "/logout":     //Log out ******************************************
            if (user == null) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                info.add("reason", "Must be logged in");
                break;
            }

            session.removeAttribute("user");
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            break;
        case "/me":         //GET-only actions *********************************
        case "/exists":
        case "/matchPrefix":
            resp.setHeader("Allow", "GET");
            resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);

            break;
        default:            //Unknown resources ********************************
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

        ServletUtils.writeJson(resp, info.build(), false);
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
            || !Common.isValidRoom(params.get("room")[0])) {
            return HttpServletResponse.SC_BAD_REQUEST;
        }

        User newUser = new User ();

        try {
            newUser.setEmail(params.get("email")[0]);
            newUser.setPassword(params.get("password")[0]);
            newUser.setRoom(params.get("room")[0]);

            if (params.containsKey("firstName"))
                newUser.setFirstName(params.get("firstName")[0]);
            if (params.containsKey("lastName"))
                newUser.setLastName(params.get("lastName")[0]);

            User.add(newUser);
        } catch (SQLException | NamingException e) {
            throw new ServletException (e);
        } catch (UserExistsException uee) {
            return HttpServletResponse.SC_CONFLICT;
        }

        return HttpServletResponse.SC_NO_CONTENT;
    }

    int editUser (Map<String,String[]> params, User user) throws ServletException {
        try {
            if (params.containsKey("room") && !Common.isValidRoom(params.get("room")[0]))
                return HttpServletResponse.SC_BAD_REQUEST;

            //Setting sensitive data - require password verification
            if ((params.containsKey("email") || params.containsKey("passwd")) &&
                (!params.containsKey("verify") || !user.verifyPassword(params.get("verify")[0]))) {
                return HttpServletResponse.SC_FORBIDDEN;
            }
            
            if (params.containsKey("firstName"))
                user.setFirstName(params.get("firstName")[0]);

            if (params.containsKey("lastName"))
                user.setLastName(params.get("lastName")[0]);

            if (params.containsKey("room"))
                user.setRoom(params.get("room")[0]);

            if (params.containsKey("email"))
                user.setEmail(params.get("email")[0]);

            if (params.containsKey("passwd"))
                user.setPassword(params.get("passwd")[0]);

        } catch (SQLException | NamingException e) {
            throw new ServletException (e);
        }

        return HttpServletResponse.SC_NO_CONTENT;
    }
    
    private static final String EMAIL_PATTERN =
        "[A-Za-z0-9!#$%&'*+-/=?^_`{|}~.]@[A-Za-z0-9.]";
}
