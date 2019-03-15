package coop.icc.escher.pinedesk;

import java.sql.*;
import javax.sql.*;
import java.util.*;
import java.time.LocalDateTime;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.naming.NamingException;

public class Ticket {
    public enum Status {
        UNASSIGNED("Unassigned"),
        IN_PROGRESS("In Progress"),
        COMPLETE("Complete");

        private final String value;

        Status (String s) { value = s; }

        @Override
        public String toString() { return value; }
    }

    public class Attachment {
        Attachment (Ticket parent, long id, String filepath) {
            m_parent = parent;
            m_attachId = id;
            m_filepath = filepath;

            try {
                Files.createDirectories(Paths.get(m_filepath).getParent());
            } catch (IOException ioe) {}
        }


        public long getId () { return m_attachId; }
        public String getFilePath () { return m_filepath; }
        public String getFileName () { 
            return m_filepath.replaceAll("*.\\/+", "");
        }
        
        public File open () { return new File (m_filepath); }
        public void delete () throws SQLException, NamingException,
                                     IOException {
            try (Connection conn = Common.getConnection()) {
                try (PreparedStatement pstmt = conn.prepareStatement(Ticket.REMOVE_ATTACH)) {
                    pstmt.setLong(1, m_parent.m_ticketId);
                    pstmt.setLong(2, m_attachId);

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

    public class Comment {
        Comment (Ticket parent, User postedBy, LocalDateTime postDate,
                 LocalDateTime editDate, String content, boolean statusChange,
                 long id) {
            m_parent = parent;
            m_postedBy = postedBy;
            m_postDate = postDate;
            m_content = content;
            m_statusChange = statusChange;
            m_commentId = id;
        }

        public Ticket getParent () { return m_parent; }
        public LocalDateTime getPostDate () { return m_postDate; }
        public LocalDateTime getEditDate () { return m_editDate; }
        public long getId () { return m_commentId; }
        public User getPoster () { return m_postedBy; }
        public boolean isStatusChange () { return m_statusChange; }

        public String getContent () { return m_content; }
        public void setContent (String content) throws SQLException,
                                                       NamingException {
            LocalDateTime newEditDate = LocalDateTime.now();

            update("content", content);
            update("editdate", Timestamp.valueOf(newEditDate));

            m_content = content;
            m_editDate = newEditDate;
        }
        
        public void delete () throws SQLException, NamingException {
            try (Connection conn = Common.getConnection()) {
                try (PreparedStatement pstmt = conn.prepareStatement(Ticket.REMOVE_COMMENT)) {
                    pstmt.setLong(1, m_parent.getId());
                    pstmt.setLong(2, m_commentId);

                    pstmt.executeUpdate();
                }
            }

            m_parent = null;
            m_postedBy = null;
            m_content = "";
            m_commentId = -1;
            m_postDate = null;
            m_editDate = null;
        }

        private void update (String key, String value) throws SQLException,
                                                              NamingException {
            String sql = String.format(Ticket.UPDATE_COMMENT, key);

            try (Connection conn = Common.getConnection()) {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, value);
                    pstmt.setLong(2, m_parent.getId());
                    pstmt.setLong(3, m_commentId);

                    pstmt.executeUpdate();
                }
            }
        }

        private void update (String key, Timestamp value) throws SQLException,
                                                                 NamingException {
            String sql = String.format(UPDATE_COMMENT, key);

            try (Connection conn = Common.getConnection()) {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setTimestamp(1, value);
                    pstmt.setLong(2, m_parent.getId());
                    pstmt.setLong(3, m_commentId);

                    pstmt.executeUpdate();
                }
            }
        }

        private Ticket m_parent;
        private User m_postedBy;
        private LocalDateTime m_postDate;
        private LocalDateTime m_editDate;
        private String m_content;
        private long m_commentId;
        private boolean m_statusChange;
    }

