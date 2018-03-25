package cn.edu.pku.sei.plde.ACS.trace.filter;

import cn.edu.pku.sei.plde.ACS.boundary.BoundaryCollect;
import cn.edu.pku.sei.plde.ACS.boundary.model.Interval;
import cn.edu.pku.sei.plde.ACS.gatherer.GathererJavaGithubCodeSnippet;
import cn.edu.pku.sei.plde.ACS.localization.Suspicious;
import cn.edu.pku.sei.plde.ACS.main.Config;
import cn.edu.pku.sei.plde.ACS.trace.ExceptionVariable;
import cn.edu.pku.sei.plde.ACS.type.TypeUtils;
import cn.edu.pku.sei.plde.ACS.utils.*;
import cn.edu.pku.sei.plde.ACS.visible.model.VariableInfo;
import org.apache.commons.lang.StringUtils;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by yanrunfa on 16/3/4.
 */
public class SearchBoundaryFilter {

    public static List<Interval> getInterval(ExceptionVariable exceptionVariable, String project, Suspicious suspicious) {
        VariableInfo info = exceptionVariable.variable;
        String variableName = info.variableName;
        String valueType = info.isSimpleType ? info.getStringType().toLowerCase() : info.getStringType();

        //获得 3 个搜索关键字
        ArrayList<String> keywords = getSerachKeyWord(exceptionVariable, suspicious);
        File codePackage = new File("experiment/searchcode/" + StringUtils.join(keywords,"-"));
        assert codePackage.exists();

        List<Interval> boundaryList;   //getBoundaryInterval() 内有取 top 20
        if (codePackage.list().length > 15 || VariableUtils.isExpression(info)){    // 为何限制 15 个 code file？？ 一般眼见的都是 80 个 file
            if (TypeUtils.isSimpleType(valueType)){
                BoundaryCollect boundaryCollect = new BoundaryCollect(codePackage.getAbsolutePath(), false, valueType);
                boundaryList = boundaryCollect.getBoundaryInterval();
            } else {
                BoundaryCollect boundaryCollect = new BoundaryCollect(codePackage.getAbsolutePath(), true, valueType);
                boundaryList = boundaryCollect.getBoundaryInterval();
            }
        } else if (TypeUtils.isSimpleType(valueType)) {
            keywords.remove(variableName);
            codePackage = new File("experiment/searchcode/" + StringUtils.join(keywords,"-"));
            assert codePackage.exists();

            BoundaryCollect boundaryCollect = new BoundaryCollect(codePackage.getAbsolutePath(), true, valueType);
            boundaryList = boundaryCollect.getBoundaryInterval();
        } else {
            keywords.remove(valueType);
            codePackage = new File("experiment/searchcode/" + StringUtils.join(keywords, "-"));
            assert codePackage.exists();

            BoundaryCollect boundaryCollect = new BoundaryCollect(codePackage.getAbsolutePath(), false, valueType);
            boundaryList = boundaryCollect.getBoundaryInterval();
        }
        return boundaryList;
    }

    private static boolean containsString(ArrayList<String> list, String string){
        for (String listString: list){
            if (listString.equalsIgnoreCase(string)){
                return true;
            }
        }
        return false;
    }

