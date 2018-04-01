package cn.edu.pku.sei.plde.ACS.fix;

import cn.edu.pku.sei.plde.ACS.assertCollect.Asserts;
import cn.edu.pku.sei.plde.ACS.junit.JunitRunner;
import cn.edu.pku.sei.plde.ACS.localization.Suspicious;
import cn.edu.pku.sei.plde.ACS.main.Config;
import cn.edu.pku.sei.plde.ACS.type.TypeUtils;
import cn.edu.pku.sei.plde.ACS.utils.*;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.eclipse.jdt.core.dom.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * Created by yanrunfa on 16/4/1.
 */
public class MethodTwoFixer {
    private final String _classpath;
    private final String _testClassPath;
    private final String _classSrcPath;
    private final String _testSrcPath;
    private final String _className;
    private final String _methodName;
    private Suspicious _suspicious;
    private String _code;
    private String _methodCode;
    private Set<Integer> _errorLines;
    private int _methodStartLine;
    private int _methodEndLine;
    private int _errorTestNum;
    public String correctPatch;
    public int correctStartLine;
    public int correctEndLine;
    public List<String> triedPatch = new ArrayList<>();

    public MethodTwoFixer(Suspicious suspicious){
        _suspicious = suspicious;
        _classpath = suspicious._classpath;
        _testClassPath = suspicious._testClasspath;
        _classSrcPath = suspicious._srcPath;
        _testSrcPath = suspicious._testSrcPath;
        _className = suspicious.classname();
        _methodName = suspicious.functionnameWithoutParam();
        _code = FileUtils.getCodeFromFile(_classSrcPath, suspicious.classname());
        _methodCode  = CodeUtils.getMethodString(_code, suspicious.functionnameWithoutParam());
        _errorLines = suspicious.errorLines();
        List<Integer> methodLines = CodeUtils.getSingleMethodLine(_code, _methodName, _errorLines.iterator().next());
        if (methodLines.size()!= 2){
            _methodStartLine = 0;
            _methodEndLine = 0;
        }else {
            _methodStartLine = methodLines.get(0);
            _methodEndLine = methodLines.get(1);
        }
        _errorTestNum = SuspiciousFixer.FAILED_TEST_NUM;
    }

    //boundarys: org.apache.commons.math.distribution.NormalDistributionTest#testMath280#169 -> if conds
    public boolean fixML(Map<String, List<String>> boundarys, int errorLine, String project, boolean debug){

        for (Map.Entry<String, List<String>> entry: boundarys.entrySet()){
            List<String> ifStrings = entry.getValue();
            ifStrings = TypeUtils.arrayDup(ifStrings);
            List<Integer> ifLines = getIfLine(errorLine);//get the if stmt being modified
            if (ifLines.size()!=2){
                continue;
            }
            int blockStartLine = ifLines.get(0);//if的下一行，即block开始的行
            int blockEndLine = ifLines.get(1);  //block 结束 } 所在行
            String entryKey = entry.getKey();
            int tried = 0;
            for (String ifString: ifStrings){
                if(tried > Config.MAX_TRIED_COND){
                    break;
                }
                if (ifString.equals("")){
                    continue;
                }
                boolean precessSucc;
                if (ifString.contains("==")){
                    precessSucc = preocessEquvalenceExpr(blockStartLine,
                            blockEndLine,
                            ifString,
                            entryKey,
                            project,
                            debug);

                }else{
                    precessSucc = processComparingExpr(blockStartLine,
                            blockEndLine,
                            ifString,
                            entryKey,
                            project,
                            debug);
                }
                if(precessSucc){
                    return true;
                }
            }

        }
        return false;
    }

