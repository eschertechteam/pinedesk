package coop.icc.escher.pinedesk;

class GroupExistsException extends Exception {
    public GroupExistsException (Group g) { this(g, null); }
    public GroupExistsException (Group g, Throwable cause) {
        super("A group with name " + g.getName() + " already exists.", cause);
    }
}
