package coop.icc.escher.pinedesk;

import java.sql.*;
import javax.sql.*;
import javax.json.*;
import javax.naming.*;

public class Common {
    static {
        s_bldFactory = Json.createBuilderFactory(null);
    }

    public static Connection getConnection () throws NamingException, SQLException {
        Context ctx = (Context)(new InitialContext()).lookup("java:comp/env");

        return ((DataSource)ctx.lookup("jdbc/pinedesk")).getConnection();
    }
    
    public static String notNull (String str) {
        return (str == null) ? "" : str;
    }
    
    public static boolean isValidRoom (String room) {
        return isValidRoom(room, false);
    }
    
    public static boolean isValidRoom (String room, boolean strict) {
        return room.matches(strict ? ROOM_REGEX_STRICT : ROOM_REGEX_LOOSE);
    }

    public static String makeStrictRoom (String room) {
        if (!isValidRoom(room)) return "";

        return room.replaceAll("-","");
    }

    public static JsonObjectBuilder createObjectBuilder () {
        return s_bldFactory.createObjectBuilder();
    }

    public static JsonArrayBuilder createArrayBuilder () {
        return s_bldFactory.createArrayBuilder();
    }

    public static final String ROOM_REGEX_STRICT = "^[FKRVZBSWT][12][1-9]$";
    public static final String ROOM_REGEX_LOOSE = "^[FKRVZBSWT]+-*[12][1-9]$";
    
    private static JsonBuilderFactory s_bldFactory;
}
