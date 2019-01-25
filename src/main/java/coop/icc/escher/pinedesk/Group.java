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
                    while (rs.next()) groups.add(new Group (rs));
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
                    while (rs.next()) groups.add(new Group (rs));
                }
            }
        }

        return groups;
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
        Connection conn = rs.getStatement().getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(MEMBER_LOOKUP_SQL)) {
            pstmt.setLong(1, m_groupId);

            try (ResultSet urs = pstmt.executeQuery()) {
                while (urs.next()) m_members.add(getUser(urs));
            }
        }
    }

    public boolean hasMember (User user) {
        for (User u : m_members) {
            if (u.getId() == user.getId()) return true;
        }

        return false;
    }

    public List<User> getMember () {
        return Collections.unmodifiableList(m_members);
    }
    public void addMember (User user) throws SQLException,
                                             NamingException,
                                             UserExistsException,
                                             NoSuchUserException {
        if (!user.isValid(true))
            throw new NoSuchUserException ("User is invalid or does not exist");

        if (m_exists) {
            try (Connection conn = Common.getConnection()) {
                try (PreparedStatement pstmt = conn.prepareStatement(ADD_MEMBER_SQL)) {
                    pstmt.setLong(1, m_groupId);
                    pstmt.setLong(2, user.getId());

                    pstmt.executeUpdate();
                } catch (SQLException sqle) {
                    //TODO: Import an SQL driver enum to make thie more readable
                    switch (sqle.getErrorCode()) {
                        case 1022:  //duplicate primary key -> mapping already exists
                            throw UserExistsException.forGroupAdd(user, this, sqle);
                        case 1452:  //foreign key doesn't exist -> no such user
                            throw NoSuchUserException.forGroupAdd(user.getId(), sqle);
                        default:
                            throw sqle;
                    }
                }
            }
        }

        m_members.add(user);
    }
    public void removeMember (User user) throws SQLException,
                                                NamingException,
                                                NoSuchUserException {
        if (!user.isValid(true))
            throw new NoSuchUserException ("User is invalid or does not exist");

        if (m_exists) {
            try (Connection conn = Common.getConnection()) {
                try (PreparedStatement pstmt = conn.prepareStatement(REMOVE_MEMBER_SQL)) {
                    pstmt.setLong(1, m_groupId);
                    pstmt.setLong(2, user.getId());

                    pstmt.executeUpdate();

                    if (pstmt.getUpdateCount() == 0)
                        throw NoSuchUserException.forGroupRemove(user, this); 
                }
            }
        }

        for (int i = 0; i < m_members.length(); ++i) {
            if (m_members.get(i).getId() == user.getId()) {
                m_members.remove(i);
                break;
            }
        }
    }

    public User getAdmin () { return m_admin; }
    public void setAdmin (User user) throws SQLException,
                                            NamingException,
                                            NoSuchUserException {
        if (!user.isValid(true))
            throw new NoSuchUserException ("User is invalid or does not exist");

        if (m_exists) update("admin", user.getId());

        m_admin = user;
    }

    public String getName () { return m_name; }
    public void setName (String name) throws SQLException, NamingException {
        if (m_exists) update("name", name);

        m_name = name;
    }

    public String getDescription () { return m_description; }
    public void setDescription (String description) throws SQLException,
                                                           NamingException {
        if (m_exists) update("description", description);

        m_description = description;
    }

    private void update (String key, long value) throws SQLException,
                                                        NamingException {
        try (Connection conn = Common.getConnection()) {
            String sql = String.format(UPDATE_SQL, key);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, value);
                pstmt.setLong(2, m_groupId);

                pstmt.executeUpdate();
            }
        }
    }

    private void update (String key, String value) throws SQLException,
                                                          NamingException {
        try (Connection conn = Common.getConnection()) {
            String sql = String.format(UPDATE_SQL, key);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, value);
                pstmt.setLong(2, m_groupId);

                pstmt.executeUpdate();
            }
        }
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
    private static final String ADD_MEMBER_SQL = 
        "INSERT INTO gmembers (groupid, userid) VALUES (?,?)";
    private static final String REMOVE_MEMBER_SQL =
        "DELETE FROM gmembers WHERE groupid=? AND userid=?";
    private static final String UPDATE_SQL =
        "UPDATE group SET %s=? WHERE groupid=?";
}
