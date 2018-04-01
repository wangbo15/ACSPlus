package cn.edu.pku.sei.plde.ACS.localization;

import cn.edu.pku.sei.plde.ACS.assertCollect.Asserts;
import cn.edu.pku.sei.plde.ACS.main.TimeLine;
import cn.edu.pku.sei.plde.ACS.trace.TraceResult;
import cn.edu.pku.sei.plde.ACS.trace.VariableTracer;
import cn.edu.pku.sei.plde.ACS.utils.*;
import cn.edu.pku.sei.plde.ACS.visible.MethodCollect;
import cn.edu.pku.sei.plde.ACS.visible.VariableCollect;
import cn.edu.pku.sei.plde.ACS.visible.model.MethodInfo;
import cn.edu.pku.sei.plde.ACS.visible.model.VariableInfo;
import javassist.NotFoundException;


import java.io.*;
import java.util.*;

/**
 * Created by yanrunfa on 16/2/25.
 */
public class Suspicious implements Serializable{
    private static final int MAX_TRACE_RESULT_COUNT = 50;
    private static final int MAX_TRACE_TEST_COUNT = 10;


    public final String _classpath;
    public List<String> _libPath = new ArrayList<>();
    public final String _testClasspath;
    private final String _classname;
    public final String _function;
    public final String _srcPath;
    public final String _testSrcPath;
    public final boolean _isConstructor;
    public final double _suspiciousness;
    public final List<String> _tests;
    public final List<String> _failTests;
    public final List<String> _lines;
    private List<VariableInfo> _variableInfo;
    public List<Integer> tracedErrorLine = new ArrayList<>();
    public Map<String, Asserts> _assertsMap = new HashMap<>();
    public Map<String, List<Integer>> _errorLineMap = new HashMap<>();
    private int _defaultErrorLine = -1;
    public int trueMethodCallNumFromTest = 0;

    public Suspicious(String classpath, String testClasspath, String srcPath, String testSrcPath, String classname, String function){
        this(classpath,
                testClasspath,
                srcPath,
                testSrcPath,
                classname,
                function,
                0,
                new ArrayList<String>(),
                new ArrayList<String>(),
                new ArrayList<String>());
    }

    private Suspicious(String classpath,
                      String testClasspath,
                      String srcPath,
                      String testSrcPath,
                      String classname,
                      String function,
                      double suspiciousness,
                      List<String> tests,
                      List<String> failTests,
                      List<String> lines) {
        this(classpath, testClasspath,srcPath, testSrcPath, classname, function, suspiciousness, tests, failTests, lines, new ArrayList<String>());
    }


    public Suspicious(String classpath,
                      String testClasspath,
                      String srcPath,
                      String testSrcPath,
                      String classname,
                      String function,
                      double suspiciousness,
                      List<String> tests,
                      List<String> failTests,
                      List<String> lines,
                      List<String> libPaths){
        _classpath = classpath;
        _testClasspath = testClasspath;
        _classname = classname;
        _srcPath = srcPath;
        _testSrcPath = testSrcPath;
        _libPath = libPaths;
        _function = function;
        _suspiciousness = suspiciousness;
        _tests = new ArrayList(new HashSet(tests));
        _failTests = new ArrayList(new HashSet(failTests));
        _lines = lines;
        _isConstructor = CodeUtils.isConstructor(_classname, _function);
    }

    public int errorAssertNums(){
        int errAssertNum = 0;
        for (Asserts asserts: _assertsMap.values()){
            errAssertNum += asserts.errorAssertNum();
        }
        return errAssertNum;
    }

    public int trueAssertNums(){
        return trueMethodCallNumFromTest;
    }

    public int trueTestNums(){
        return _tests.size() - _failTests.size();
    }

    public Set<Integer> errorLines(){
        if (_assertsMap.size() == 0){
            return new HashSet<>();
        }
        Set<Integer> result = new HashSet<>();
        for (Map.Entry<String, List<Integer>> entry: _errorLineMap.entrySet()){
            result.addAll(entry.getValue());
        }
        return result;
    }

    public void setErrorLines(Set<Integer> lines){
        this._lines.clear();
        for (int line: lines){
            this._lines.add(String.valueOf(line));
        }
    }


    public int getDefaultErrorLine() {
        //如果是构造函数,则重新寻找错误行
        if (_classname.substring(_classname.lastIndexOf(".") + 1).equals(_function.substring(0, _function.indexOf('(')))) {
            for (String test : _tests) {
                try {
                    String testTrace = TestUtils.getTestTrace(_classpath, _testClasspath, test.split("#")[0], test.split("#")[1]);
                    if (testTrace == null){
                        continue;
                    }
                    for (String line : testTrace.split("\n")) {
                        if (line.contains(classname()+".") && line.contains("(") && line.contains(")")) {
                            if (_defaultErrorLine!= -1){
                                continue;
                            }
                            _defaultErrorLine = Integer.valueOf(line.substring(line.indexOf("(") + 1, line.indexOf(")")).split(":")[1]);
                        }
                    }
                }catch(NotFoundException e){
                    e.printStackTrace();
                }
            }
        }
        if (_defaultErrorLine == -1) {
            for (String line : _lines) {
                if (_defaultErrorLine < Integer.valueOf(line)) {
                    _defaultErrorLine = Integer.valueOf(line);
                }
            }
        }

        return _defaultErrorLine;
    }

