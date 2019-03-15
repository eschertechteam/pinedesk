package coop.icc.escher.pinedesk;

public class NoSuchGroupException extends Exception {
    public NoSuchGroupException (long id) {
        this(id, null);
    }
    public NoSuchGroupException (long id, Throwable cause) {
        super("Group " + String.valueOf(id) + " does not exist", cause);
    }
    public NoSuchGroupException (String name) {
        this(name, null);
    }
    public NoSuchGroupException (String name, Throwable cause) {
        super("No groups with name '" + name + "' exist.", cause);
    }
}
