package coop.icc.escher.pinedesk;

import java.sql.*;
import javax.sql.*;
import java.util.*;
import coop.icc.escher.pinedesk.util.DoubleKeyCache;

public class User {
    //STATIC LOOKUP/CREATE METHODS
    public static boolean exists (String email) throws SQLException,
                                                       NamingException {
        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(EXISTS_SQL)) {
                pstmt.setString(1, email);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return (rs.getInt(1) != 0);
                }
            }
        }

        return false;
    }

    public static boolean inCache (String email) {
        return s_userCache.contains(email);
    }

    public static boolean inCache (long id) {
        return s_userCache.contains(id);
    }

    public static User lookup (String email) throws SQLException,
                                                    NamingException,
                                                    NoSuchUserException {
        if (s_userCache.contains(email))
            return s_userCache.lookup(email);

        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_EMAIL_SQL)) {
                pstmt.setString(1, email);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return new User (rs);
                    else throw new NoSuchUserException (email);
                }
            }
        }

        return new User ();
    }
    
    public static User lookup (long id) throws SQLException,
                                                    NamingException,
                                                    NoSuchUserException {
        if (s_userCache.contains(id))
            return s_userCache.lookup(id);

        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_ID_SQL)) {
                pstmt.setLong(1, id);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return new User (rs);
                    else throw new NoSuchUserException (email);
                }
            }
        }

        return new User ();
    }

    public static void add (User newUser) throws SQLException,
                                                 NamingException,
                                                 UserExistsException {
        if (m_room.equals("")) 
            throw new IllegalArgumentException ("User room number not set.");
        if (m_email.equals(""))
            throw new IllegalArgumentException ("User email not set.");
        if (!m_google && m_passHash.equals(""))
            throw new IllegalArgumentException ("User password not set.");

        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(NEW_SQL)) {
                pstmt.setString(1, newUser.m_email);
                pstmt.setString(2, newUser.m_passHash);
                pstmt.setBoolean(3, newUser.m_google);
                pstmt.setString(4, newUser.m_firstName);
                pstmt.setString(5, newUser.m_lastName);
                pstmt.setString(6, newUser.m_room);

                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        newUser.m_userId = rs.getLong(1);
                        newUser.m_exists = true;
                        s_userCache.insert(newUser.m_userId, newUser.m_email, newUser);
                    }
                } catch (SQLException sqle) {
                    //UNIQUE constraint violation error code in MySQL/MariaDB
                    //TODO: Import the driver and use an enum instead
                    if (sqle.getErrorCode() == 1169)
                        throw new UserExistsException(newUser.m_email, sqle);
                    else
                        throw e;
                }
            }
        }
    }

    //CONSTRUCTORS
    public User () {
        m_email = "";
        m_passHash = "";
        m_firstName = "";
        m_lastName = "";
        m_room = "";
        m_exists = false;
    }
    User (ResultSet rs) throws SQLException {
        load(rs);
        s_userCache.insert(m_userId, m_email, this);
        m_exists = true;
    }

    //GETTERS AND SETTERS
    public long getId () { return m_userId; }

    public String getEmail () { return m_email; }
    public void setEmail (String newEmail) throws SQLExcpetion,
                                                  NamingException {
        if (m_exists) update("email", newEmail);

        m_email = newEmail;
    }

    public boolean verifyPassword (String password) {
        if (m_google) return false;

        PasswordVerifier verify = new PasswordVerifier ();

        return verify.authenticate(password.toCharArray(), m_passHash); 
    }
    public void setPassword (String newPassword) throws SQLException,
                                                        NamingException {
        if (m_google) return;

        PasswordVerifier verify = new PasswordVerifier ();
        String newHash = verify.hash(newPassword.toCharArray());

        if (m_exists) update("passhash", newHash);

        m_passHash = newHash;
    }

    public String getFirstName () { return m_firstName; }
    public String getLastName () { return m_lastName; }
    public void setFirstName (String newFirst) throws SQLException,
                                                      NamingException {
        if (m_exists) update("firstname", newFirst);

        m_firstName = newFirst;
    }
    public void setLastName (String newLast) throws SQLException,
                                                    NamingException {
        if (m_exists) update("lastname", newLast);

        m_lastName = newLast;
    }

    public String getRoom () { return m_room; }
    public void setRoom (String newRoom) throws SQLException, NamingException {
        newRoom = Common.makeStrictRoom(newRoom);

        //Invalid room numbers will have 0 length at this point
        if (newRoom.length() == 0) return;

        if (m_exists) update("room", newRoom);

        m_room = newRoom;
    }

    public boolean isGoogle () { return m_google; }
    public void setGoogle (boolean isGoogle) throws UserExistsException {
        //If a user signed in with Google, we shouldn't change them to a
        //regular account, and vice versa
        if (m_exists) throw new UserExistsException (isGoogle);

        m_google = isGoogle;

        if (m_google) m_passHash = "";
    }

    //PUBLIC HELPER FUNCTIONS
    public void reload () throws SQLException, NamingException {
        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(RELOAD_SQL)) {
                pstmt.setLong(1, m_userId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) load(rs);
                }
            }
        }
    }

    //PRIVATE HELPER FUNCTIONS
    private void update (String key, String value) throws SQLException,
                                                          NamingException {
        try (Connection conn = Common.getConnection()) {
            String sql = String.format(UPDATE_SQL, key);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, value);
                pstmt.setLong(2, m_userId);

                pstmt.executeUpdate();
            }
        }
    }

    private void update (String key, boolean value) throws SQLException,
                                                           NamingException {
        try (Connection conn = Common.getConnection()) {
            String sql = String.format(UPDATE_SQL, key);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setBoolean(1, value);
                pstmt.setLong(2, m_userId);

                pstmt.executeUpdate();
            }
        }
    }

    private void load (ResultSet rs) throws SQLException {
        m_userId = rs.getLong("userid");
        m_email = rs.getString("email");
        m_passHash = Common.notNull(rs.getString("email"));
        m_google = rs.getBoolean("google");
        m_firstName = Common.notNull(rs.getString("firstname"));
        m_lastName = Common.notNull(rs.getString("lastname"));
        m_room = rs.getString("room");
    }

    //MEMBERS
    private String m_email;
    private String m_passHash;
    private String m_firstName;
    private String m_lastName;
    private String m_room;
    private long m_userId;
    private bool m_google;
    private bool m_exists;

    //STATIC MEMBERS
    private static final String EXISTS_SQL = "SELECT COUNT(*) FROM users WHERE email=?";
    private static final String LOOKUP_EMAIL_SQL = "SELECT userid, email, "
        + "passhash, google, firstname, lastname, room FROM users WHERE email=?";
    private static final String LOOKUP_ID_SQL = "SELECT userid, email, "
        + "passhash, google, firstname, lastname, room FROM users WHERE userid=?";
    private static final String RELOAD_SQL = "SELECT email, passhash, google, "
        + "firstname, lastname, room FROM users WHERE userid=?";
    private static final String UPDATE_SQL = "UPDATE users SET %s=? WHERE userid=?";
    private static final String NEW_SQL = "INSERT INTO users (email, passhash, "
        + "google, firstname, lastname, room) VALUES (?,?,?,?,?,?)";

    private static DoubleKeyCache<Long, String, User> s_userCache;
}
