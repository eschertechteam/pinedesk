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

@WebServlet(name="GroupsServlet", urlPatterns={ "/api/groups/*" })
public class GroupsServlet extends HttpServlet {
    @Override
    protected void doGet (HttpServletRequest req, HttpServletResponse resp)
                         throws ServletException {
        String path = req.getPathInfo();
        JsonObjectBuilder info = Common.createObjectBuilder();
        User user = ServletUtils.getCurrentUser(req.getSession());

        if (path == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            ServletUtils.writeJson(resp, info.build(), false);
            return;
        }

        if (user == null) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            info.add("reason", "You must be logged in to view groups.");
            ServletUtils.writeJson(resp, info.build());
            return;
        }

        String[] pathElements = path.substring(1).split("/");

        switch (pathElements.length) {
        case 1: {
                queryGroup(params, resp, pathElements[0], user);

                switch (resp.getStatus()) {
                case HttpServletResponse.SC_BAD_REQUEST:
                    info.add("reason", "Malformatted group ID or shortname");
                    break;
                case HttpServletResponse.SC_NOT_FOUND:
                    info.add("reason", "The requested group does not exist.");
                    break;
                }
            }
            break;
        case 2:
            switch (pathElements[1]) {
            case "members":
                queryMembers(resp, pathElements[0], info);
                break;
            case "add":
            case "remove":
            case "edit":
                resp.setHeader("Allow", "POST");
                resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                break;
            default:
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                info.add("reason", "The action `" + pathElements[1] + "` is not supported.");
            }
            break;
        default:
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

        if (!resp.isCommitted())
            ServletUtils.writeJson(resp, info.build(), false);
    }

    protected void doPost (HttpServletRequest req, HttpServletResponse resp)
                          throws ServletException {
        Map<String,String[]> params = req.getParameterMap();
        String path = req.getPathInfo();
        JsonObjectBuilder = ServletUtils.createObjectBuilder();
        User currentUser = ServletUtils.getCurrentUser(req.getSession());
    }

    private void queryGroup (Map<String, String[]> params,
                             HttpServletResponse resp, String rawQuery,
                             User user) throws ServletException {
        boolean showMembers = (params.containsKey("members")
                               && params.get("members")[0].equals("true"));

        switch (rawQuery) {
        case "all":
        case "mine":
            try {
                List<Group> groups = (rawQuery.equals("all") ? Group.getAll() 
                                                             : user.getGroups);
                JsonArrayBuilder arr = Common.createArrayBuilder();

                for (Group group : groups) arr.add(group.jsonify(showMembers));

                ServletUtils.writeJson(resp, arr);
            } catch (SQLException | NamingException e) {
                throw new ServletException (e);
            }
            break;
        default:
            try {
                Group group = null;

                if (rawQuery.matches(SHORTNAME_PATTERN)) {
                    group = Group.lookup(rawQuery);
                } else if (rawQuery.matches("^\\d+$")) {
                    group = Group.lookup(Long.parseLong(rawQuery));
                } else {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                }

                ServletUtils.writeJson(resp, group.jsonify(showMembers).build());
            } catch (SQLException | NamingException e) {
                throw new ServletException (e);
            } catch (NoSuchGroupException nsge) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        }
    }

    private void queryMembers (HttpServletResponse resp, String rawGroup,
                               JsonObjectBuilder info) 
                              throws ServletException {
        if (rawGroup.equals("all") || rawGroup.equals("mine")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            info.add("reason", "The members action requires a specific group");
            return;
        }

        Group group = null;

        try {
            if (rawGroup.matches(SHORTNAME_PATTERN)) {
                group = Group.lookup(rawGroup);
            } else if (rawGroup.matches("^\\d+$")) {
                group = Group.lookup(Long.parseLong(rawGroup));
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                info.add("reason", "Malformatted group ID or shortname");
                return;
            }
        } catch (SQLException | NamingException e) {
            throw new ServletException (e);
        } catch (NoSuchGroupException nsge) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            info.add("reason", "The requested group does not exist");
            return;
        }

        List<User> members = group.getMembers();
        JsonArrayBuilder arr = Common.createArrayBuilder();

        for (User member : members) arr.add(member.jsonify(false));

        ServletUtils.writeJson(resp, arr.build());
    }
    
    private static final String SHORTNAME_PATTERN = "^[_\\-A-Za-z0-9]{1,25}$";
}
