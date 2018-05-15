package jmx.org.apache.cassandra.two.zero.db.compaction;

import javax.management.openmbean.TabularData;
import java.util.List;
import java.util.Map;

public interface CompactionManagerMBean {

    /** List of running compaction objects. */
    public List<Map<String, String>> getCompactions();

    /** List of running compaction summary strings. */
    public List<String> getCompactionSummary();

    /** compaction history **/
    public TabularData getCompactionHistory();

    /**
     * @see org.apache.cassandra.metrics.CompactionMetrics#pendingTasks
     * @return estimated number of compactions remaining to perform
     */
    @Deprecated
    public int getPendingTasks();

    /**
     * @see org.apache.cassandra.metrics.CompactionMetrics#completedTasks
     * @return number of completed compactions since server [re]start
     */
    @Deprecated
    public long getCompletedTasks();

    /**
     * @see org.apache.cassandra.metrics.CompactionMetrics#bytesCompacted
     * @return total number of bytes compacted since server [re]start
     */
    @Deprecated
    public long getTotalBytesCompacted();

    /**
     * @see org.apache.cassandra.metrics.CompactionMetrics#totalCompactionsCompleted
     * @return total number of compactions since server [re]start
     */
    @Deprecated
    public long getTotalCompactionsCompleted();

    /**
     * Triggers the compaction of user specified sstables.
     * You can specify files from various keyspaces and columnfamilies.
     * If you do so, user defined compaction is performed several times to the groups of files
     * in the same keyspace/columnfamily.
     *
     * @param dataFiles a comma separated list of sstable filename to compact
     */
    public void forceUserDefinedCompaction(String dataFiles);

    /**
     * Stop all running compaction-like tasks having the provided {@code type}.
     * @param type the type of compaction to stop. Can be one of:
     *   - COMPACTION
     *   - VALIDATION
     *   - CLEANUP
     *   - SCRUB
     *   - INDEX_BUILD
     */
    public void stopCompaction(String type);

    /**
     * Returns core size of compaction thread pool
     */
    public int getCoreCompactorThreads();

    /**
     * Allows user to resize maximum size of the compaction thread pool.
     * @param number New maximum of compaction threads
     */
    public void setCoreCompactorThreads(int number);

    /**
     * Returns maximum size of compaction thread pool
     */
    public int getMaximumCompactorThreads();

    /**
     * Allows user to resize maximum size of the compaction thread pool.
     * @param number New maximum of compaction threads
     */
    public void setMaximumCompactorThreads(int number);

    /**
     * Returns core size of validation thread pool
     */
    public int getCoreValidationThreads();

    /**
     * Allows user to resize maximum size of the compaction thread pool.
     * @param number New maximum of compaction threads
     */
    public void setCoreValidationThreads(int number);

    /**
     * Returns size of validator thread pool
     */
    public int getMaximumValidatorThreads();

    /**
     * Allows user to resize maximum size of the validator thread pool.
     * @param number New maximum of validator threads
     */
    public void setMaximumValidatorThreads(int number);
}
