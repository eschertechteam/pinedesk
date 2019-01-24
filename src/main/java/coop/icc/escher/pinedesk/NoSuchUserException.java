package coop.icc.escher.pinedesk;

public class NoSuchUserException extends Exception {
    public NoSuchUserException (String email) {
        super("The user with email " + email " could not be found.");
    }
}
