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
        Map<String,String[]> params = req.getParameterMap();
        String path = req.getPathInfo();
        JsonObjectBuilder info = Common.createObjectBuilder();
        User user = ServletUtils.getActiveUser(req.getSession());

        if (user == null) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            info.add("reason", "You must be logged in to view groups.");
            ServletUtils.writeJson(resp, info.build());
            return;
        }

        if (path == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            ServletUtils.writeJson(resp, info.build(), false);
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
            case "addMembers":
            case "removeMembers":
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
        JsonObjectBuilder info = Common.createObjectBuilder();
        User currentUser = ServletUtils.getActiveUser(req.getSession());

        if (user == null) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            info.add("reason", "You must be logged into edit groups");
            ServletUtils.writeJson(resp, info.build());
            return;
        }

        if (path == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            ServletUtils.writeJson(resp, info.build(), false);
            return;
        }

        String[] pathElements = path.substring(1).split("/");

        switch (pathElements.length) {
        case 1:
            switch (pathElements[0]) {
            case "new":
                {
                    User admin = currentUser;

                    if (params.containsKey("admin")) {
                        try {
                            String rawUser = params.get("admin")[0];

                            if (rawUser.matches("\\d+")) {
                                admin = User.lookup(Long.parseLong(rawUser));
                            } else if (rawUser.matches(EMAIL_PATTERN)) {
                                admin = User.lookup(rawUser);
                            } else {
                                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                                info.add("reason", "Malformed user query for admin parameter");
                                break;
                            }
                        } catch (SQLException | NamingException e) {
                            throw new ServletException (e);
                        } catch (NoSuchUserException nsue) {
                            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                            info.add("reason", "User " + rawUser + " does not exist.");
                            break;
                        }
                    }

                    resp.setStatus(addGroup(params, admin));
                }

                if (resp.getStatus() == HttpServletResponse.SC_BAD_REQUEST) {
                    info.add("reason", "Missing parameter or malformed short name");
                }
                break;
            default:
                resp.setHeader("Allow", "GET");
                resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                String editPath = req.getServletPath() + path + "/edit";
                String newPath = req.getServletPath() + path + "/addMembers";
                String rmPath = req.getServletPath() + path + "/removeMembers";
                info.add("reason", "Use " + editPath + ", " + newPath 
                         + ", or " + rmPath + " to edit groups");
                break;
            }
            break;
        case 2:
            {
                Group group = null;

                try {
                    String rawGroup = pathElements[0];

                    if (rawGroup.matches("\\d+")) {
                        group = Group.lookup(Long.longValue(rawGroup));
                    } else if (rawGroup.matches(SHORTNAME_PATTERN)) {
                        group = Group.lookup(rawGroup);
                    } else {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        info.add("reason", "Invalid group shortname");
                        break;
                    }
                } catch (SQLException | NamingException e) {
                    throw new ServletException (e);
                }

                switch (pathElements[1]) {
                case "edit":
                    if (currentUser.getId() != group.getAdmin().getId()) {
                        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        info.add("reason", "Must be group administrator to make changes");
                        break;
                    }

                    resp.setStatus(editGroup(group, params));

                    if (resp.getStatus() == HttpServletResponse.SC_BAD_REQUEST)
                        info.add("reason", "Invalid or missing parameter");

                    break;
                case "addMembers":
                case "removeMembers":
                }
            }
            break;
        default:
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

        ServletUtils.writeJson(resp, info.build(), false);
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
                                                             : user.getGroups());
                JsonArrayBuilder arr = Common.createArrayBuilder();

                for (Group group : groups) arr.add(group.jsonify(showMembers));

                ServletUtils.writeJson(resp, arr.build());
            } catch (SQLException | NamingException e) {
                throw new ServletException (e);
            }
            break;
        case "new":
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
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
        List<User> members = null;
        JsonArrayBuilder arr = Common.createArrayBuilder();

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
            
            members = group.getMembers();
        } catch (SQLException | NamingException e) {
            throw new ServletException (e);
        } catch (NoSuchGroupException nsge) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            info.add("reason", "The requested group does not exist");
            return;
        }
        
        for (User member : members) arr.add(member.jsonify(false));

        ServletUtils.writeJson(resp, arr.build());
    }
   
    private int addGroup (Map<String,String[]> params, User admin)
                         throws ServletException {
        Group newGroup = new Group (admin);

        try {
            newGroup.setAdmin(admin);
            
            if (params.containsKey("name")) {
                String name = params.get("name")[0];

                if (!name.matches(SHORTNAME_PATTERN))
                    return HttpServletResponse.SC_BAD_REQUEST;

                newGroup.setName(name);
            }

            if (params.containsKey("longName"))
                newGroup.setLongName(params.get("longName")[0]);

            if (params.containsKey("description"))
                newGroup.setDescription(params.get("description")[0]);

            Group.add(newGroup);
        } catch (SQLException | NamingException e) {
            throw new ServletException (e);
        } catch (IllegalArgumentException iae) {
            return HttpServletResponse.SC_BAD_REQUEST;
        }

        return HttpServletResponse.SC_OK;
    }

    private int editGroup (Group group, Map<String, String[]> params)
                          throws ServletException {
        try {
            if (params.containsKey("admin"))
        } catch (SQLException | NamingException e) {
            throw new ServletException (e);
        } catch (NoSuchUserException nsue) {
        }
    }

    private static final String SHORTNAME_PATTERN = "^[_\\-A-Za-z0-9]{1,25}$";
    private static final String EMAIL_PATTERN =
        "[A-Za-z0-9!#$%&'*+-/=?^_`{|}~.]@[A-Za-z0-9.]";
}
