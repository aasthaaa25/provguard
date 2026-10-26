package com.provguard.storage;

import com.provguard.core.RiskResult;
import com.provguard.core.SecurityEvent;

public interface SecurityEventObserver {
    void onEvent(SecurityEvent event, RiskResult result);
}
