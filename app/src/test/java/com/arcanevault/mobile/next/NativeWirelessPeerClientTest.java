package com.arcanevault.mobile.next;

import static org.junit.Assert.*;
import org.junit.Test;

public final class NativeWirelessPeerClientTest {
    @Test public void permitsOnlyPrivateLansWithExactPeerToken()throws Exception {
        String token="a".repeat(48);
        assertEquals("192.168.1.3",NativeWirelessPeerClient.validate(
            "http://192.168.1.3:34001/arcane-peer/"+token).getHost());
        assertEquals("10.2.0.8",NativeWirelessPeerClient.validate(
            "http://10.2.0.8:3456/arcane-peer/"+token).getHost());
        assertEquals("172.16.2.8",NativeWirelessPeerClient.validate(
            "http://172.16.2.8:9876/arcane-peer/"+token).getHost());
        for(String input:new String[]{
            "https://192.168.1.3:34001/arcane-peer/"+token,
            "http://127.0.0.1:34001/arcane-peer/"+token,
            "http://8.8.8.8:34001/arcane-peer/"+token,
            "http://evil.com:34001/arcane-peer/"+token,
            "http://192.168.1.3:34001/admin/"+token,
            "http://192.168.1.3:34001/arcane-peer/"+token+"?x=1",
            "http://192.168.1.3:34001/arcane-peer/abc",
            "http://192.168.1.3/arcane-peer/"+token,
            "http://192.168.1.3:34001/arcane-peer/"+token+"#fragment"
        }) {
            try {NativeWirelessPeerClient.validate(input);fail("Accepted unsafe link: "+input);}
            catch(IllegalArgumentException expected){} 
        }
    }
}
