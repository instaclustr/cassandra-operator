//package com.instaclustr.backup;
//
//import com.google.common.collect.ImmutableList;
//import com.google.common.util.concurrent.AbstractScheduledService;
//import com.google.common.util.concurrent.ServiceManager;
//import CommitLogBackupTask;
//import FilesUploader;
//import GlobalLock;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.concurrent.TimeUnit;
//
//public class CommitLogBackupApplication extends Application {
//    private static final Logger logger = LoggerFactory.getLogger(CommitLogBackupApplication.class);
//
//    public static void main(String[] args) throws Exception {
//        final CommitLogBackupArguments arguments = new CommitLogBackupArguments("commitlog-com.instaclustr.backup", System.err);
//        arguments.parseArguments(args);
//
//        GlobalLock globalLock = new GlobalLock(arguments.sharedContainerPath.toString());
//
//
//        runServices(new ServiceManager(ImmutableList.of(
//                new AbstractScheduledService() {
//                    @Override
//                    protected void runOneIteration() throws Exception {
//                        new CommitLogBackupTask(arguments.sharedContainerPath,
//                                arguments.commitLogArchiveOverride,
//                                null,
//                                new FilesUploader(arguments),
//                                arguments).call();
//
//                        logger.info("CommitLog Backup completed successfully.");
//                    }
//
//                    @Override
//                    protected Scheduler scheduler() {
//                        return Scheduler.newFixedDelaySchedule(5, 5, TimeUnit.MINUTES);
//                    }
//                }
//        )));
//
//        }
//}
