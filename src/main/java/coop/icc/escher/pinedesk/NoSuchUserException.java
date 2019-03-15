package coop.icc.escher.pinedesk;

public class NoSuchUserException extends Exception {
    public NoSuchUserException (String reason) {
        super(reason);
    }
    public NoSuchUserException (String reason, Throwable cause) {
        super(reason, cause);
    }

    public static NoSuchUserException forUserLookup (String email) {
        return new NoSuchUserException ("No user with email " + email + " could be found.");
    }
    public static NoSuchUserException forUserLookup (String email, Throwable cause) {
        return new NoSuchUserException ("No user with email " + email + " could be found.", cause);
    }

    public static NoSuchUserException forUserLookup (long id) {
        return new NoSuchUserException ("User " + String.valueOf(id) + " does not exist.");
    }
    public static NoSuchUserException forUserLookup (long id, Throwable cause) {
        return new NoSuchUserException ("User " + String.valueOf(id) + " does not exist." , cause);
    }

    public static NoSuchUserException forGroupRemove (User u, Group g) {
        return new NoSuchUserException ("User " + String.valueOf(u.getId()) 
                                        + " is not a member of group "
                                        + String.valueOf(g.getId()));
    }
    public static NoSuchUserException forGroupRemove (User u, Group g,
                                                      Throwable cause) {
        return new NoSuchUserException ("User" + String.valueOf(u.getId())
                                        + " is not a member of group "
                                        + String.valueOf(g.getId()));
    }
}
