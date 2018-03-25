package cn.edu.pku.sei.plde.ACS.fix;

import cn.edu.pku.sei.plde.ACS.assertCollect.Asserts;
import cn.edu.pku.sei.plde.ACS.boundary.BoundaryGenerator;
import cn.edu.pku.sei.plde.ACS.jdtVisitor.LoopVisitor;
import cn.edu.pku.sei.plde.ACS.localization.Suspicious;
import cn.edu.pku.sei.plde.ACS.main.Config;
import cn.edu.pku.sei.plde.ACS.main.TimeLine;
import cn.edu.pku.sei.plde.ACS.trace.ExceptionExtractor;
import cn.edu.pku.sei.plde.ACS.trace.ExceptionVariable;
import cn.edu.pku.sei.plde.ACS.trace.TraceResult;
import cn.edu.pku.sei.plde.ACS.trace.filter.AbandanTrueValueFilter;
import cn.edu.pku.sei.plde.ACS.utils.*;
import cn.edu.pku.sei.plde.ACS.visible.model.VariableInfo;
import com.google.common.collect.Sets;

import java.io.File;
import java.util.*;

import cn.edu.pku.sei.plde.ACS.utils.ShellUtils;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
//import edu.pku.sei.conditon.simple.Invocker;

/**
 * Created by yanrunfa on 16-4-13.
 */
public class SuspiciousFixer {
    public static int FAILED_TEST_NUM = 0;

    public Map<ExceptionVariable, List<String>> boundarysMap = new HashMap<>(); // exceptionVariable to ifStrings
    private Map<VariableInfo, List<String>> trueValues;
    private Map<VariableInfo, List<String>> falseValues;
    private List<TraceResult> traceResults;

    private int ithSuspicous = -1;
    private Suspicious suspicious;
    private List<ExceptionVariable> exceptionVariables;
    private String project;
    private TimeLine timeLine;
    private List<String> methodOneHistory = new ArrayList<>();
    private List<String> methodTwoHistory = new ArrayList<>();
    private List<String> bannedHistory = new ArrayList<>();

    public SuspiciousFixer(int ithSuspicous, Suspicious suspicious, String project, TimeLine timeLine){
        this.ithSuspicous = ithSuspicous;
        this.suspicious = suspicious;
        this.project = project;
        this.timeLine = timeLine;

        //TODO: trace of pradicates by gzoltar
        traceResults = suspicious.getTraceResult(project, timeLine);
        //这里会给 info 添加 varName.null 和 varName.Comparable
        trueValues = AbandanTrueValueFilter.getTrueValue(traceResults, suspicious.getAllInfo());//belongs to succ test
        falseValues = AbandanTrueValueFilter.getFalseValue(traceResults, suspicious.getAllInfo());//belongs to fail test

        if (FAILED_TEST_NUM == 0){
            FAILED_TEST_NUM = TestUtils.getFailTestNumInProject(project);//run defects4j test. 这个 FAILED_TEST_NUM 几次写都很关键
        }
    }

