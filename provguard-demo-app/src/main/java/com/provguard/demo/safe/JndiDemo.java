package com.provguard.demo.safe;

import com.provguard.core.exception.ProvGuardBlockedException;
import javax.naming.InitialContext;
import javax.naming.NamingException;

public final class JndiDemo {
    public static void main(String[] args) {
        boolean triggerBlock = args.length > 0 && "blocked".equalsIgnoreCase(args[0]);
        System.out.println("Starting JNDI Demo (triggerBlock=" + triggerBlock + ")");
        try {
            InitialContext ctx = new InitialContext();
            if (triggerBlock) {
                System.out.println("Attempting untrusted JNDI lookup: ldap://127.0.0.1:1389/Exploit");
                ctx.lookup("ldap://127.0.0.1:1389/Exploit");
            } else {
                System.out.println("Attempting local safe JNDI lookup: java:comp/env/myConfig");
                try {
                    ctx.lookup("java:comp/env/myConfig");
                } catch (NamingException ignored) {
                    System.out.println("Local JNDI lookup handled safely.");
                }
            }
        } catch (ProvGuardBlockedException blocked) {
            System.out.println("ProvGuard blocked untrusted JNDI lookup: " + blocked.getMessage());
        } catch (Throwable error) {
            System.out.println("JNDI call completed with: " + error.getMessage());
        }
    }
}
