package coop.icc.escher.pinedesk;

public class NoSuchUserException extends Exception {
    public NoSuchUserException (String reason) {
        super(reason);
    }
    public NoSuchUserException (String reason, Throwable cause) {
        super(reason, cause);
    }

    public static NoSuchUserException forUserLookup (String email) {
        new NoSuchUserException ("No user with email " + email + " could be found.");
    }
    public static NoSuchUserException forUserLookup (String email, Throwable cause) {
        new NoSuchUserException ("No user with email " + email + " could be found.", cause);
    }

    public static NoSuchUserException forGroupAdd (long id) {
        new NoSuchUserException ("User " + String.valueOf(id) + " does not exist.");
    }
    public static NoSuchUserException forGroupAdd (long id, Throwable cause) {
        new NoSuchUserException ("User " + String.valueOf(id) + " does not exist." , cause);
    }

    public static NoSuchUserException forGroupRemove (User u, Group g) {
        new NoSuchUserException ("User " + String.valueOf(u.getId()) 
                                 + " is not a member of group "
                                 + String.valueOf(g.getId()));
    }
    public static NoSuchUserException forGroupRemove (User u, Group g,
                                                      Throwable cause) {
        new NoSuchUserException ("User" + String.valueOf(u.getId())
                                 + " is not a member of group "
                                 + String.valueOf(g.getId()));
    }
}
