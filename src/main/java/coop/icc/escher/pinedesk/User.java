package coop.icc.escher.pinedesk;

import java.sql.*;
import javax.sql.*;
import java.util.*;

public class User {
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

    public static User lookup (String email) throws SQLException,
                                                    NamingException,
                                                    NoSuchUserException {
        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_SQL)) {
                pstmt.setString(1, email);

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
        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(NEW_SQL)) {
                pstmt.setString(1, m_email);
                pstmt.setString(2, m_passHash);
                pstmt.setBoolean(3, m_google);
                pstmt.setString(4, m_firstName);
                pstmt.setString(5, m_lastName);
                pstmt.setString(6, m_room);

                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        newUser.m_userId = rs.getInt(1);
                        newUser.m_exists = true;
                    }
                }
            }
        }
    }

    public User () { m_exists = false; }
    User (ResultSet rs) throws SQLException {
        m_userId = rs.getInt(1);
        m_email = rs.getString(2);
        m_passHash = Common.notNull(rs.getString(3));
        m_google = rs.getBoolean(4);
        m_firstName = Common.notNull(rs.getString(5));
        m_lastName = Common.notNull(rs.getString(6));
        m_room = rs.getString(7);
        m_exists = true;
    }

    public int getId () { return m_userId; }

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

    private void update (String key, String value) throws SQLException,
                                                          NamingException {
        try (Connection conn = Common.getConnection()) {
            String sql = String.format(UPDATE_SQL, key);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, value);
                pstmt.setInt(2, m_userId);

                pstmt.executeUpdate();
            }
        }
    }

    private void update (String key, int value) throws SQLException,
                                                       NamingException {
        try (Connection conn = Common.getConnection()) {
            String sql = String.format(UPDATE_SQL, key);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, value);
                pstmt.setInt(2, m_userId);

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
                pstmt.setInt(2, m_userId);

                pstmt.executeUpdate();
            }
        }
    }

    private static String EXISTS_SQL = "SELECT COUNT(*) FROM users WHERE email=?";
    private static String LOOKUP_SQL = "SELECT userid, email, passhash, "
        + "google, firstname, lastname, room FROM users WHERE email=?";
    private static String UPDATE_SQL = "UPDATE users SET %s=? WHERE userid=?";
    private static String NEW_SQL = "INSERT INTO users (email, passhash, "
        + "google, firstname, lastname, room) VALUES (?,?,?,?,?,?)";

    private String m_email;
    private String m_passHash;
    private String m_firstName;
    private String m_lastName;
    private String m_room;
    private int m_userId;
    private bool m_google;
    private bool m_exists;
}
