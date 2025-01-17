package org.genepattern.gpunit.exec.rest;

import java.io.File;
import java.net.URI;

import org.genepattern.gpunit.GpUnitException;
import org.genepattern.gpunit.exec.rest.json.JobResultObj;
import org.genepattern.gpunit.BatchModuleTestObject;
import org.genepattern.gpunit.BatchProperties;
import org.junit.Assert;

public class RestClientUtil {

    /*
     * Run a job on the server. If parentJobValidator is not null, then this job is being run on half of the parent job
     * represented by the parent validator (i.e., its a diff job being run to verify the parent job).
     */
    public static void runTest(
            BatchProperties batchProps,
            BatchModuleTestObject testObject,
            JobResultValidatorRest parentJobValidator
        ) throws GpUnitException
    {
        JobRunnerRest runner;
        try {
            runner=new JobRunnerRest(batchProps, testObject.getTestCase());
        }
        catch (GpUnitException e) {
            Assert.fail("Error preparing job submission: "+e.getLocalizedMessage());
            return;
        }
        
        //1) run the job
        final URI jobUri;
        try {
            jobUri=runner.submitJob();
        }
        catch (GpUnitException e) {
            throw e;
        }
        catch (Throwable t) {
            throw new GpUnitException("Unexpected error in JobRunnerRest.submitJob", t);
        }

        //2) poll for job completion
        final long jobCompletionTimeout_ms=1000L*batchProps.getJobCompletionTimeout();
        final JobResultObj jobResult=pollForJobCompletion(runner, jobUri, System.currentTimeMillis(), jobCompletionTimeout_ms);

        //3) validate job results  
        String jobId=jobResult.getJobId();
        Assert.assertNotNull("jobId==null", jobId);
        File jobResultDir=testObject.getJobResultDir(batchProps.getBatchOutputDir(), jobId);
        
        //special-case: optionally save the job.json object to a file
        if (batchProps.getSaveJobJson()) {
            // for debugging, let's put it in the current working dir
            //jobResult.saveJobJsonToDir(new File(System.getProperty("user.dir")));
            // for debugging, let's give it a custom file path
            //jobResult.saveJobJsonToFile(new File("test_job.json"));
            jobResult.saveJobJsonToDir(jobResultDir);
        }
        
        JobResultValidatorRest validator=new JobResultValidatorRest(batchProps, testObject, jobResultDir, parentJobValidator);
        validator.setRestClient(runner);
        validator.setJobStatus(jobResult);
        try {
            if (parentJobValidator != null) {
                validator.validateRemoteDiff(); // validate a diff job which was executed on behalf of the job represented by parentJobValidator
            }
            else if (batchProps.getRunLocalAssertions()) {
                validator.validate();  //  validate the old fashioned way, using local assertions/diffs
            }
            else {
                validator.validateRemote();  // validate using remote diffs
            }
        }
        finally {
            if (jobResult != null) {
                validator.clean();
            }
        }
    }
    
    //helper methods for polling for job completion, could be made generic
    /**
     * When polling for job completion, get the amount of time to wait before the next callback,
     * as a function of the total time elapsed.
     * 
     * @param dateStarted_ms
     * @param timeElapsed_ms
     * @return
     */
    public static long getSleepInterval(long dateStarted_ms, long timeElapsed_ms) {
        long delta_s=(long) Math.floor(timeElapsed_ms/1000L);
        if      (delta_s <=120L ) {  // poll every 2 seconds for the first 2 minutes
            return 2000L;
        }
        else if (delta_s <= 300L) { // poll every 5 seconds for the next 3 minutes (5 minutes total)
            return 5000L;
        }
        else if (delta_s <= 600L) { // poll every 10 seconds for anything less than 10 minutes
            return 10000L;
        }
        else if (delta_s <= 3600L) { // poll every 20 seconds for anything less than an hour
            return 20000L;
        }
        else {                       // poll every minute after an hour 
            return 60000L;
        }
    }
    
    /**
     * Poll for job completion, fail the test if the running time of the job exceeds the given jobTimeout interval.
     * 
     * @param runner
     * @param jobUri
     * @param dateStarted
     * @param jobCompletionTimeout_ms, the maximum amount of time, in milliseconds, to wait for the job to complete.
     * @return
     * @throws InterruptedException
     * @throws GpUnitException
     */
    private static JobResultObj pollForJobCompletion(final JobRunnerRest runner, final URI jobUri, final long timeStarted_ms, final long jobCompletionTimeout_ms) 
    throws GpUnitException
    {
        while(true) { 
            final long timeElapsed=System.currentTimeMillis()-timeStarted_ms;
            final long sleepInterval=getSleepInterval(timeStarted_ms, timeElapsed);
            // timeout check
            if (timeElapsed>jobCompletionTimeout_ms) {
                Assert.fail(String.format("test timed out after %.2f%n seconds, job="+jobUri, (timeElapsed/1000.0)));
            }
            try {
                Thread.sleep(sleepInterval);
            }
            catch (InterruptedException e) {
                Assert.fail(String.format("test interrupted after %.2f%n seconds, job="+jobUri, (timeElapsed/1000.0)));
                Thread.currentThread().interrupt();
                return null;
            }
        
            JobResultObj job=runner.getJobResultObj(jobUri);
            if (job==null) {
                Assert.fail("job==null");
                return null;
            }
            try {
                if (job.isFinished()) {
                    return job;
                }
            }
            catch (Exception e) {
                throw new GpUnitException("Error parsing JSON object from: "+jobUri, e);
            }
        }
    }
    
}