    public boolean mainFixProcessByML(){
        String srcRoot = suspicious._srcPath;
        String testSrcRoot = suspicious._testSrcPath;
        String filePath = suspicious.classname().replace(".", "/");

        if(filePath.contains("$")){
            int dolarIdx = filePath.indexOf('$');
            filePath = filePath.substring(0, dolarIdx) + ".java";
        }else{
            filePath += ".java";
        }

        int line = suspicious.getDefaultErrorLine();

        String jdkEightPath = "/home/nightwish/program_files/jdk1.8.0_111/bin/java";

        String predCmd = jdkEightPath + " -jar Condition.jar " + this.project +
                " " + srcRoot + " " + testSrcRoot + " " + filePath + " " + line + " " + this.ithSuspicous;

        if(runPredictor(predCmd) == false){
            return false;
        }

        List<String> allConditions = ExprUtil.loadConditions(this.project, this.ithSuspicous);

        if(allConditions.size() == 0){
            System.out.println("NO COND PRED: " + predCmd);
            return false;
        }

        ExceptionExtractor extractor = new ExceptionExtractor(suspicious);
        Map<Integer, List<TraceResult>> traceResultWithLine = traceResultClassify(traceResults);
        Map<Integer, List<TraceResult>> firstToGo = new TreeMap<Integer, List<TraceResult>>(new Comparator<Integer>() {
            @Override
            public int compare(Integer integer, Integer t1) {
                return integer.compareTo(t1);
            }
        });
        for (Map.Entry<Integer, List<TraceResult>> entry: traceResultWithLine.entrySet()){
            if (suspicious.tracedErrorLine.contains(entry.getKey())){
                firstToGo.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<Integer, List<TraceResult>> entry: firstToGo.entrySet()){
            if (timeLine.isTimeout()){
                return false;
            }
            if (fixInLineWithPredictor(entry.getKey(), allConditions, extractor, false)){
                return true;
            }
        }
        //why need these lines
        for (Map.Entry<Integer, List<TraceResult>> entry: traceResultWithLine.entrySet()){
            if (firstToGo.containsKey(entry.getKey())){
                continue;
            }
            if (timeLine.isTimeout()){
                return false;
            }
            if (fixInLineWithPredictor(entry.getKey(), allConditions, extractor, true)){
                return true;
            }
        }
        return false;
    }

    public boolean mainFixProcess(){
        ExceptionExtractor extractor = new ExceptionExtractor(suspicious);
        Map<Integer, List<TraceResult>> traceResultWithLine = traceResultClassify(traceResults);// line => its trace
        Map<Integer, List<TraceResult>> firstToGo = new TreeMap<>(new Comparator<Integer>() {//why sort again? Did it have been sorted in traceResultClassify()?
            @Override
            public int compare(Integer integer, Integer t1) {
                return integer.compareTo(t1);
            }
        });
        //先处理有 trace 的 line
        for (Map.Entry<Integer, List<TraceResult>> entry: traceResultWithLine.entrySet()){
            if (suspicious.tracedErrorLine.contains(entry.getKey())){
                firstToGo.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<Integer, List<TraceResult>> entry: firstToGo.entrySet()){// line num => its trace
            if (timeLine.isTimeout()){
                return false;
            }
            int line = entry.getKey();
            List<TraceResult> traceResults = entry.getValue();
            boolean onlyMethod2 = false;
            if (fixInLineWithTraceResult(line, traceResults, extractor, onlyMethod2)){//why call 'fixInLineWithTraceResult' twice ???
                return true;
            }
        }
        for (Map.Entry<Integer, List<TraceResult>> entry: traceResultWithLine.entrySet()){
            if (firstToGo.containsKey(entry.getKey())){
                continue;
            }
            if (timeLine.isTimeout()){
                return false;
            }
            boolean onlyMethod2 = true;
            if (fixInLineWithTraceResult(entry.getKey(), entry.getValue(), extractor, onlyMethod2)){
                return true;
            }
        }
        return false;
    }

    private boolean fixInLineWithPredictor(int line, List<String> allConditions, ExceptionExtractor extractor, boolean onlyMethod2) {
        exceptionVariables = extractor.extractVariableByFailedValues(suspicious,traceResults);
        List<List<ExceptionVariable>> echelons = extractor.getTop2Level();// it seems sort by topological, select top 2 level ?
        Set<String> assertMsgSet = new HashSet<>();
        for (List<ExceptionVariable> echelon: echelons) {// so this loop only process topological top 2 level
            assertMsgSet.addAll(classifyWithAssert(echelon).keySet());
        }

        for(String assertStr: assertMsgSet){
            if (!onlyMethod2) {
                if (timeLine.isTimeout()) {
                    return false;
                }
                String methodOneResult = fixMethodOneML(suspicious, assertStr, allConditions, project, line);
                RecordUtils.printRuntimeMessage(suspicious, project, exceptionVariables, new ArrayList<List<ExceptionVariable>>(), line);
                if (!methodOneResult.equals("")) {
                    RecordUtils.printHistoryBoundary(new HashMap<String, List<String>>(), methodOneResult, suspicious, methodOneHistory, methodTwoHistory, bannedHistory);
                    return true;
                }
            }
            if (timeLine.isTimeout()) {
                return false;
            }
            String methodTwoResult = fixMethodTwoML(suspicious, assertStr, allConditions, project, line);
            RecordUtils.printRuntimeMessage(suspicious, project, exceptionVariables, echelons, line);
            if (!methodTwoResult.equals("")) {
                RecordUtils.printHistoryBoundary(new HashMap<String, List<String>>(), methodTwoResult, suspicious, methodOneHistory, methodTwoHistory, bannedHistory);
                return true;
            }
        }

        return false;
    }

    //TODO: 跟这里！
    private boolean fixInLineWithTraceResult(int line, List<TraceResult> traceResults, ExceptionExtractor extractor, boolean onlyMethod2){
        //为何又重新赋值一次？ SuspiciousFixer 构造方法里有了
        trueValues = AbandanTrueValueFilter.getTrueValue(traceResults, suspicious.getAllInfo());
        //values of failed test-case
        falseValues = AbandanTrueValueFilter.getFalseValue(traceResults, suspicious.getAllInfo());
        //根据trace，把failed test中不一样的值加入怀疑list
        exceptionVariables = extractor.extractVariableByFailedValues(suspicious, traceResults);

        List<List<ExceptionVariable>> echelons = extractor.getTop2Level();// 只取 top 2 level
        for (List<ExceptionVariable> echelon: echelons) {// 对每个 level 上的变量

            //生成 if 条件。 KEY: test_cls # test_mtd # assertLine   VAL: if(conds) 列表
            Map<String, List<String>> boundarys = generateBoundarys(echelon);
            if (!onlyMethod2){
                if (timeLine.isTimeout()){
                    return false;
                }
                Map<String, List<String>> boundaryCopy = deepCopyBoundarys(boundarys);
                String methodOneResult = fixMethodOne(suspicious, boundaryCopy, project, line);
                RecordUtils.printRuntimeMessage(suspicious, project, exceptionVariables, echelons, line);
                if (!methodOneResult.equals("")) {
                    RecordUtils.printHistoryBoundary(boundarys, methodOneResult, suspicious, methodOneHistory, methodTwoHistory, bannedHistory);
                    return true;
                }
            }
            if (timeLine.isTimeout()){
                return false;
            }
            AngelicFilter filter = new AngelicFilter(suspicious, project); // filter what?
            Map<String, List<String>> boundaryCopy = deepCopyBoundarys(boundarys);
            String methodTwoResult = fixMethodTwo(suspicious, filter.filter(line,boundaryCopy,traceResults), project, line, false);
            RecordUtils.printRuntimeMessage(suspicious, project, exceptionVariables, echelons, line);
            if (!methodTwoResult.equals("")) {
                RecordUtils.printHistoryBoundary(boundarys, methodTwoResult, suspicious, methodOneHistory, methodTwoHistory, bannedHistory);
                return true;
            }

        }// end for (List<ExceptionVariable> echelon: echelons)

        return false;
    }


    private Map<String, List<String>> generateBoundarys(List<ExceptionVariable> exceptionVariables){
        Map<String, List<String>> assertToBoundarysMap = new HashMap<>();
        Map<String, List<ExceptionVariable>> assertExceptVarMap = classifyWithAssert(exceptionVariables);//KEY：assert 的编号，VAL：异常值列表
        for (Map.Entry<String, List<ExceptionVariable>> assertEchelon : assertExceptVarMap.entrySet()) {
            List<String> ifStrings = getIfStrings(assertEchelon.getValue()); //TODO: 重要！生成 if 条件
            if (ifStrings.size()<= 0){
                continue;
            }
            assertToBoundarysMap.put(assertEchelon.getKey(), ifStrings);
        }
        return assertToBoundarysMap;
    }

    private Map<String, List<String>> deepCopyBoundarys(Map<String, List<String>> boundarys){
        Map<String, List<String>> copy = new HashMap<>();
        for (Map.Entry<String, List<String>> entry: boundarys.entrySet()){
            String key = entry.getKey();
            List<String> value = new ArrayList<>(entry.getValue());
            copy.put(key,value);
        }
        return copy;
    }

    private Map<Integer, List<TraceResult>> traceResultClassify(List<TraceResult> traceResults){
        Map<Integer, List<TraceResult>> result = new TreeMap<Integer, List<TraceResult>>(new Comparator<Integer>() {
            @Override
            public int compare(Integer integer, Integer t1) {
                return integer.compareTo(t1);
            }
        });
        for (TraceResult traceResult: traceResults){
            if (!result.containsKey(traceResult._traceLine)){
                List<TraceResult> results = new ArrayList<>();
                results.add(traceResult);
                result.put(traceResult._traceLine, results);
            }
            else {
                result.get(traceResult._traceLine).add(traceResult);
            }
        }
        return result;
    }


    private Map<String, List<ExceptionVariable>> classifyWithAssert(List<ExceptionVariable> exceptionVariables){//divide exceVaris into exceptionVariable.getAssertMessage()
        Map<String, List<ExceptionVariable>> result = new HashMap<>();
        for (ExceptionVariable exceptionVariable: exceptionVariables){
            if (!result.containsKey(exceptionVariable.getAssertMessage())){//exceptionVariable.getAssertMessage() : org.apache.commons.math.distribution.NormalDistributionTest#testMath280#169
                List<ExceptionVariable> variables = new ArrayList<>();
                variables.add(exceptionVariable);
                result.put(exceptionVariable.getAssertMessage(),variables);
            }
            else {
                result.get(exceptionVariable.getAssertMessage()).add(exceptionVariable);//if-else 只是个插入 map 的操作而已
            }
        }
        return result;
    }


    private List<String> getIfStrings(List<ExceptionVariable> exceptionVariables){
        List<String> returnList = new ArrayList<>();

//        Map<ExceptionVariable, ArrayList<String>> result = new HashMap<>();
        for (ExceptionVariable exceptionVariable: exceptionVariables){
            List<String> boundarys = getBoundary(exceptionVariable);    //TODO: 重要！生成 if 条件

            for (String condition: boundarys){
                String ifString = MathUtils.replaceSpecialNumber(getIfStatementFromBoundary(condition)); // statement 变成 if(condition)
                if (!returnList.contains(ifString) && !ifString.equals("")){
                    returnList.add(ifString);
                }
            }
        }
        return returnList;
    }

    private String getIfStatementFromBoundary(String boundary){
        return "if ("+ boundary+")";
    }

    private List<String> getBoundary(ExceptionVariable exceptionVariable){
        if (!boundarysMap.containsKey(exceptionVariable)){
            long downLoadStartTime = System.currentTimeMillis();
            //TODO: 重要！生成 if 条件
            List<String> boundarys = BoundaryGenerator.generate(suspicious,exceptionVariable, trueValues, falseValues, project);
            timeLine.addDownloadTime(System.currentTimeMillis()-downLoadStartTime);
            if(timeLine.isTimeout()){
                return new ArrayList<>();
            }
            boundarysMap.put(exceptionVariable, boundarys);
        }
        return boundarysMap.get(exceptionVariable);
    }


    public String fixMethodTwo(Suspicious suspicious, Map<String, List<String>> ifStrings, String project, int errorLine, boolean debug){
        if (ifStrings.size() == 0) {
            return "";
        }
        MethodTwoFixer fixer = new MethodTwoFixer(suspicious);
        if (fixer.fix(ifStrings, Sets.newHashSet(errorLine), project, debug)) {
            return fixer.correctPatch + "[" + fixer.correctStartLine + "," + fixer.correctEndLine + "]";
        }
        methodTwoHistory = fixer.triedPatch;

        return "";
    }

    private String fixMethodTwoML(Suspicious suspicious, String assertMsg, List<String> allConditions, String project, int line) {
        if (allConditions.size() == 0) {
            return "";
        }
        MethodTwoFixer methodTwoFixer = new MethodTwoFixer(suspicious);
        Map<String, List<String>> tmpMap = new HashMap<>();
        tmpMap.put(assertMsg, allConditions);
        if (methodTwoFixer.fixML(tmpMap, Sets.newHashSet(line), project, false)) {
            return methodTwoFixer.correctPatch + "[" + methodTwoFixer.correctStartLine + "," + methodTwoFixer.correctEndLine + "]";
        }
        methodTwoHistory = methodTwoFixer.triedPatch;
        return "";
    }

    public String fixMethodOne(Suspicious suspicious,Map<String, List<String>> assertToBoundarysMap, String project, int errorLine) {
        if (assertToBoundarysMap.size() == 0){
            return "";
        }
        MethodOneFixer methodOneFixer = new MethodOneFixer(suspicious, project);
        for (Map.Entry<String, List<String>> entry: assertToBoundarysMap.entrySet()){

            String assertKey = entry.getKey(); // Test Class # Test Method # Assert ID

            String testClassName = assertKey.split("#")[0];
            String testMethodName = assertKey.split("#")[1];
            int assertLine = Integer.valueOf(assertKey.split("#")[2]);

            if (assertLine == -1){
                testClassName = suspicious._failTests.get(0).split("#")[0];
                testMethodName = suspicious._failTests.get(0).split("#")[1];

                if (suspicious._assertsMap.containsKey(suspicious._failTests.get(0))){
                    if (suspicious._assertsMap.get(suspicious._failTests.get(0))._errorAssertLines.size()>0){
                        assertLine = suspicious._assertsMap.get(suspicious._failTests.get(0))._errorAssertLines.get(0);
                    }
                }
            }

            String testCode = FileUtils.getCodeFromFile(suspicious._testSrcPath, testClassName);
            if (!CodeUtils.getLineFromCode(testCode, assertLine).contains("assert")){// error test stmt is 'assert'?
                assertLine = -1;    //对于 Math-25 这样是没有 assert 的
            }

            ReturnCapturer fixCapturer = new ReturnCapturer(suspicious._classpath,
                    suspicious._srcPath,
                    suspicious._testClasspath,
                    suspicious._testSrcPath);

            String ifBody = fixCapturer.getFixFrom(testClassName,
                    testMethodName,
                    assertLine,
                    suspicious.classname(),
                    suspicious.functionnameWithoutParam());//return or throw

            // errorLine 不为 0，直接用errorLine，否则用 suspicious._errorLineMap 里面是 testMethod 为 key，源码 if 之后的行号。所以看模板I的patch，大多在if内下一行
            List<Integer> patchLineList = (errorLine != 0) ? Arrays.asList(errorLine) : suspicious._errorLineMap.get(testClassName+"#"+testMethodName);    // insert line number?
            List<String> ifStatementList = entry.getValue();

            int patchLine = patchLineList.get(0);
            banIfStmtsByNLP(ifStatementList, ifBody, patchLine); //慢！

            if (ifStatementList.size() == 0){
                return "";
            }
            if (suspicious._isConstructor && ifBody.contains("return")){//构造方法不能用 return 的patch
                continue;
            }
            if (ifBody.equals("")){
                continue;
            }
            List<String> ifPreds = entry.getValue();
            Patch patch = new Patch(testClassName, testMethodName, suspicious.classname(), patchLineList, ifPreds, ifBody);//注意 Patch 包含某行的全部 patchString

            Asserts asserts = suspicious._assertsMap.get(testClassName+"#"+testMethodName); // important! value been put in 'VariableTracer.trace()'
            AssertComment comment = new AssertComment(asserts, assertLine);
            comment.comment();  //看样子像是注释其他 assert ？ 待确认
            boolean result = methodOneFixer.addPatch(patch);
            comment.uncomment();
            if (result){
                break;
            }
        }
        int finalErrorNums = methodOneFixer.fix();
        methodOneHistory = methodOneFixer.triedPatch;
        if (finalErrorNums != -1){
            return methodOneFixer._patches.get(0)._patchString.get(0);
        }
        return "";
    }


    /**
     * ban 的机制尚不明确
     */
    private void banIfStmtsByNLP(List<String> ifStatementList, String ifBody, int patchLine){
        List<String> bannedStatementList = new ArrayList<>();
        /* 运行很慢的循环，内部用 NLP 的主语分析 */
        for (String statemnt : ifStatementList) {
            if (ifStringFilter(statemnt, ifBody, patchLine)) {
                bannedStatementList.add(statemnt);
            }
        }
        ifStatementList.removeAll(bannedStatementList);
        bannedHistory.addAll(bannedStatementList);
    }

    private String fixMethodOneML(Suspicious suspicious, String assertStr, List<String> ifStrings, String project, int errorLine){
        if (ifStrings.size() == 0){
            return "";
        }
        ReturnCapturer fixCapturer = new ReturnCapturer(suspicious._classpath,suspicious._srcPath, suspicious._testClasspath, suspicious._testSrcPath);
        MethodOneFixer methodOneFixer = new MethodOneFixer(suspicious, project);

        String testClassName = assertStr.split("#")[0];
        String testMethodName = assertStr.split("#")[1];
        int assertLine = Integer.valueOf(assertStr.split("#")[2]);
        Asserts asserts = suspicious._assertsMap.get(testClassName+"#"+testMethodName);
        AssertComment comment = new AssertComment(asserts, assertLine);
        if (assertLine == -1){
            testClassName = suspicious._failTests.get(0).split("#")[0];
            testMethodName = suspicious._failTests.get(0).split("#")[1];
            if (suspicious._assertsMap.containsKey(suspicious._failTests.get(0))){
                if (suspicious._assertsMap.get(suspicious._failTests.get(0))._errorAssertLines.size()>0){
                    assertLine = suspicious._assertsMap.get(suspicious._failTests.get(0))._errorAssertLines.get(0);
                }
            }
        }
        if (!CodeUtils.getLineFromCode(FileUtils.getCodeFromFile(suspicious._testSrcPath, testClassName),assertLine).contains("assert")){// error test stmt is 'assert'?
            assertLine = -1;
        }
        String fixString = fixCapturer.getFixFrom(testClassName, testMethodName, assertLine, suspicious.classname(), suspicious.functionnameWithoutParam());//return or throw
        List<Integer> patchLine = errorLine != 0? Arrays.asList(errorLine) : suspicious._errorLineMap.get(testClassName+"#"+testMethodName);    // insert line number?
        if (suspicious._isConstructor && fixString.contains("return") || fixString.equals("")){
            return "";
        }
        Patch patch = new Patch(testClassName, testMethodName, suspicious.classname(), patchLine, ifStrings, fixString);
        comment.comment();//backup what?
        boolean result = methodOneFixer.addPatch(patch);
        comment.uncomment();

        int finalErrorNums = methodOneFixer.fix();
        methodOneHistory = methodOneFixer.triedPatch;
        if (finalErrorNums != -1){
            return methodOneFixer._patches.get(0)._patchString.get(0);
        }
        return "";
    }

    private boolean ifStringFilter(String ifStatement,String fixString, int patchLine){
        DocumentBaseFilter filter = new DocumentBaseFilter(suspicious);
        if (filter.filterWithAnnotation(ifStatement,fixString, patchLine)){
            return true;
        }
        if (ifStatement.contains(">") || ifStatement.contains("<")){
            if (fixString.startsWith("return") && !VariableUtils.isExpression(fixString.split(" ")[1])){    //这里对 return 的单独处理是为什么？
                return true;
            }
        }
        return false;
    }

    private boolean runPredictor(String predCmd){
        List<String> errMsgs = ShellUtils.runCmd(predCmd, null);
        for(String s : errMsgs){
            if(s.startsWith("PREDICTOR ERROR!")){
                return false;
            }
        }
        return true;
    }
}