    public String classname(){
        String className =  _classname.trim().replace(";","");
        if (className.contains("$")){
            className = className.substring(0, className.indexOf("$"));
        }
        return className;
    }

    public String functionname(){
        if (_function.contains("[")){
            return _function.substring(0, _function.lastIndexOf("["));
        }
        return _function.replace("\\","");
    }

    public String functionnameWithoutParam(){
        return _function.substring(0, _function.indexOf("("));
    }

    public List<String> getTestClasses(){
        List<String> classes = new ArrayList<String>();
        for (String test: _tests){
            if (!test.contains("#")){
                continue;
            }
            classes.add(test.split("#")[0]);
        }
        return classes;
    }

    private String getJavaSrcFilePath(String srcRootPath){
        String classname = classname();
        if (_classname.contains("$")){
            classname = _classname.substring(0, _classname.lastIndexOf('$'));
        }
        String result =  srcRootPath + System.getProperty("file.separator") + classname.replace(".",System.getProperty("file.separator")) + ".java";
        return result.replace(" ","").replace("//","/");
    }


    private String getSrcPackagePath(String classSrcRootPath){
        String classSrcPath = getJavaSrcFilePath(classSrcRootPath);
        return classSrcPath.substring(0,classSrcPath.lastIndexOf(System.getProperty("file.separator"))).replace(" ","").replace("//","/");
    }


    public List<VariableInfo> getAllInfo(){
        return InfoUtils.filterBannedVariable(_variableInfo);
    }

    /**
     * 重要方法
     */
    public List<VariableInfo> getVariableInfo(int line){
        String srcRoot = this._srcPath;
        if (_variableInfo == null){
            _variableInfo = new ArrayList<>();
        }
        List<VariableInfo> variableInfos = new ArrayList<VariableInfo>();
        String classSrcPath = getJavaSrcFilePath(srcRoot);
        VariableCollect variableCollect = VariableCollect.GetInstance(getSrcPackagePath(srcRoot));
        List<VariableInfo> locals = variableCollect.getVisibleLocalInMethodList(classSrcPath, line);
        if (locals.size() == 0){
            locals = variableCollect.getVisibleLocalInMethodList(classSrcPath, line-1);
        }
        for (VariableInfo local: locals){
            local.isLocalVariable = true;
            List<VariableInfo> subVariableInfo = InfoUtils.getSubInfoOfComplexVariable(local,srcRoot, classSrcPath);
            for (VariableInfo info: subVariableInfo){
                info.isLocalVariable = true;
            }
            variableInfos.addAll(subVariableInfo);
        }
        variableInfos.addAll(locals);
        variableCollect = VariableCollect.GetInstance(getSrcPackagePath(srcRoot));
        List<VariableInfo> parameters = variableCollect.getVisibleParametersInMethodList(classSrcPath, line);
        for (VariableInfo param: parameters){
            param.isParameter = true;
            List<VariableInfo> subVariableInfo = InfoUtils.getSubInfoOfComplexVariable(param,srcRoot, classSrcPath);
            for (VariableInfo info: subVariableInfo){
                info.isParameter = true;
            }
            variableInfos.addAll(subVariableInfo);
        }
        variableInfos.addAll(parameters);

        if (!_isConstructor){
            variableCollect = VariableCollect.GetInstance(getSrcPackagePath(srcRoot));
            LinkedHashMap<String, ArrayList<VariableInfo>> classvars = variableCollect.getVisibleFieldInAllClassMap(classSrcPath);
            if (classvars.containsKey(classSrcPath)){
                List<VariableInfo> fields = classvars.get(classSrcPath);
                // remove the static variable when in a static method.
                List<VariableInfo> staticVars = new ArrayList<>();
                if (MethodCollect.checkIsStaticMethod(getJavaSrcFilePath(srcRoot),_function.substring(0, _function.indexOf("(")))){
                    for (VariableInfo info: fields){
                        if (!info.isStatic){
                            staticVars.add(info);
                        }
                    }
                }
                String code = FileUtils.getCodeFromFile(_srcPath, _classname);
                List<VariableInfo> innerVars = new ArrayList<>();
                if (!MethodUtils.isInnerMethod(code,functionnameWithoutParam())){
                    for (VariableInfo info: fields){
                        if (!CodeUtils.hasField(code, info.variableName)){
                            innerVars.add(info);
                        }
                    }
                }
                fields.removeAll(staticVars);
                fields.removeAll(innerVars);
                for (VariableInfo field: fields){
                    field.isFieldVariable = true;
                }
                variableInfos.addAll(fields);
            }
        }
        List<VariableInfo> addonVariableInfos = addonVariableInfos(line, parameters);
        for (VariableInfo info: addonVariableInfos){
            boolean isAdd = true;
            for (VariableInfo existsInfo: variableInfos){
                if (info.variableName.equals(existsInfo.variableName) && info.isExpression){
                    existsInfo.isExpression = true;
                    isAdd = false;
                    break;
                }
            }
            if (isAdd){
                variableInfos.add(info);
            }
        }
        _variableInfo.removeAll(variableInfos);//??
        _variableInfo.addAll(variableInfos);
        _variableInfo = InfoUtils.filterBannedVariable(_variableInfo);
        return InfoUtils.filterBannedVariable(variableInfos);
    }

