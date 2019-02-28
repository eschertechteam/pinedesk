package coop.icc.escher.pinedesk;

import java.sql.*;
import javax.sql.*;
import java.util.*;
import java.time.LocalDateTime;
import java.io.File;
import java.nio.file.Files;

public class Ticket {
    public enum Status {
        UNASSIGNED("Unassigned"),
        IN_PROGRESS("In Progress"),
        COMPLETE("Complete")

        private final String value;

        public Status () {
            this(UNASSIGNED);
        }

        public Status (String value_) {
            value = value_;
        }

        @Override
        public String toString() { return value; }
    }

    public class Attachment {
        Attachment (Ticket parent, long id, String filepath) {
            m_parent = parent;
            m_id = id;
            m_filepath = filepath;

            try {
                Files.createDirectories(Paths.get(m_filepath).getParent());
            } catch (IOException ioe) {}
        }

        long getId () { return m_attachId; }
        String getFilePath () { return m_filepath; }
        String getFileName () { return m_filepath.replaceAll("*.\\/+", ""); }
        File open () { return File (m_filepath); }
        void delete () throws SQLException, NamingException, IOException {
            try (Connection conn = Common.getConnection()) {
                try (PreparedStatement pstmt = conn.prepareStatement(REMOVE_ATTACH)) {
                    pstmt.setLong(1, m_parent.m_ticketId);
                    pstmt.setLong(2, m_id);

                    pstmt.executeUpdate();
                }
            }

            //Delete file
            Files.delete(open().toPath());
        }

        private Ticket m_parent;
        private long m_attachId;
        private String m_filepath;
    }

