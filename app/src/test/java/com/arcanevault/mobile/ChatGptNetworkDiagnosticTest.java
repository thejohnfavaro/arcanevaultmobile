package com.arcanevault.mobile;

import java.io.IOException;
import java.net.UnknownHostException;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ChatGptNetworkDiagnosticTest {
    @Test public void hostnameFailuresGetNetworkGuidanceWithoutAccountAdvice() {
        String issue=MobileChatGptClient.connectionError(
            new IOException("Unable to connect",new UnknownHostException("auth.openai.com")));
        assertTrue(issue.contains("DNS"));
        assertTrue(issue.contains("Wi-Fi"));
        assertTrue(issue.contains("mobile data"));
        assertFalse(issue.contains("password"));
        assertFalse(issue.contains("reinstall"));
    }
    @Test public void unrelatedFailuresKeepSpecificReason() {
        assertEquals("Forbidden",MobileChatGptClient.connectionError(
            new IOException("Forbidden")));
    }
}