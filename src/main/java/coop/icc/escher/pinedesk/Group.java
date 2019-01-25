package coop.icc.escher.pinedesk;

import java.sql.*;
import javax.sql.*;
import java.util.*;

public class Group {
    //STATIC LOOKUP/CREATE METHODS
    public static List<Group> getAll () throws SQLException, NamingException {
        List<Group> groups = new ArrayList<Group>();

        try (Connection conn = Common.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery(LOOKUP_ALL_SQL)) {
                    while (rs.next()) groups.add(fromDb(rs));
                }
            }
        }

        return groups;
    }

    public static List<Group> getMembership (User user) throws SQLException,
                                                               NamingException {
        List<Group> groups = new ArrayList<Group>();

        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_BY_MEMBER_SQL)) {
                pstmt.setLong(1, user.getId());

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) groups.add(fromDb(rs));
                }
            }
        }

        return groups;
    }

    private static Group fromDb (ResultSet rs) throws SQLException {
        Group group = new Group (rs);
        Connection conn = rs.getStatement().getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(MEMBER_LOOKUP_SQL)) {
            pstmt.setLong(1, m_groupId);

            try (ResultSet urs = pstmt.executeQuery()) {
                while (urs.next()) group.m_members.add(getUser(urs));
            }
        }

        return group;
    }

    private static User getUser (ResultSet rs) throws SQLException {
        long id = rs.getLong("userid");

        if (User.inCache(id)) return User.lookup(id);

        return new User (rs);
    }

    //CONSTRUCTORS
    public Group () {
        this(null);
    }

    public Group (User admin) {
        m_name = "";
        m_description = "";
        m_admin = admin;
        m_exists = false;
        m_members = new ArrayList<User> ();
    }

    private Group (ResultSet rs) throws SQLException {
        m_groupId = rs.getLong("groupid");
        m_name = rs.getString("name");
        m_description = rs.getString("description");
        m_admin = getUser(rs);
        m_exists = true;

        m_members = new ArrayList<User> ();
    }

    private List<User> m_members;
    private User m_admin;
    private String m_name;
    private String m_description; 
    private long m_groupId;
    private boolean m_exists;

    private static final String LOOKUP_ALL_SQL = 
        "SELECT g.groupid, g.name, g.description, u.userid, u.email, "
        + "u.passhash, u.google, u.firstname, u.lastname, u.room "
        + "FROM groups g JOIN users u ON g.admin=u.userid ORDER BY g.groupid";
    private static final String LOOKUP_BY_MEMBER_SQL =
        "SELECT g.groupid, g.name, g.description, u.userid, u.email, "
        + "u.passhash, u.google, u.firstname, u.lastname, u.room "
        + "FROM groups g NATURAL JOIN gmember m "
        + "JOIN users u ON g.admin=u.userid "
        + "WHERE m.userid=? ORDER BY g.groupid";
    private static final String MEMBER_LOOKUP_SQL =
        "SELECT userid, u.email, u.passhash, u.google, u.firstname, "
        + "u.lastname, u.room FROM gmembers g NATURAL JOIN users u "
        + "WHERE g.groupid=? ORDER BY u.lastname ASC, u.firstname ASC";
}