    private static ArrayList<String> getSerachKeyWord(ExceptionVariable exceptionVariable, Suspicious suspicious){
        VariableInfo info = exceptionVariable.variable;
        List<String> addonKeywords = new ArrayList<>();

        String variableName = info.variableName;
        String valueType = info.isSimpleType ? info.getStringType().toLowerCase() : info.getStringType();
        if (variableName.endsWith("[i]")){
            variableName = variableName.substring(0, variableName.indexOf("["));
        }
        ArrayList<String> keywords = new ArrayList<>();// github 的三个查询关键字
        keywords.add("if");
        keywords.addAll(addonKeywords);
        keywords.add(valueType);

        if (variableName.endsWith(".null")){
            variableName = variableName.substring(0, variableName.lastIndexOf("."));
            valueType = "object";
        }
        if (variableName.endsWith(".Comparable")){
            variableName = variableName.substring(0, variableName.lastIndexOf("."));
            valueType = "Comparable";
        }

        if (VariableUtils.isExpression(info)){
            String code = FileUtils.getCodeFromFile(suspicious._srcPath, suspicious.classname());
            String methodCode = CodeUtils.getMethodString(code, info.expressMethod);
            if (!methodCode.equals("")){
                String returnType = MethodUtils.getMethodReturnType(methodCode);
                if (!returnType.equals("") && !returnType.equals("void")){
                    keywords.add(returnType);
                }
            }
            String keyword = "";
            for (Character ch: info.expressMethod.toCharArray()){
                if(!((ch<='Z')&&(ch>='A'))){
                    keyword += ch;
                }
                else {
                    if (!containsString(keywords, keyword)) {
                        keywords.add(keyword);
                    }
                    keyword = ""+ch;
                }
            }
            if (!containsString(keywords, keyword)) {
                keywords.add(keyword);
            }
            keywords.remove(variableName);
            keywords.remove(valueType);
        }
        if (variableName.length() == 1){
            String methodName = suspicious.functionnameWithoutParam();
            String keyword = "";
            String code = FileUtils.getCodeFromFile(suspicious._srcPath, suspicious.classname());
            String methodCode = CodeUtils.getMethodString(code, methodName);
            if (!methodCode.equals("")){
                String returnType = MethodUtils.getMethodReturnType(methodCode);
                if (!returnType.equals("") && !returnType.equals("void")){
                    keywords.add(returnType);
                }
            }

            for (Character ch: methodName.toCharArray()){
                if(!((ch<='Z')&&(ch>='A'))){
                    keyword += ch;
                }
                else {
                    if (!containsString(keywords, keyword)) {
                        keywords.add(keyword);
                    }
                    keyword = ""+ch;
                }
            }
            if (!containsString(keywords, keyword)) {
                keywords.add(keyword);
            }
            keywords.remove(valueType);

        }
        if (!variableName.equals("this") && !VariableUtils.isExpression(info) && variableName.length() > 1){// 论文里写的是大于 2 个字母，而非 1
            keywords.add(variableName.replace(" ",""));
        }
        return keywords;
    }

    private static List<Interval> processManyCodeFilesOrExpression(File codePackage, String valueType){
        List<Interval> boundaryList;
        if (TypeUtils.isSimpleType(valueType)){
            BoundaryCollect boundaryCollect = new BoundaryCollect(codePackage.getAbsolutePath(), false, valueType);
            boundaryList = boundaryCollect.getBoundaryInterval();
        } else {
            BoundaryCollect boundaryCollect = new BoundaryCollect(codePackage.getAbsolutePath(), true, valueType);
            boundaryList = boundaryCollect.getBoundaryInterval();
        }
        return boundaryList;
    }


    public static void searchCode(ArrayList<String> keywords, String project){
        ExecutorService service = Executors.newSingleThreadExecutor();
        Future<Boolean> future = service.submit(new SearchCodeProcess(keywords, project));
        try {
            future.get(Config.SEARCH_BOUNDARY_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException e){
            future.cancel(true);
            service.shutdownNow();
            RuntimeUtils.killProcess();
            e.printStackTrace();
        } catch (TimeoutException e){
            future.cancel(true);
            service.shutdownNow();
            RuntimeUtils.killProcess();
            e.printStackTrace();
        } catch (ExecutionException e){
            service.shutdownNow();
            future.cancel(true);
            RuntimeUtils.killProcess();
            e.printStackTrace();
        }
    }




}

class SearchCodeProcess implements Callable<Boolean> {
    public ArrayList<String> keywords;
    public String project;

    public SearchCodeProcess(ArrayList<String> keywords, String project) {
        this.project = project;
        this.keywords = keywords;
    }

    public synchronized Boolean call() {
        String projName = getProjectFullName(project);

        GathererJavaGithubCodeSnippet GathererJavaGithubCodeSnippet = new GathererJavaGithubCodeSnippet(keywords,
                StringUtils.join(keywords, "-"),
                projName);
        try {
            GathererJavaGithubCodeSnippet.searchCode();//get code fragments from github
            if (Thread.interrupted()){
                return false;
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        return true;
    }

    private static String getProjectFullName(String project){
        if (project.startsWith("Math")){
            return "commons-math";
        }
        if (project.startsWith("Lang")){
            return "commons-lang";
        }
        if (project.startsWith("Closure")){
            return "closure-compiler";
        }
        if (project.startsWith("Chart")){
            return "jfreechart";
        }
        if (project.startsWith("Time")){
            return "joda-time";
        }
        return project;
    }
}