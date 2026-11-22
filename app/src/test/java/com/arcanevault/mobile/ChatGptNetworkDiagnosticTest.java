package com.arcanevault.mobile;

import java.io.IOException;
import java.net.UnknownHostException;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ChatGptNetworkDiagnosticTest {
    @Test public void hostnameFailuresIdentifyHostWithoutAccountAdvice() {
        String issue=MobileChatGptClient.connectionError(
            new IOException("Unable to connect",new UnknownHostException("auth.openai.com")));
        assertTrue(issue.contains("DNS"));
        assertTrue(issue.contains("auth.openai.com"));
        assertTrue(issue.contains("OAuth request"));
        assertFalse(issue.contains("authorization code="));
        assertFalse(issue.contains("password"));
        assertFalse(issue.contains("reinstall"));
    }
    @Test public void wrappedDnsFailuresTriggerFallbackClassification() {
        assertTrue(MobileChatGptClient.hasDnsFailure(
            new IOException("Transport failed",
                new IOException("Wrapped",new UnknownHostException("auth.openai.com")))));
        assertTrue(MobileChatGptClient.hasDnsFailure(
            new UnknownHostException("auth.openai.com")));
        assertFalse(MobileChatGptClient.hasDnsFailure(
            new IOException("HTTP 401")));
        assertFalse(MobileChatGptClient.hasDnsFailure(
            new IOException("DNS error text without DNS exception")));
    }
    @Test public void unrelatedFailuresKeepSpecificReason() {
        assertEquals("Forbidden",MobileChatGptClient.connectionError(
            new IOException("Forbidden")));
    }
}