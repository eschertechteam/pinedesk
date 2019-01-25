package coop.icc.escher.pinedesk;

public class UserExistsException extends Exception {
    public UserExistsException (boolean attemptingLink) {
        if (attemptingLink)
            super("Cannot link an existing, unlinked account to Google.");
        else
            super("Cannot unlink an account that has already been linked to Google.");
    }
    public UserExistsException (String email) {
        super("A user with email " + email + " already exists.");
    }
    public UserExistsException (String email, Throwable cause) {
        super("A user with email " + email + " already exists.", cause);
    }
}
