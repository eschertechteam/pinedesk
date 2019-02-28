package coop.icc.escher.pinedesk;

class NoSuchTicketException extends Exception {
    public NoSuchTicketException (long id) {
        this(id, null);
    }
    public NoSuchTicketException (long id, Throwable cause) {
        super("Ticket " + String.valueOf(id) + " does not exist", cause);
    }
}
