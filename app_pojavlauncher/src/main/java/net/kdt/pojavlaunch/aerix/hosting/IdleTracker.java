package net.kdt.pojavlaunch.aerix.hosting;

/** Decides when an empty server has been empty long enough to be put to sleep. */
public final class IdleTracker {
    private long emptySince = -1;

    /** Call on every player-count sample. Returns true once the server has had 0 players for [limitMs]. */
    public boolean shouldSleep(int online, long nowMs, long limitMs) {
        if (online > 0) {
            emptySince = -1;
            return false;
        }
        if (emptySince < 0) emptySince = nowMs;
        return nowMs - emptySince >= limitMs;
    }

    /** Forget any empty period, e.g. when the server has just started and nobody had a chance to join. */
    public void reset() {
        emptySince = -1;
    }
}