    private List<VariableInfo> addonVariableInfos(int line, List<VariableInfo> parameters){
        List<VariableInfo> infos = new ArrayList<>();
        String code = FileUtils.getCodeFromFile(_srcPath, _classname);
        //"this" variable
        VariableInfo thisInfo = new VariableInfo("this", null, false, classname().substring(classname().lastIndexOf(".")+1));
        thisInfo.isAddon = true;
        infos.add(thisInfo);
        //if expression
        if (LineUtils.isLineInIf(code, line)){
            for (int i=line; i>0; i--){
                String lineIString = CodeUtils.getLineFromCode(code, i);
                if (LineUtils.isIfAndElseIfLine(lineIString)){
                    List<VariableInfo> ifAddon = InfoUtils.getVariableInIfStatement(lineIString, functionnameWithoutParam());
                    if (ifAddon != null){
                        infos.addAll(ifAddon);
                    }
                    break;
                }
            }
        }
        return infos;
    }

    public List<TraceResult> getTraceResult(String project, TimeLine timeLine) {
        VariableTracer tracer = new VariableTracer(_srcPath, _testSrcPath, this, project);
        List<TraceResult> traceResults = new ArrayList<>();
        List<String> trueTests = new ArrayList<>(_tests);
        trueTests.removeAll(_failTests);

        int tracedTestCount = 0;
        for (String testClass: trueTests){
            String testClassname = testClass.split("#")[0];
            String testMethodName = testClass.split("#")[1];
            if (!_failTests.contains(testClass) && !testFilter(_testSrcPath, testClassname, testMethodName)){
                continue;
            }
            try{
                traceResults.addAll(tracer.trace(classname(), functionname(), testClass.split("#")[0], testClass.split("#")[1], getDefaultErrorLine(), true));
            } catch (IOException e){
                e.printStackTrace();
            }
            tracedTestCount ++;
            if (traceResults.size()> MAX_TRACE_RESULT_COUNT || tracedTestCount > MAX_TRACE_TEST_COUNT){
                break;
            }
            if (timeLine.isTimeout()){
                return new ArrayList<>();
            }
        }

        // 对每个失败测试用 gzoltar trace
        for (String testclass: _failTests){
            try{
                int line = getDefaultErrorLine();
                assert line > 0;

                String clsName = classname();
                String funName = functionname();
                String testClassname = testclass.split("#")[0];
                String testMethodName = testclass.split("#")[1];
                //这里 trace ！！ 有执行 gzoltar ！
                List<TraceResult> tr = tracer.trace(clsName, funName, testClassname, testMethodName,line,false);

                traceResults.addAll(tr);
            } catch (IOException e){
                e.printStackTrace();
            }
            if (traceResults.size()> MAX_TRACE_RESULT_COUNT){
                break;
            }
            if (timeLine.isTimeout()){
                return new ArrayList<>();
            }
        }
        return traceResults;
    }

    private boolean testFilter(String testSrcPath, String testClassname, String testMethodName){
        String code = FileUtils.getCodeFromFile(testSrcPath, testClassname);
        String methodCode = FileUtils.getTestFunctionCodeFromCode(code, testMethodName,testSrcPath);
        if (methodCode.equals("")){
            return false;
        }
        List<String> assertLines = CodeUtils.getAssertInTest(testSrcPath, testClassname, testMethodName);
        if (assertLines.size() != 1){
            return true;
        }
        String assertLine = assertLines.get(0);
        if (!assertLine.trim().startsWith("Assert.assertEquals(") && !assertLine.trim().startsWith("TestUtils.assertEquals(") ){
            return true;
        }
        List<String> params = CodeUtils.divideParameter(assertLine,1);
        String param1 = params.get(0);
        String param2 = params.get(1);
        if (param1.contains(".") && param1.contains("(") && param2.contains(".") && param2.contains("(")){
            if (param1.substring(param1.indexOf("."), param1.indexOf("(")).equals(param2.substring(param2.indexOf("."), param2.indexOf("(")))){
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return  _function + " @ " + _defaultErrorLine;
    }
}