    public static Ticket lookup (long id) throws SQLException, NamingException,
                                                 NoSuchTicketException {
        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_ID_SQL)) {
                pstmt.setLong(1, id);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return new Ticket (rs);
                    else throw new NoSuchTicketException (id);
                }
            }
        }
    }

    public static List<Ticket> getAll () throws SQLException, NamingException {
        try (Connection conn = Common.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery(LOOKUP_ALL_SQL)) {
                    return buildTicketList(rs);
                }
            }
        }
    }

    public static List<Ticket> getGlobal () throws SQLException,
                                                   NamingException {
        try (Connection conn = Common.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery(LOOKUP_GLOBAL_SQL)) {
                    return buildTicketList(rs);
                }
            }
        }
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
    }

    public static List<Ticket> getByCompleteDate (LocalDateTime start,
                                                  LocalDateTime end)
                                                 throws SQLException,
                                                        NamingException {
        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_BY_COMPL_DATE_SQL)) {
                pstmt.setTimestamp(1, Timestamp.valueOf(start));
                pstmt.setTimestamp(2, Timestamp.valueOf(end));

                try (ResultSet rs = pstmt.executeQuery()) {
                    return buildTicketList(rs);
                }
            }
        }
    }

    public static void add (Ticket t) throws SQLException, NamingException {
        if (t.m_title.equals(""))
            throw new IllegalArgumentException ("No title given.");

        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(NEW_SQL)) {
                pstmt.setString(1, t.m_title);
                pstmt.setString(2, t.m_description);
                pstmt.setLong(3, t.m_reporter.getId());
                pstmt.setTimestamp(5, Timestamp.valueOf(t.m_reportDate));
                pstmt.setTimestamp(6, (t.m_completeDate == null ? null : Timestamp.valueOf(t.m_completeDate)));
                pstmt.setLong(7, t.m_group.getId());
                pstmt.setString(8, t.m_status.toString());
                pstmt.setBoolean(9, t.m_global);

                if (t.m_status == Status.UNASSIGNED) pstmt.setNull(4, Types.VARCHAR);
                else pstmt.setLong(4, t.m_assignee.getId());

                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        t.m_ticketId = rs.getLong(1);
                        t.m_exists = true;
                    }
                }
            }
        }
    }

    private static List<Ticket> buildTicketList (ResultSet rs) throws SQLException,
                                                                      NamingException {
        List<Ticket> tix = new ArrayList<Ticket> ();

        while (rs.next()) tix.add(new Ticket (rs));

        return tix;
    }

    Ticket (ResultSet rs) throws SQLException, NamingException {
        long tmpAssigneeId = rs.getLong("assignee");

        try {
            if (rs.wasNull()) m_assignee = null;
            else m_assignee = User.lookup(tmpAssigneeId);
            
            m_reporter = User.lookup(rs.getLong("reporter"));
            m_group = Group.lookup(rs.getLong("group"));
        } catch (NoSuchGroupException | NoSuchUserException e) {} //this should never happen

        m_ticketId = rs.getLong("ticketid");
        m_title = rs.getString("title");
        m_description = rs.getString("description");
        m_reportDate = rs.getTimestamp("reportdate").toLocalDateTime();
        m_status = Status.valueOf(rs.getString("status"));
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
                    while (rs.next())
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

    public Attachment createAttachment (String filepath) throws SQLException,
                                                                NamingException {
        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(INSERT_ATTACH)) {
                pstmt.setLong(1, m_ticketId);
                pstmt.setString(2, filepath);

                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next())
                        return new Attachment (this, rs.getLong(1), filepath);                
                }
            }
        }

        return null;
    }

    public List<Comment> getComments () throws SQLException, NamingException {
        List<Comment> comments = new ArrayList<Comment>();

        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_COMMENT_ALL)) {
                pstmt.setLong(1, m_ticketId);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        LocalDateTime postDate = rs.getTimestamp(2).toLocalDateTime();  
                        LocalDateTime editDate = rs.getTimestamp(3).toLocalDateTime();
                        boolean isStatus = rs.getBoolean(4);
                        User postedBy = isStatus ? null : User.lookup(rs.getLong(5));

                        comments.add(new Comment (this, postedBy, postDate,
                                                  editDate, rs.getString(3),
                                                  isStatus, rs.getLong(1)));
                    }
                } catch (NoSuchUserException nsue) {}   //this should never happen
            }
        }

        return comments;
    }

    public Comment getComment (long id) throws SQLException, NamingException {
        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(LOOKUP_COMMENT_ID)) {
                pstmt.setLong(1, m_ticketId);
                pstmt.setLong(2, id);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        LocalDateTime postDate = rs.getTimestamp(2).toLocalDateTime();
                        LocalDateTime editDate = rs.getTimestamp(3).toLocalDateTime();
                        boolean isStatus = rs.getBoolean(5);
                        User postedBy = isStatus ? null : User.lookup(rs.getLong(6));

                        return new Comment (this, postedBy, postDate, editDate,
                                            rs.getString(4), isStatus,
                                            rs.getLong(1));
                    }
                } catch (NoSuchUserException nsue) {}   //this should never happen
            }
        }

        return null;
    }

    public Comment createComment (User postedBy, String content) throws SQLException,
                                                                        NamingException {
        LocalDateTime postDate = LocalDateTime.now();
        Timestamp postTimestamp = Timestamp.valueOf(postDate);
        boolean isStatus = (postedBy == null);

        try (Connection conn = Common.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(INSERT_COMMENT)) {
                pstmt.setLong(1, m_ticketId);
                pstmt.setTimestamp(2, postTimestamp);
                pstmt.setTimestamp(3, postTimestamp);
                pstmt.setString(4, content);
                pstmt.setBoolean(5, isStatus);
                
                if (isStatus) pstmt.setNull(6, Types.VARCHAR);
                else pstmt.setLong(6, postedBy.getId());

                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return new Comment (this, postedBy, postDate, postDate,
                                            content, isStatus, rs.getLong(1));
                    }
                }
            }
        }

        return null;
    }

    public Comment createComment (String statusText) throws SQLException,
                                                            NamingException {
        return createComment(null, statusText);
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
        "SELECT attachmentid, filepath FROM tattach WHERE ticketid=?";
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

    private static final String LOOKUP_COMMENT_BASE =
        "SELECT commentid, postdate, editdate, content, statuschange, postedby "
        + "FROM tcomments WHERE ticketid=? ";
    private static final String LOOKUP_COMMENT_ID =
        LOOKUP_COMMENT_BASE + " AND commentid=?";
    private static final String LOOKUP_COMMENT_ALL =
        LOOKUP_COMMENT_BASE + " ORDER BY postdate ASC";
    private static final String INSERT_COMMENT =
        "INSERT INTO tcomments (ticketid, postdate, editdate, content, "
        + "statuschange, postedby)  VALUES (?,?,?,?,?,?)";
    private static final String REMOVE_COMMENT =
        "DELETE FROM tcomments WHERE ticketid=? AND commentid=?";
    private static final String UPDATE_COMMENT =
        "UPDATE tcomments SET %s=? WHERE ticketid=? AND commentid=?";
}
