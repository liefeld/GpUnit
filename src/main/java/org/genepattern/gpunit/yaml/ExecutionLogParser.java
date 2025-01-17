package org.genepattern.gpunit.yaml;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;

import org.genepattern.gpunit.GpUnitException;
import org.genepattern.gpunit.ModuleTestObject;

/**
 * Helper class which converts a gp_execution_log.txt file into a gp-unit file in yaml format.
 * 
 * Example execution log,
<pre>
# Created: Mon Apr 23 16:49:21 EDT 2012 by pcarr@broadinstitute.org
# Job: 16558    server:  http://gpdev.broadinstitute.org/gp/
# Module: ConvertLineEndings urn:lsid:broad.mit.edu:cancer.software.genepattern.module.analysis:00002:2
# Parameters: 
#    input.filename = /xchip/gpdev/shared_data/sqa/TestFiles/all_aml_test.cls   /gp/data//xchip/gpdev/shared_data/sqa/TestFiles/all_aml_test.cls
#    output.file = <input.filename_basename>.cvt.<input.filename_extension>
</pre>

Example input file parameters,
 * 1) external url
#    input.filename = ftp://ftp.broadinstitute.org/pub/genepattern/datasets/all_aml/all_aml_train.cls
 * 2) job upload
#    input.filename = /xchip/gpdev/shared_data/sqa/TestFiles/all_aml_test.cls   /gp/data//xchip/gpdev/shared_data/sqa/TestFiles/all_aml_test.cls
 * 2) a job with a previous job result file
#    input.filename = all_aml_test.cvt.res   http://gpdev.broadinstitute.org/gp/jobResults/16556/all_aml_test.cvt.res
 * 3) a job with a server file path file
#    input.filename = /xchip/gpdev/shared_data/sqa/TestFiles/all_aml_test.cls   /gp/data//xchip/gpdev/shared_data/sqa/TestFiles/all_aml_test.cls

 * 
 * 
 * @author pcarr
 */
public class ExecutionLogParser {
    public static ModuleTestObject parse(final File executionLog) throws GpUnitException {
        GpUnitFileParser.checkTestFile(executionLog);
        final File testDir = GpUnitFileParser.initTestDir(executionLog);
        final V v = new V(testDir);
        
        LineNumberReader reader = null;
        try {
            reader = new LineNumberReader(new FileReader(executionLog));
            String line = null;
            while((line = reader.readLine()) != null) {
                v.nextLine(line);
            }
        }
        catch (Throwable t) {
            throw new GpUnitException("File I/O error parsing execution log='"+executionLog+"': "+t.getLocalizedMessage(), t);
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                } 
                catch (IOException e) {
                    throw new GpUnitException("Unexpected I/O error parsing execution log='"+executionLog+"': "+e.getLocalizedMessage(), e);
                }
            }
        }
        final ModuleTestObject testCase = v.getTestCase();
        //set the test name
        if (testCase.getName() == null) {
            String testName = "testName";
            if (executionLog.getParentFile() != null) {
                testName = executionLog.getParentFile().getName() + "/" + executionLog.getName();
            }
            testCase.setName(testName);
        }
        return testCase;
        
    }
    
    //visitor pattern-ish, create a new instance of this object, read each line from the execution log, and build up the model
    private static class V {
        private static class ParamEntry {
            public String name="";
            public Object value=null;
        }
        
        public V() {
            this(new File("."));
        }
        public V(File testDir) {
            testCase.setInputdir(testDir);
        }

        private int lineCount=0;
        private String server="";
        private String job="";
        private ModuleTestObject testCase = new ModuleTestObject();
        
        public ModuleTestObject getTestCase() {
            return testCase;
        }
        
        private void error(String errorMessage) throws GpUnitException {
            throw new GpUnitException(errorMessage);
        }
        
        public void nextLine(String line) throws GpUnitException {
            ++lineCount;
            if (line == null) {
                return;
            }
            if (line.trim().length() == 0) {
                return;
            }
            //strip out leading '#' and whitespace
            if (line.startsWith("#")) {
                line = line.substring(1);
                if (line == null) {
                    return;
                }
                line = line.replaceAll("^\\s+", "");
            }
            
            if (lineCount == 2) {
                //set the server name, helpful for handling input parameters
                //# Job: 16556    server:  http://gpdev.broadinstitute.org/gp/
                String[] tokens = line.split("\\s+");
                if (tokens.length == 4) {
                    job = tokens[1];
                    server = tokens[3];
                }
            }

            if (lineCount < 3) {
                return;
            }
            if (lineCount == 3) {
                //parse the module name and lsid
                //# Module: ConvertLineEndings urn:lsid:broad.mit.edu:cancer.software.genepattern.module.analysis:00002:2
                
                String[] tokens = line.split(" ");
                if (tokens.length < 2 || !tokens[0].startsWith("Module:")) {
                    error("Expecting '# Module: <name> <lsid>' on line 3");
                }
                testCase.setModule(tokens[1]);
                if (tokens.length > 2) {
                    testCase.setModuleLsid(tokens[2]);
                }
                return;
            }
            if (lineCount == 4) {
                //should be '# Parameters: ' line
                if (!line.startsWith("Parameters:")) {
                    error("Expecting '# Parameters: ' on line 4");
                }
                return;
            }
            //else, assume it's another input parameter
            ParamEntry paramEntry = parseParamLine(line);
            if (paramEntry != null) {
                testCase.getParams().put(paramEntry.name, paramEntry.value);
            } 
        }
        
        private ParamEntry parseParamLine(String line) throws GpUnitException {
            //Example input file parameters,
// * 1) external url
//#    input.filename = ftp://ftp.broadinstitute.org/pub/genepattern/datasets/all_aml/all_aml_train.cls
// * 2) job upload
//#    input.filename = all_aml_test.res   /gp/getFile.jsp?job=16556&file=pcarr%40broadinstitute.org_run3943667334821074755.tmp%2Fall_aml_test.res
// * 2) a job with a previous job result file
//#    input.filename = all_aml_test.cvt.res   http://gpdev.broadinstitute.org/gp/jobResults/16556/all_aml_test.cvt.res
// * 3) a job with a server file path file
//#    input.filename = /xchip/gpdev/shared_data/sqa/TestFiles/all_aml_test.cls   /gp/data//xchip/gpdev/shared_data/sqa/TestFiles/all_aml_test.cls
            int idx = line.indexOf(" = ");
            if (idx < 0) {
                error("Error parsing gp_execution_log, line "+lineCount+": Missing ' = '");
            }
            ParamEntry param = new ParamEntry();
            param.name = line.substring(0, idx).trim();
            param.value = line.substring(idx + 3);
            
            //HACK: this assumes exactly three space (' ') characters between each token
            String[] tokens = param.value.toString().split("   ");
            if (tokens.length == 1) {
                //use literal value ... only one token
                return param;
            }
            param.value=tokens[0];
            
            //Note: for these special cases, user must manually download the correct input file into the test directory
            //special-case for job upload files, cast to a File object
            if (tokens[1].startsWith("/gp/getFile.jsp?")) {
                param.value = new File(tokens[0]);
                return param;
            }
            //special-case for previous job result files, cast to a File object
            if (tokens[1].startsWith( server + "jobResults/" )) {
                param.value = new File(tokens[0]);
                return param;
            }
            
            //all other parameters are passed by value, use the first token
            return param;
        }
    }

}
