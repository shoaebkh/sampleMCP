package org.shoaeb.mcp.salesforce;

/** Thrown when an Apex REST call returns 404 — e.g. no matching Account. */
public class SalesforceNotFoundException extends RuntimeException {

    public SalesforceNotFoundException(String message) {
        super(message);
    }
}
