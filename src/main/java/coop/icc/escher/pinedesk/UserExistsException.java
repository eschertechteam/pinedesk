package coop.icc.escher.pinedesk;

public class UserExistsException extends Exception {
    public UserExistsException (boolean attemptingLink) {
        super(attemptingLink ? "Cannot link an existing, unlinked account to Google."
                             : "Cannot unlink an account that has already been linked to Google.");
    }
    public UserExistsException (String reason) {
        super(reason);
    }
    public UserExistsException (String reason, Throwable cause) {
        super(reason, cause);
    }

    public static UserExistsException forGoogleLink (boolean attemptingLink) {
        if (attemptingLink)
            return new UserExistsException ("Cannot link an existing, unlinked account to Google.");
        else
            return new UserExistsException ("Cannot unlink an account that has already been linked to Google.");
    }
    public static UserExistsException forUserCreate (User u) {
        return new UserExistsException ("A user with email " + u.getEmail() + " already exists.");
    }
    public static UserExistsException forUserCreate (User u, Throwable cause) {
        return new UserExistsException ("A user with email " + u.getEmail() + " already exists.", cause);
    }
    public static UserExistsException forGroupAdd (User u, Group g) {
        return new UserExistsException ("User " + String.valueOf(u.getId())
                                        + " is already in group "
                                        + String.valueOf(g.getId()));
    }
    public static UserExistsException forGroupAdd (User u, Group g,
                                                   Throwable cause) {
        return new UserExistsException ("User " + String.valueOf(u.getId()) 
                                        + " is already in group " 
                                        + String.valueOf(g.getId()), cause);
    }
}
