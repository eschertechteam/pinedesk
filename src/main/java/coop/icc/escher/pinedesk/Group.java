package coop.icc.escher.pinedesk;

import java.sql.*;
import javax.sql.*;
import java.util.*;
import javax.json.*;
import javax.naming.NamingException;

import coop.icc.escher.pinedesk.util.DoubleKeyCache;

public class Group {
    //STATIC LOOKUP/CREATE METHODS
    public static List<Group> getAll () throws SQLException, NamingException {
        List<Group> groups = new ArrayList<Group>();

        try (Connection conn = Common.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery(LOOKUP_ALL_SQL)) {
                    while (rs.next()) {
                        if (s_groupCache.containsK1(rs.getLong("groupid")))
                            groups.add(s_groupCache.lookupK1(rs.getLong("groupid")));
                        else
                            groups.add(new Group (rs));
                    }
                }
            }
        }

        return groups;
    }

    public static List<Group> getByUser (User user) throws SQLException,
                                                           NamingException {
        List<Group> groups = new ArrayList<Group>();

        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_BY_MEMBER_SQL)) {
                pstmt.setLong(1, user.getId());

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        if (s_groupCache.containsK1(rs.getLong("groupid")))
                            groups.add(s_groupCache.lookupK1(rs.getLong("groupid")));
                        else
                            groups.add(new Group (rs));
                    }
                }
            }
        }

        return groups;
    }

    public static Group lookup (String name) throws SQLException,
                                                    NamingException,
                                                    NoSuchGroupException {
        if (s_groupCache.containsK2(name))
            return s_groupCache.lookupK2(name);

        try (Connection conn = Common.getConnection()) {
            String sql = String.format(LOOKUP_BY_ATTR_SQL, "name");

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, name);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return new Group (rs);
                    else throw new NoSuchGroupException (name);
                }
            }
        }
    }

    public static Group lookup (long id) throws SQLException,
                                                NamingException,
                                                NoSuchGroupException {
        if (s_groupCache.containsK1(id))
            return s_groupCache.lookupK1(id);

        try (Connection conn = Common.getConnection()) {
            String sql = String.format(LOOKUP_BY_ATTR_SQL, "groupid");

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, id);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return new Group (rs);
                    else throw new NoSuchGroupException (id);
                }
            }
        }
    }

    public static void add (Group group) throws SQLException,
                                                NamingException,
                                                GroupExistsException {
        if (group.m_members.size() == 0)
            throw new IllegalArgumentException ("No members in group");
        if (group.m_admin == null)
            throw new IllegalArgumentException ("Group has no administrator");
        if (group.m_name.length() == 0)
            throw new IllegalArgumentException ("Group has no name");

        try (Connection conn = Common.getConnection()) {
            //Create group
            try (PreparedStatement pstmt = conn.prepareStatement(GROUP_ADD_SQL)) {
                pstmt.setString(1, group.m_name);
                pstmt.setString(2, group.m_longName);
                pstmt.setString(3, group.m_description);
                pstmt.setLong(4, group.m_groupId);

                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        group.m_groupId = rs.getLong(1);
                        group.m_exists = true;
                    }
                }
            } catch (SQLException sqle) {
                //MySQL/MariaDB error code for duplicate primary key
                //TODO: use an enum for this
                if (sqle.getErrorCode() == 1022)
                    throw new GroupExistsException(group, sqle);
                else
                    throw sqle;
            }

            //Add group members
            try (PreparedStatement pstmt = conn.prepareStatement(ADD_MEMBER_SQL)) {
                for (User member : group.m_members) {
                    pstmt.setLong(1, group.m_groupId);
                    pstmt.setLong(2, member.getId());
                    pstmt.addBatch();
                }

                pstmt.executeBatch();
            }
        }

        s_groupCache.insert(group.m_groupId, group.m_name, group);
    }

    private static User getUser (ResultSet rs) throws SQLException,
                                                      NamingException {
        long id = rs.getLong("userid");

        try {
            if (User.inCache(id)) return User.lookup(id);
        } catch (NoSuchUserException nsue) {}

        return new User (rs);
    }

    //CONSTRUCTORS
    public Group () {
        this((User)null);
    }

    public Group (User admin) {
        m_name = m_longName = m_description = "";
        m_admin = admin;
        m_exists = false;
        m_members = new ArrayList<User> ();
    }

    Group (ResultSet rs) throws SQLException, NamingException {
        m_groupId = rs.getLong("groupid");
        m_name = rs.getString("name");
        m_longName = rs.getString("longname");
        m_description = rs.getString("description");
        m_admin = getUser(rs);
        m_exists = true;

        m_members = new ArrayList<User> ();

        s_groupCache.insert(m_groupId, m_name, this);
    }

    public long getId () { return m_groupId; }

    public boolean hasMember (User user) throws SQLException, NamingException {
        if (m_exists && m_members.size() == 0) updateMembers();

        for (User u : m_members) {
            if (u.getId() == user.getId()) return true;
        }

        return false;
    }

    public List<User> getMembers () throws SQLException, NamingException {
        if (m_exists && m_members.size() == 0) updateMembers();

        return Collections.unmodifiableList(m_members);
    }
    public void addMember (User user) throws SQLException,
                                             NamingException,
                                             UserExistsException,
                                             NoSuchUserException {
        if (!user.isValid(true))
            throw new NoSuchUserException ("User is invalid or does not exist");

        if (m_exists) {
            if (m_members.size() == 0) updateMembers();

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
                            throw NoSuchUserException.forUserLookup(user.getId(), sqle);
                        default:
                            throw sqle;
                    }
                }
            }
        } else {
            if (hasMember(user))
                throw UserExistsException.forGroupAdd(user, this);
        }

        m_members.add(user);
    }
    public void removeMember (User user) throws SQLException,
                                                NamingException,
                                                NoSuchUserException {
        if (!user.isValid(true))
            throw new NoSuchUserException ("User is invalid or does not exist");

        if (m_exists) {
            if (m_members.size() == 0) updateMembers();

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

        for (int i = 0; i < m_members.size(); ++i) {
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
        if (!hasMember(user))
            throw new NoSuchUserException ("User is not a member of this group");

        if (m_exists) update("admin", user.getId());

        m_admin = user;
    }

    public String getName () { return m_name; }
    public void setName (String name) throws SQLException, NamingException {
        if (m_exists) update("name", name);

        m_name = name;
    }

    public String getLongName () { return m_longName; }
    public void setLongName (String longName) throws SQLException, NamingException {
        if (m_exists) update("longname", longName);

        m_longName = longName;
    }

    public String getDescription () { return m_description; }
    public void setDescription (String description) throws SQLException,
                                                           NamingException {
        if (m_exists) update("description", description);

        m_description = description;
    }

    public JsonObjectBuilder jsonify (boolean withMembers) throws SQLException,
                                                                  NamingException {
        if (!m_exists) return null;

        JsonObjectBuilder jGroup = Common.createObjectBuilder()
            .add("id", m_groupId)
            .add("name", m_name)
            .add("longName", m_longName)
            .add("description", m_description)
            .add("admin", m_admin.jsonify(false));

        if (withMembers) {
            if (m_members.size() == 0) updateMembers();

            JsonArrayBuilder jMembers = Common.createArrayBuilder();

            for (User member : m_members) jMembers.add(member.jsonify(false));

            jGroup.add("members", jMembers);
        }

        return jGroup;
    }

    public JsonObjectBuilder jsonify () throws SQLException, NamingException {
        return jsonify(false);
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

    private void updateMembers () throws SQLException,
                                         NamingException {
        m_members.clear();

        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(MEMBER_LOOKUP_SQL)) {
                pstmt.setLong(1, m_groupId);

                try (ResultSet urs = pstmt.executeQuery()) {
                    while (urs.next()) m_members.add(getUser(urs));
                }
            }
        }
    }

    private List<User> m_members;
    private User m_admin;
    private String m_name;
    private String m_longName;
    private String m_description; 
    private long m_groupId;
    private boolean m_exists;

    private static DoubleKeyCache<Long, String, Group> s_groupCache;

    private static final String LOOKUP_ALL_SQL = 
        "SELECT g.groupid, g.name, g.longname, g.description, u.userid, "
        + "u.email, u.google, u.firstname, u.lastname, u.room "
        + "FROM groups g JOIN users u ON g.admin=u.userid ORDER BY g.groupid";
    private static final String LOOKUP_BY_ATTR_SQL =
        "SELECT g.groupid, g.name, g.longname, g.description, u.userid, "
        + "u.email, u.google, u.firstname, u.lastname, u.room "
        + "FROM groups g JOIN users u ON g.admin=u.userid WHERE g.%s=?";
    private static final String LOOKUP_BY_MEMBER_SQL =
        "SELECT g.groupid, g.name, g.longname, g.description, u.userid, "
        + "u.email, u.passhash, u.google, u.firstname, u.lastname, u.room "
        + "FROM groups g NATURAL JOIN gmember m "
        + "JOIN users u ON g.admin=u.userid "
        + "WHERE m.userid=? ORDER BY g.groupid";
    private static final String MEMBER_LOOKUP_SQL =
        "SELECT userid, u.email, u.passhash, u.google, u.firstname, "
        + "u.lastname, u.room FROM gmembers g NATURAL JOIN users u "
        + "WHERE g.groupid=? ORDER BY u.lastname ASC, u.firstname ASC";
    private static final String GROUP_ADD_SQL =
        "INSERT INTO groups (name, longname, description, admin) "
        + "VALUES (?,?,?,?)";
    private static final String ADD_MEMBER_SQL = 
        "INSERT INTO gmembers (groupid, userid) VALUES (?,?)";
    private static final String REMOVE_MEMBER_SQL =
        "DELETE FROM gmembers WHERE groupid=? AND userid=?";
    private static final String UPDATE_SQL =
        "UPDATE group SET %s=? WHERE groupid=?";
}
