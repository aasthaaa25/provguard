package com.provguard.storage;

import com.provguard.core.Decision;
import com.provguard.core.RiskResult;

import java.util.concurrent.atomic.AtomicLong;

public final class EventStatistics {
    private final AtomicLong captured = new AtomicLong();
    private final AtomicLong logged = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong allowed = new AtomicLong();
    private final AtomicLong alerted = new AtomicLong();
    private final AtomicLong blocked = new AtomicLong();
    private final AtomicLong mlUnavailable = new AtomicLong();

    public void onCaptured(RiskResult result, boolean queued) {
        captured.incrementAndGet();
        if (!queued) {
            dropped.incrementAndGet();
        }
        if (result.enforced()) {
            blocked.incrementAndGet();
        } else {
            switch (result.decision()) {
                case ALLOW -> allowed.incrementAndGet();
                case ALERT, BLOCK -> alerted.incrementAndGet();
            }
        }
        if (!"AVAILABLE".equals(result.modelStatus())) {
            mlUnavailable.incrementAndGet();
        }
    }

    public void onLogged() {
        logged.incrementAndGet();
    }

    public void reviseModel(RiskResult fast, RiskResult enriched) {
        boolean fastMissing = !"AVAILABLE".equals(fast.modelStatus());
        boolean enrichedMissing = !"AVAILABLE".equals(enriched.modelStatus());
        if (fastMissing && !enrichedMissing) {
            mlUnavailable.decrementAndGet();
        } else if (!fastMissing && enrichedMissing) {
            mlUnavailable.incrementAndGet();
        }
    }

    public long captured() {
        return captured.get();
    }

    public long logged() {
        return logged.get();
    }

    public long dropped() {
        return dropped.get();
    }

    public long allowed() {
        return allowed.get();
    }

    public long alerted() {
        return alerted.get();
    }

    public long blocked() {
        return blocked.get();
    }

    public long mlUnavailable() {
        return mlUnavailable.get();
    }

    public String summary() {
        return "captured=" + captured()
                + " logged=" + logged()
                + " dropped=" + dropped()
                + " allowed=" + allowed()
                + " alerted=" + alerted()
                + " blocked=" + blocked()
                + " mlUnavailable=" + mlUnavailable();
    }
}
