package com.provguard.storage;

import com.provguard.core.RiskResult;
import com.provguard.core.SecurityEvent;

public interface EventWriter {
    void write(SecurityEvent event, RiskResult result);

    void close();
}
