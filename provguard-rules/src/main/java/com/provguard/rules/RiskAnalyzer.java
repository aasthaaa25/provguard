package com.provguard.rules;

import com.provguard.core.RiskResult;
import com.provguard.core.SecurityEvent;

public interface RiskAnalyzer {
    RiskResult analyze(SecurityEvent event);
}
