package org.slowcoders.storm;

import java.sql.SQLException;

public class RestrictedOperation extends SQLException {

    public RestrictedOperation(String message) {
        super(message);
    }
}
