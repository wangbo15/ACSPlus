package cn.edu.pku.sei.plde.conqueroverfitting.localization.common.junit;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.slf4j.Logger;
import cn.edu.pku.sei.plde.conqueroverfitting.localization.common.container.classic.MetaList;
import cn.edu.pku.sei.plde.conqueroverfitting.localization.common.container.classic.MetaSet;

import java.util.Collection;

import static java.lang.String.format;
import static cn.edu.pku.sei.plde.conqueroverfitting.localization.common.library.LoggerLibrary.logDebug;
import static cn.edu.pku.sei.plde.conqueroverfitting.localization.common.library.LoggerLibrary.loggerFor;


public class TestCasesListener extends RunListener {

    public TestCasesListener() {
        numberOfTests = 0;
        testCases = MetaSet.newHashSet();
        failedTests = MetaSet.newHashSet();
    }

    @Override
    public void testRunStarted(Description description) throws Exception {
        allTests().clear();
        failedTests().clear();
        processBeforeRun();
    }

    @Override
    public void testStarted(Description description) throws Exception {
        incrementNumberOfTests();
        TestCase testCase = addTestCaseTo(allTests(), description);
        logDebug(logger(), format("[#%d. %s started...]", numberOfTests(), testCase.toString()));
        processTestStarted(testCase);
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        Description description = failure.getDescription();
        addTestCaseTo(failedTests(), description);
    }

    @Override
    public void testFinished(Description description) throws Exception {
        TestCase testCase = testCaseOf(description);
        if (failedTests().contains(testCase)) {
            logDebug(logger(), format("[#%d. FAILED]", numberOfTests()));
            processFailedRun(testCase);
        } else {
            logDebug(logger(), format("[#%d. SUCCESS]", numberOfTests()));
            processSuccessfulRun(testCase);
        }
        processTestFinished(testCase);
    }

    @Override
    public void testRunFinished(Result result) throws Exception {
        logTestRunFinished(result);
        processAfterRun();
    }

    protected void processBeforeRun() {
        /* subclassResponsibility */
    }

    protected void processTestStarted(TestCase testCase) {
    	/* subclassResponsibility */
    }

    protected void processSuccessfulRun(TestCase testCase) {
		/* subclassResponsibility */
    }

    protected void processFailedRun(TestCase testCase) {
    	/* subclassResponsibility */
    }

    protected void processTestFinished(TestCase testCase) {
    	/* subclassResponsibility */
    }

    protected void processAfterRun() {
    	/* subclassResponsibility */
    }

    public int numberOfTests() {
        return numberOfTests;
    }

    public int numberOfFailedTests() {
        return failedTests().size();
    }

    public Collection<TestCase> successfulTests() {
        Collection<TestCase> successfulTests = MetaSet.newHashSet(allTests());
        successfulTests.removeAll(failedTests());
        return successfulTests;
    }

    public Collection<TestCase> allTests() {
        return testCases;
    }

    public Collection<TestCase> failedTests() {
        return failedTests;
    }

    protected TestCase testCaseOf(Description description) {
        return TestCase.from(description.getClassName(), description.getMethodName(), numberOfTests());
    }

    private TestCase addTestCaseTo(Collection<TestCase> collection, Description description) {
        TestCase testCase = testCaseOf(description);
        collection.add(testCase);
        return testCase;
    }

    private void incrementNumberOfTests() {
        numberOfTests += 1;
    }

    private void logTestRunFinished(Result result) {
        Collection<String> lines = MetaList.newArrayList();
        lines.add(format("Tests run finished (%d ms)", result.getRunTime()));
        lines.add("<> Total tests run: " + result.getRunCount());
        lines.add("<> Ignored tests: " + result.getIgnoreCount());
        lines.add("<> Failed tests: " + result.getFailureCount());
        for (Failure failure : result.getFailures()) {
            lines.add("~ " + failure.getTestHeader());
            lines.add("[" + failure.getMessage() + "]");
            Throwable exception = failure.getException();
            lines.add(exception.toString());
            for (int i = 0; i <= 5; i += 1) {
                StackTraceElement element = exception.getStackTrace()[i];
                lines.add("    at " + element.toString());
            }
        }
        logDebug(logger(), lines);
    }

    protected Logger logger() {
        return loggerFor(this);
    }

    private int numberOfTests;
    private Collection<TestCase> testCases;
    private Collection<TestCase> failedTests;
}