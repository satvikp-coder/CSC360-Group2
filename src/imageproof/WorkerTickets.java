package imageproof;

/** EDT-owned generation counters; an older load or analysis can never publish newer state. */
final class WorkerTickets {
    private long load, analysis;
    long nextLoad() { analysis++; return ++load; }
    long nextAnalysis() { return ++analysis; }
    boolean currentLoad(long ticket) { return ticket == load; }
    boolean currentAnalysis(long ticket) { return ticket == analysis; }
}