    public static Ticket lookup (long id) throws SQLException, NamingException {
        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_ID_SQL)) {
                pstmt.setLong(1, id);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return new Ticket (rs);
                    else throw new NoSuchTicketException (id);
                }
            }
        }

        return new Ticket ();
    }

    public static List<Ticket> getAll () throws SQLException, NamingException {
        try (Connection conn = Common.getConnection()) {
            try (Statement stmt = conn.getStatement()) {
                try (ResultSet rs = stmt.executeQuery(LOOKUP_ALL_SQL)) {
                    return buildTicketList(rs);
                }
            }
        }

        return new ArrayList<Ticket> ();
    }

    public static List<Ticket> getGlobal () throws SQLException,
                                                   NamingException {
        try (Connection conn = Common.getConnection()) {
            try (Statement stmt = conn.getStatement()) {
                try (ResultSet rs = stmt.executeQuery(LOOKUP_GLOBAL_SQL)) {
                    return buildTicketList(rs);
                }
            }
        }

        return new ArrayList<Ticket> ();
    }

    public static List<Ticket> getByReporter (User reporter) throws SQLException, 
                                                                    NamingException {
        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_BY_REPORTER_SQL)) {
                pstmt.setLong(1, reporter.getId());

                try (ResultSet rs = pstmt.executeQuery()) {
                    return buildTicketList(rs);
                }
            }
        }

        return new ArrayList<Ticket> ();
    }

    public static List<Ticket> getByAssignee (User assignee) throws SQLException,
                                                                    NamingException {
        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_BY_ASSIGNEE_SQL)) {
                pstmt.setLong(1, assignee.getId());

                try (ResultSet rs = pstmt.executeQuery()) {
                    return buildTicketList(rs);
                }
            }
        }

        return new ArrayList<Ticket> ();
    }

    public static List<Ticket> getByGroup (Group group) throws SQLException,
                                                               NamingException {
        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_BY_GROUP_SQL)) {
                pstmt.setLong(1, group.getId());

                try (ResultSet rs = pstmt.executeQuery()) {
                    return buildTicketList(rs);
                }
            }
        }

        return new ArrayList<Ticket> ();
    }

    public static List<Ticket> getByReportDate (LocalDateTime start,
                                                LocalDateTime end)
                                               throws SQLException,
                                                      NamingException {
        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_BY_REQ_DATE_SQL)) {
                pstmt.setTimestamp(1, Timestamp.valueOf(start));
                pstmt.setTimestamp(2, Timestamp.valueOf(end));

                try (ResultSet rs = pstmt.executeQuery()) {
                    return buildTicketList(rs);
                }
            }
        }

        return new ArrayList<Ticket> ();
    }

    public static List<Ticket> getByCompleteDate (LocalDateTime start,
                                                  LocalDateTime end)
                                                 throws SQLException,
                                                        NamingException {
        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_BY_COMPL_DATE_SQL)) {
                pstmt.setTimestamp(1, new Timestamp (start));
                pstmt.setTimestamp(2, new Timestamp (end));

                try (ResultSet rs = pstmt.executeQuery()) {
                    return buildTicketList(rs);
                }
            }
        }

        return new ArrayList<Ticket> ();
    }

    public static void add (Ticket t) throws SQLException, NamingException {
        if (m_title.equals(""))
            throw new IllegalArgumentException ("No title given.");

        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(NEW_SQL)) {
                pstmt.setString(1, t.m_title);
                pstmt.setString(2, t.m_description);
                pstmt.setLong(3, t.m_reporter.getId);
                pstmt.setTimestamp(5, new Timestamp (t.m_reportDate));
                pstmt.setTimestamp(6, (t.m_completeDate == null ? null : new Timestamp (t.m_completeDate)));
                pstmt.setLong(7, t.m_group.getId());
                pstmt.setString(8, t.m_status.toString());
                pstmt.setBoolean(9, t.m_global);

                if (m_status == Status.UNASSIGNED) pstmt.setNull(4);
                else pstmt.setLong(4, t.m_assignee.getId());

                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        t.m_userId = rs.getLong(1);
                        t.m_exists = true;
                    }
                }
            }
        }
    }

    private static List<Ticket> buildTicketList (ResultSet rs) throws SQLException {
        List<Ticket> tix = new ArrayList<Ticket> ();

        while (rs.next()) tix.add(new Ticket (rs));

        return tix;
    }

    Ticket (ResultSet rs) throws SQLException {
        long tmpAssigneeId = rs.getLong("assignee");

        if (rs.wasNull()) m_assignee = null;
        else m_assignee = User.lookup(tmpAssigneeId);

        m_ticketId = rs.getLong("ticketid");
        m_title = rs.getString("title");
        m_description = rs.getString("description");
        m_reporter = User.lookup(rs.getLong("reporter"));
        m_reportDate = rs.getTimestamp("reportdate");
        m_group = Group.lookup(rs.getLong("group"));
        m_status = new Status (rs.getString("status"));
        m_global = rs.getBoolean("global");

        if (rs.getTimestamp("completedate") == null)
            m_completeDate = rs.getTimestamp("completedate").toLocalDateTime();
        else
            m_completeDate = null;

        m_exists = true;
    }

    public Ticket () {
        this("", null, null);
    }

    public Ticket (String title, User reporter, Group group) {
        m_title = title;
        m_description = "";
        m_reporter = reporter;
        m_assignee = null;
        m_reportDate = LocalDateTime.now();
        m_completeDate = null;
        m_group = group;
        m_status = Status.UNASSIGNED;
        m_global = false;

        m_exists = false;
    }

    public long getId () { return m_ticketId; }

    public String getTitle () { return m_title; }
    public void setTitle (String title) throws SQLException, NamingException {
        if (m_exists) update("title", title);

        m_title = title;
    }

    public String getDescription () { return m_description; }
    public void setDescription (String description) throws SQLException,
                                                           NamingException {
        if (m_exists) update("description", description);

        m_description = description;
    }

    public User getReporter () { return m_reporter; }
    public void setReporter (User reporter) throws SQLException,
                                                   NamingException {
        if (m_exists) update("reporter", reporter.getId());

        m_reporter = reporter;
    }

    public User getAssignee () { return m_assignee; }
    public void setAssignee (User assignee) throws SQLException,
                                                   NamingException {
        if (m_exists) update("assignee", assignee.getId());

        m_assignee = assignee;
    }

    public Group getGroup () { return m_group; }
    public void setGroup (Group group) throws SQLException, NamingException {
        if (m_exists) update("group", group.getId());

        m_group = group;
    }

    public LocalDateTime getReportDate () { return m_reportDate; }

    public LocalDateTime getCompleteDate () { return m_completeDate; }
    public void setCompleteDate (LocalDateTime completeDate)
        throws SQLException, NamingException {
        if (m_exists) update("completedate", Timestamp.valueOf(completeDate));

        m_completeDate = completeDate;
    }

    public Status getStatus () { return m_status; }
    public void setStatus (Status status) throws SQLException, NamingException {
        if (m_exists) update("status", status.toString());

        m_status = status;
    }

    public boolean isGlobal () { return m_global; }
    public void setGlobal (boolean global) throws SQLException,
                                                  NamingException {
        if (m_exists) update("global", global);

        m_global = global;
    }

    public List<Attachment> getAttachments () throws SQLException,
                                                     NamingException {
        List<Attachment> attachments = new ArrayList<Attachment>();

        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_ATTACH_ALL)) {
                pstmt.setLong(1, m_ticketId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next())
                        attachments.add(new Attachment (this, rs.getLong(1), rs.getString(2)));
                }
            }
        }

        return attachments;
    }

    public Attachment getAttachment (String filepath) throws SQLException,
                                                             NamingException {
        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_ATTACH_FILEPATH)) {
                pstmt.setLong(1, m_ticketId);
                pstmt.setString(2, filepath);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next())
                        return new Attachment (this, rs.getLong(1), rs.getString(2));
                }
            }
        }

        return null;
    }

    public Attachment getAttachment (long id) throws SQLException,
                                                     NamingException {
        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_ATTACH_FILEPATH)) {
                pstmt.setLong(1, m_ticketId);
                pstmt.setLong(2, id);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next())
                        return new Attachment (this, rs.getLong(1), rs.getString(2));
                }
            }
        }

        return null;
    }

    public Attachment createAttachment (String filepath) {
        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(INSERT_ATTACH)) {
                pstmt.setLong(1, m_ticketId);
                pstmt.setString(2, filepath);

                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next())
                        return Attachment (this, rs.getLong(1), filepath);                
                }
            }
        }

        return null;
    }

    private void update (String key, String value) throws SQLException,
                                                          NamingException {
        try (Connection conn = Common.getConnection()) {
            String sql = String.format(UPDATE_SQL, key);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, value);
                pstmt.setLong(2, m_ticketId);

                pstmt.executeUpdate();
            }
        }
    }

    private void update (String key, Timestamp value) throws SQLException,
                                                             NamingException {
        try (Connection conn = Common.getConnection()) {
            String sql = String.format(UPDATE_SQL, key);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setTimestamp(1, value);
                pstmt.setLong(2, m_ticketId);

                pstmt.executeUpdate();
            }
        }
    }

    private void update (String key, long value) throws SQLException, 
                                                        NamingException {
        try (Connection conn = Common.getConnection()) {
            String sql = String.format(UPDATE_SQL, key);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, value);
                pstmt.setLong(2, m_ticketId);

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
                pstmt.setLong(2, m_ticketId);

                pstmt.executeUpdate();
            }
        }
    }

    private Group m_group;
    private User m_reporter;
    private User m_assignee;
    private String m_title;
    private String m_description;
    private LocalDateTime m_reportDate;
    private LocalDateTime m_completeDate;
    private Status m_status;
    private long m_ticketId;
    private boolean m_global;
    private boolean m_exists;

    //SQL query/update strings
    private static final String LOOKUP_BASE_SQL =
        "SELECT ticketid, title, description, reporter, assignee, reportdate, "
        + "completedate, group, status, global "
        + "FROM tickets t ";
    private static final String LOOKUP_SUFFIX_SQL = "ORDER BY reportdate DESC";
    private static final String LOOKUP_ALL_SQL =
        LOOKUP_BASE_SQL + LOOKUP_SUFFIX_SQL;
    private static final String LOOKUP_GLOBAL_SQL =
        LOOKUP_BASE_SQL + " WHERE global=1 " + LOOKUP_SUFFIX_SQL;
    private static final String LOOKUP_ID_SQL =
        LOOKUP_BASE_SQL + " WHERE ticketid=? " + LOOKUP_SUFFIX_SQL;
    private static final String LOOKUP_BY_GROUP_SQL =
        LOOKUP_BASE_SQL + " WHERE group=? " + LOOKUP_SUFFIX_SQL;
    private static final String LOOKUP_BY_REPORTER_SQL =
        LOOKUP_BASE_SQL + " WHERE reporter=? " + LOOKUP_SUFFIX_SQL;
    private static final String LOOKUP_BY_ASSIGNEE_SQL =
        LOOKUP_BASE_SQL + " WHERE assignee=? " + LOOKUP_SUFFIX_SQL;
    private static final String LOOKUP_BY_REQ_DATE_SQL =
        LOOKUP_BASE_SQL + " WHERE reportdate BETWEEN ? AND ? "
        + LOOKUP_SUFFIX_SQL;
    private static final String LOOKUP_BY_COMPL_DATE_SQL =
        LOOKUP_BASE_SQL + " WHERE completedate BETWEEN ? AND ?"
        + LOOKUP_SUFFIX_SQL;
    
    private static final String UPDATE_SQL = 
        "UPDATE tickets SET %s=? WHERE ticketid=?";

    private static final String NEW_SQL =
        "INSERT INTO tickets (title, description, reporter, assignee, "
        + "reportdate, completedate, group, status, global) VALUES "
        + "(?,?,?,?,?,?,?,?,?)";

    private static final String LOOKUP_ATTACH_BASE =
        "SELECT attachmentid, filepath FROM tattach a WHERE ticketid=?";
    private static final String LOOKUP_ATTACH_FILEPATH =
        LOOKUP_ATTACH_BASE + " AND filepath=?";
    private static final String LOOKUP_ATTACH_ID =
        LOOKUP_ATTACH_BASE + " AND attachmentid=?";
    private static final String LOOKUP_ATTACH_ALL =
        LOOKUP_ATTACH_BASE + " ORDER BY attachid ASC";
    private static final String INSERT_ATTACH =
        "INSERT INTO tattach (ticketid, filepath) VALUES (?,?)";
    private static final String REMOVE_ATTACH =
        "DELETE FROM tattach WHERE ticketid=? AND attachmentid=?";
}