    private boolean processComparingExpr(int blockStartLine,
                                         int blockEndLine,
                                         String ifString,
                                         String entryKey,
                                         String project,
                                         boolean debug) {

        String ifStatement;
        for (int endLine: getLinesCanAdd(blockStartLine, blockEndLine,_code)) {
            String lastLineString = CodeUtils.getLineFromCode(_code, blockStartLine-1);
            String wholeLineString = CodeUtils.getWholeLineFromCodeReverse(_code, blockStartLine-1);
            boolean result;
            if (!LineUtils.isIfAndElseIfLine(wholeLineString)) {
                continue;
            }
            else {
                int leftStart = lastLineString.indexOf('(');
                int rightEnd = lastLineString.lastIndexOf(')');

                String oriExpr = lastLineString.substring(leftStart + 1, rightEnd);
                String ifBegin = lastLineString.substring(0, leftStart + 1);
                String ifEnd = lastLineString.substring(rightEnd);

                String fixExpr = "(" + getIfStringFromStatement(getIfStatementFromString(ifString));
                if (ifFilterML(oriExpr, fixExpr)){//TODO: 过滤
                    return false;
                }

                ifStatement = ifBegin + fixExpr + ifEnd; //replace the original expr
                try {
                    result = fixWithAddIf(blockStartLine-1, endLine, ifStatement, entryKey,  true, project, debug);
                } catch (TimeoutException e){
                    return false;
                }
                if (result){
                    correctStartLine = blockStartLine - 1;
                    correctEndLine = endLine;
                    correctPatch = ifStatement;
                    triedPatch.add(ifStatement);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean preocessEquvalenceExpr(int blockStartLine,
                                           int blockEndLine,
                                           String ifString,
                                           String entryKey,
                                           String project,
                                           boolean debug) {
        String ifStatement = "";
        for (int endLine: getLinesCanAdd(blockStartLine, blockEndLine,_code)) {
            String lastLineString = CodeUtils.getLineFromCode(_code, blockStartLine - 1);
            String wholeLineString = CodeUtils.getWholeLineFromCodeReverse(_code, blockStartLine - 1);
            boolean result = false;
            if (!LineUtils.isIfAndElseIfLine(wholeLineString)) {
                continue;
            } else {
                String ifEnd = lastLineString.substring(lastLineString.lastIndexOf(')'));
                lastLineString = lastLineString.substring(0, lastLineString.lastIndexOf(')'));
                if (!ifFilter(lastLineString, ifString)) {
                    continue;
                }
                ifStatement = lastLineString + "&&" + getIfStringFromStatement(getIfStatementFromString(ifString)) + ifEnd;
                try {
                    result = fixWithAddIf(blockStartLine - 1, endLine, ifStatement, entryKey, true, project, debug);
                } catch (TimeoutException e) {
                    return false;
                }
                if (result) {
                    correctStartLine = blockStartLine - 1;
                    correctEndLine = endLine;
                    correctPatch = ifStatement;
                    triedPatch.add(ifStatement);
                    return true;
                } else {
                    ifStatement = lastLineString + "||" + getIfStringFromStatement(ifString) + ifEnd;
                    try {
                        result = fixWithAddIf(blockStartLine - 1, endLine, ifStatement, entryKey, true, project, debug);
                    } catch (TimeoutException e) {
                        return false;
                    }
                    if (result) {
                        correctStartLine = blockStartLine - 1;
                        correctEndLine = endLine;
                        correctPatch = ifStatement;
                        triedPatch.add(ifStatement);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 与原条件相等者过滤，与原条件无变量相同者过滤
     * @param oriExprStr
     * @param fixExprStr
     * @return
     */
    private boolean ifFilterML(String oriExprStr, String fixExprStr) {
        String oriTmp = oriExprStr.replaceAll("\\s", "");
        String fixTmp = fixExprStr.replaceAll("\\s", "");
        if(("(" + oriTmp + ")").equals(fixTmp)){
            return true;
        }
        try{
            Expression ori = (Expression) JavaFile.genASTFromSource(oriExprStr, ASTParser.K_EXPRESSION);
            ExprVisitor oriVisitor = new ExprVisitor();
            ori.accept(oriVisitor);
            // 如果原式没有方法调用，则不filter，否则fixExpr与oriExpr 的调用方法必须有交集
            if(oriVisitor.methods.isEmpty()){
                return false;
            }
            Expression fix = (Expression) JavaFile.genASTFromSource(fixExprStr, ASTParser.K_EXPRESSION);
            ExprVisitor fixVisitor = new ExprVisitor();
            fix.accept(fixVisitor);
            for(String oriMtd : oriVisitor.methods){
                if(fixVisitor.methods.contains(oriMtd)){
                    return false;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
            System.err.println(fixExprStr);
        }
        return true;
    }

    private class ExprVisitor extends ASTVisitor {
        public Set<String> methods = new HashSet<>();
        @Override
        public boolean visit(MethodInvocation node) {
            methods.add(node.getName().getIdentifier());
            return super.visit(node);
        }
    }

    public boolean fix(Map<String, List<String>> boundarys, int errorLine, String project, boolean debug){
        for (Map.Entry<String, List<String>> entry: boundarys.entrySet()){
            List<String> ifStrings = entry.getValue();
            ifStrings = TypeUtils.arrayDup(ifStrings);

            List<Integer> ifLines = getIfLine(errorLine);
            if (ifLines.size()!=2){
                continue;
            }
            //if 的起止行
            int blockStartLine = ifLines.get(0);
            int blockEndLine = ifLines.get(1);

            for (String ifString: ifStrings){
                if (ifString.equals("")){
                    continue;
                }
                //只留下 "=="，这个过滤有点夸张吧
                if (ifString.contains(">") && ifString.contains("<") && !ifString.contains("<?>")){
                    continue;
                }

                String ifStatement;
                for (int endLine: getLinesCanAdd(blockStartLine, blockEndLine,_code)) {
                    String lastLineString = CodeUtils.getLineFromCode(_code, blockStartLine - 1);
                    String wholeLineString = CodeUtils.getWholeLineFromCodeReverse(_code, blockStartLine-1);
                    boolean result = false;
                    if (!LineUtils.isIfAndElseIfLine(wholeLineString)) { // 只处理 if 和 else if 的行
                        continue;
                    }
                    else {
                        String ifEnd = lastLineString.substring(lastLineString.lastIndexOf(')'));
                        lastLineString = lastLineString.substring(0, lastLineString.lastIndexOf(')'));
                        if (!ifFilter(lastLineString, ifString)){ // 新条件与原条件相等，或不是 (大于、大于等于、小于、小于等于 NUM) 形式的过滤
                            continue;
                        }
                        ifStatement =lastLineString+ "&&" + getIfStringFromStatement(getIfStatementFromString(ifString)) + ifEnd;
                        try {
                            result = fixWithAddIf(blockStartLine-1, endLine, ifStatement,entry.getKey(),  true, project, debug);
                        } catch (TimeoutException e){
                            return false;
                        }
                        if (result){
                            correctStartLine = blockStartLine-1;
                            correctEndLine = endLine;
                            correctPatch = ifStatement;
                            triedPatch.add(ifStatement);
                            return true;
                        }else{
                            ifStatement =lastLineString+ "||" +getIfStringFromStatement(ifString) + ifEnd;
                            try {
                                result = fixWithAddIf(blockStartLine-1, endLine, ifStatement,entry.getKey(),  true, project, debug);
                            } catch (TimeoutException e){
                                return false;
                            }
                            if (result){
                                correctStartLine = blockStartLine-1;
                                correctEndLine = endLine;
                                correctPatch = ifStatement;
                                triedPatch.add(ifStatement);
                                return true;
                            }
                        }
                    }
                }

            }

        }
        return false;
    }

    private boolean ifFilter(String ifBefore, String newIf){
        ifBefore = ifBefore.replace(" ","");
        if (ifBefore.startsWith("if(") || ifBefore.startsWith("}elseif(")){
            ifBefore = ifBefore.substring(ifBefore.indexOf("(")+1);
        }
        newIf = newIf.replace(" ","");
        newIf = removeBracket(getIfStringFromStatement(newIf));
        if (newIf.equals(ifBefore)){
            return false;
        }
        if (ifBefore.contains(">")){
            try {
                if (ifBefore.contains(">=")){
                    MathUtils.parseStringValue(ifBefore.substring(ifBefore.indexOf("=")+1));
                }
                else {
                    MathUtils.parseStringValue(ifBefore.substring(ifBefore.indexOf(">")+1));
                }
            } catch (NumberFormatException e){
                return false;
            }
            if (newIf.contains(ifBefore.substring(0, ifBefore.indexOf(">")))){
                return true;
            }
        }
        if (ifBefore.contains("<")){
            try {
                if (ifBefore.contains("<=")){
                    MathUtils.parseStringValue(ifBefore.substring(ifBefore.indexOf("=")+1));
                }
                else {
                    MathUtils.parseStringValue(ifBefore.substring(ifBefore.indexOf("<")+1));
                }
            } catch (NumberFormatException e){
                return false;
            }
            if (newIf.contains(ifBefore.substring(0, ifBefore.indexOf("<")))){
                return true;
            }
        }
        return false;
    }

    private static List<Integer> getLinesCanAdd(int startLine, int endLine, String code){
        List<Integer> result = new ArrayList<>();
        int braceCount = 0;
        if (startLine == endLine){
            result.add(endLine);
            return result;
        }
        for (int i= endLine; i> startLine; i--){
            String lineString = CodeUtils.getLineFromCode(code, i);
            if (braceCount == 0){
                result.add(i);
            }
            braceCount += CodeUtils.countChar(lineString,'{');
            braceCount -= CodeUtils.countChar(lineString,'}');
        }
        return result;
    }

    private String getIfStatementFromString(String ifString){
        /*
        String statement =  ifString.replace("if (","if (!(");
        statement =  ifString.replace("if(","if (!(");
        statement += "){";
        return statement;
        */
        return ifString + "){";
    }

    private String getIfStringFromStatement(String ifStatement){
        return ifStatement.substring(ifStatement.indexOf('(')+1, ifStatement.lastIndexOf(')'));
    }

    private String removeBracket(String ifStatement){
        ifStatement = ifStatement.replace(" ","");
        if (ifStatement.startsWith("(") || ifStatement.startsWith("!(")){
            return getIfStringFromStatement(ifStatement);
        }
        return ifStatement;
    }

    private boolean fixWithAddIf(int ifStartLine, int ifEndLine, String ifStatement,String testMessage, boolean replace, String project, boolean debug) throws TimeoutException{
        String testClassName = testMessage.split("#")[0];
        String testMethodName = testMessage.split("#")[1];
        int assertLine = Integer.valueOf(testMessage.split("#")[2]);
        Asserts asserts = _suspicious._assertsMap.get(testClassName+"#"+testMethodName);
        AssertComment comment = new AssertComment(asserts, assertLine);
        List<String> thrownExceptions = new ArrayList<>();
        if (asserts._thrownExceptionMap.containsKey(assertLine)){
            thrownExceptions = asserts._thrownExceptionMap.get(assertLine);
        }
        if (!canBeEqualsNull(ifStatement, thrownExceptions)){
        //    return false;
        }
        comment.comment();

        File targetJavaFile = new File(FileUtils.getFileAddressOfJava(_classSrcPath, _className));
        File targetClassFile = new File(FileUtils.getFileAddressOfClass(_classpath, _className));
        File javaBackup = FileUtils.copyFile(targetJavaFile.getAbsolutePath(), FileUtils.tempJavaPath(_className,"MethodTwoFixer"));
        File classBackup = FileUtils.copyFile(targetClassFile.getAbsolutePath(), FileUtils.tempClassPath(_className,"MethodTwoFixer"));
        SourceUtils.insertIfStatementToSourceFile(targetJavaFile, ifStatement, ifStartLine, ifEndLine, replace);
        try {
            targetClassFile.delete();
            ShellUtils.shellRun(Arrays.asList("javac -Xlint:unchecked -source 1.6 -target 1.6 -cp "+ buildClasspath(Arrays.asList(PathUtils.getJunitPath())) +" -d "+_classpath+" "+ targetJavaFile.getAbsolutePath()));
        }
        catch (IOException e){
            FileUtils.copyFile(classBackup, targetClassFile);
            FileUtils.copyFile(javaBackup, targetJavaFile);
            comment.uncomment();
            return false;
        }
        if (!targetClassFile.exists()){ //编译不成功
            FileUtils.copyFile(classBackup, targetClassFile);
            FileUtils.copyFile(javaBackup, targetJavaFile);
            comment.uncomment();
            return false;
        }
        //Assert will call gzlotar to get trace
        Asserts assertsAfterFix = new Asserts(_classpath,_classSrcPath, _testClassPath, _testSrcPath, testClassName, testMethodName, new ArrayList<String>(), project);
        if (assertsAfterFix.timeout){
            comment.uncomment();
            return false;
        }
        int errAssertAfterFix = assertsAfterFix.errorNum();
        int errAssertBeforeFix = asserts.errorNum();
        System.out.print("Method 2 try patch: "+ifStatement+" in line: "+ifStartLine);
        if (errAssertAfterFix < errAssertBeforeFix || errAssertAfterFix == 0) {
            int errorTestNumAfterFix = TestUtils.getFailTestNumInProject(project);  //will run 'defects4j test'
            if (errorTestNumAfterFix == Integer.MAX_VALUE){
                comment.uncomment();
                throw new TimeoutException();
            }
            if (errorTestNumAfterFix < _errorTestNum){
                System.out.println(" fix success");
                if (debug){
                    FileUtils.copyFile(classBackup, targetClassFile);
                    FileUtils.copyFile(javaBackup, targetJavaFile);
                }
                SuspiciousFixer.FAILED_TEST_NUM = errorTestNumAfterFix;
                RecordUtils.recordIf(_code, ifStatement,ifStartLine,ifEndLine,replace,project);
                comment.uncomment();
                return true;
            }
        }
        FileUtils.copyFile(classBackup, targetClassFile);
        FileUtils.copyFile(javaBackup, targetJavaFile);
        comment.uncomment();
        System.out.println(" fix fail");
        return false;
    }


    private List<Integer> getIfLine(int errorLine){
        int braceCount = 0;
        for (int i= errorLine-1; i>_methodStartLine; i--){
            String lineString = CodeUtils.getLineFromCode(_code, i);
            braceCount += CodeUtils.countChar(lineString, '{');
            braceCount -= CodeUtils.countChar(lineString, '}');
        }
        if (braceCount > 0){
            return getBraceArea(errorLine);
        }
        else {
            return Arrays.asList(errorLine - 1, errorLine);
        }
    }

    private boolean changeIfArea(List<Integer> area, int errorLine){
        int startLine = area.get(0);
        int endLine = area.get(1);
        if (errorLine - startLine >= errorLine - endLine){
            area.set(1, endLine+1);
        }
        else {
            area.set(0, startLine-1);
        }
        return area.get(0) > _methodStartLine && area.get(1) < _methodEndLine;
    }
    //TODO: baka bug!!
    private List<Integer> getBraceArea(int errorLine){
        List<Integer> result = new ArrayList<>();
        int bracket = 0;
        for (int i = 0; i < _methodEndLine-_methodStartLine; i++){
            String lineString = CodeUtils.getLineFromCode(_code, errorLine-i);
            if (lineString.contains("{") || lineString.contains("}")){
                if (bracket == 0){
                    result.add(errorLine-i+1);
                    break;
                }
            }
            bracket += CodeUtils.countChar(lineString, '}');
            bracket -= CodeUtils.countChar(lineString, '{');
        }
        bracket = 0;
        for (int i = 0; i<_methodEndLine-_methodStartLine; i++){
            String lineString = CodeUtils.getLineFromCode(_code, errorLine+i);
            if (result.size() != 0){
                if (lineString.contains("}")){
                    if (bracket == 0){
                        result.add(errorLine+i);
                        break;
                    }
                }
                bracket += CodeUtils.countChar(lineString, '{');
                bracket -= CodeUtils.countChar(lineString, '}');
            }
        }
        return result;
    }

    private String buildClasspath(List<String> additionalPath){
        String path = "\"";
        path += _classpath;
        path += System.getProperty("path.separator");
        path += _testClassPath;
        path += System.getProperty("path.separator");
        path += JunitRunner.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        path += System.getProperty("path.separator");
        path += StringUtils.join(additionalPath,System.getProperty("path.separator"));
        path += System.getProperty("path.separator");
        path += StringUtils.join(_suspicious._libPath,System.getProperty("path.separator"));
        path += "\"";
        return path;
    }

    private static boolean canBeEqualsNull(String ifString, List<String> thrownExceptions){
        if (!ifString.contains("null")){
            return true;
        }
        for (String exception: thrownExceptions){
            if (exception.contains("java.lang.NullPointerException")){
                return true;
            }
        }
        return false;
    }


}
