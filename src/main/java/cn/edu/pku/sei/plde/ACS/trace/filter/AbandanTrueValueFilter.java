package cn.edu.pku.sei.plde.ACS.trace.filter;

import cn.edu.pku.sei.plde.ACS.localization.Suspicious;
import cn.edu.pku.sei.plde.ACS.trace.ExceptionVariable;
import cn.edu.pku.sei.plde.ACS.trace.TraceResult;
import cn.edu.pku.sei.plde.ACS.type.TypeUtils;
import cn.edu.pku.sei.plde.ACS.utils.CodeUtils;
import cn.edu.pku.sei.plde.ACS.utils.FileUtils;
import cn.edu.pku.sei.plde.ACS.utils.InfoUtils;
import cn.edu.pku.sei.plde.ACS.utils.MathUtils;
import cn.edu.pku.sei.plde.ACS.visible.model.VariableInfo;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import java.util.*;

/**
 * Created by yanrunfa on 16/3/4.
 */
public class AbandanTrueValueFilter {

    private static boolean omit(VariableInfo variableInfo){
        if (variableInfo == null){
            return true;
        }
        //ban field variable
        if (variableInfo.isFieldVariable){
            return true;
        }
        return false;
    }

    public static List<ExceptionVariable> abandon(Suspicious suspicious, List<TraceResult> traceResults, List<VariableInfo> vars) {
        List<ExceptionVariable> exceptionValues = new ArrayList<>();
        Map<VariableInfo, List<String>> trueVariable = AbandanTrueValueFilter.getTrueValue(traceResults, vars);
//        Map<VariableInfo, List<String>> falseVariable = AbandanTrueValueFilter.getFalseValue(traceResults, vars);

        List<ExceptionVariable> levelTwoCandidate = new ArrayList<>();
        for (TraceResult traceResult: traceResults){
            //跳过正确的traceResult
            if (traceResult.getTestResult()){
                continue;
            }

            for (Map.Entry<String, List<String>> entry: traceResult.getResultMap().entrySet()){//traceResult.getResultMap() : var_string => var value list
                String varname = entry.getKey();
                List<String> results = entry.getValue();
                if (varname.contains(".null") && results.contains("false") && results.size() > 1){
                    results.remove("false");
                } else if (varname.contains(".null") && results.contains("false")){
                    continue;
                }

                VariableInfo variableInfo = getVariableInfoWithName(vars, varname);

                if (omit(variableInfo)){
                    System.out.println("WARNING: Omit VariableInfo With Variable Name " + varname);
                    continue;
                }

                //对于数组，把没在正确值中出现的元素加入怀疑值列表
                if (TypeUtils.isArrayFromName(variableInfo.variableName)){
                    List<String> falseValues = new ArrayList<>();
                    List<String> trueValues = trueVariable.get(variableInfo);
                    if (trueValues == null){
                        continue;
                    }
                    for (String value: results){
                        if (!trueValues.contains(value)){
                            falseValues.add(value);
                        }
                    }
                    if (falseValues.size() != 0){
                        ExceptionVariable variable = new ExceptionVariable(variableInfo, traceResult, falseValues);
                        if (!exceptionValues.contains(variable)){
                            exceptionValues.add(variable);
                        }
                        continue;
                    }
                }
                //跳过与正确值有交集的variable,加入第二等级怀疑变量候选列表
                if (trueVariable.containsKey(variableInfo)){
                    List<String> trueValues = trueVariable.get(variableInfo);
                    if (MathUtils.hasInterSection(trueValues, results)){ // 两个 List 的交集
                        ExceptionVariable variable = new ExceptionVariable(variableInfo, traceResult);
                        if (!levelTwoCandidate.contains(variable)){
                            levelTwoCandidate.add(variable);
                        }
                        continue;
                    }
                }
                ExceptionVariable variable = new ExceptionVariable(variableInfo, traceResult);
                if (!exceptionValues.contains(variable)){
                    exceptionValues.add(variable);
                }
            }
        }
        exceptionValues = cleanVariables(exceptionValues);
        if (exceptionValues.size() != 0){
            exceptionValues.addAll(getThis(suspicious, vars, traceResults));
            return exceptionValues;
        }
        exceptionValues.addAll(levelTwoCandidate);
        exceptionValues.addAll(getThis(suspicious, vars, traceResults));
        return exceptionValues;
    }

    public static List<ExceptionVariable> getThis(Suspicious suspicious, List<VariableInfo> vars, List<TraceResult> traceResults){
        List<ExceptionVariable> result = new ArrayList<>();
        String code = FileUtils.getCodeFromFile(suspicious._srcPath, suspicious.classname());
        if (CodeUtils.hasMethod(code,"equals")){
            Set<String> equalVariable = CodeUtils.getEqualVariableInSource(FileUtils.getFileAddressOfJava(suspicious._srcPath, suspicious.classname()));
            if (equalVariable.size() == 0){
                return new ArrayList<>();
            }
            for (TraceResult traceResult: traceResults){
                String valueName = suspicious.classname().substring(suspicious.classname().lastIndexOf(".")+1);
                valueName+="(";
                for (String variable: equalVariable){
                    List<String> value = traceResult.get(variable);
                    if (value == null){
                        continue;
                    }
                    valueName += value.get(0);
                    valueName +=",";
                }
                if (valueName.endsWith(",")){
                    valueName = valueName.substring(0, valueName.length()-1);
                }
                valueName+=")";
                if (valueName.contains("()")){
                    continue;
                }
                VariableInfo thisInfo = getVariableInfoWithName(vars, "this");
                ExceptionVariable exceptionVariable = new ExceptionVariable(thisInfo, traceResult, Arrays.asList(valueName));
                result.add(exceptionVariable);
            }
        }
        return result;
    }


    public static Map<VariableInfo, List<String>> filter(List<TraceResult> traceResults, List<VariableInfo> vars){
        Map<VariableInfo, List<String>> trueValues = filterTrueValue(traceResults, vars);
        Map<VariableInfo, List<String>> exceptionValues = new HashMap<VariableInfo, List<String>>();
        for (TraceResult traceResult: traceResults){
            if (traceResult.getTestResult()){
                continue;
            }
            Set<String> keys = traceResult.getResultMap().keySet();
            for (String key: keys){
                VariableInfo infoKey = getVariableInfoWithName(vars, key);
                if (infoKey == null){
                    continue;
                }
                if (!exceptionValues.containsKey(infoKey)){
                    exceptionValues.put(infoKey, new ArrayList<String>());
                }
                if (TypeUtils.isComplexType(infoKey.getStringType())){
                    for (String value: traceResult.get(key)){
                        if (value.equals("true")){
                            exceptionValues.get(infoKey).add("null");
                        }
                    }
                    continue;
                }
                for (String value: traceResult.get(key)){
                    String[] valueArray = stringToArray(value);
                    if (!trueValues.containsKey(infoKey)){
                        exceptionValues.get(infoKey).add(value);
                    }
                    else {
                        int count = 0;
                        for (String v: valueArray){
                            if (trueValues.get(infoKey).toString().contains(", "+v) ||
                                    trueValues.get(infoKey).toString().contains(v+",") ||
                                    trueValues.get(infoKey).toString().contains("["+v+"]")
                                    ){
                                count++;
                            }
                        }

                        if (count == 0 ){
                            exceptionValues.get(infoKey).add(value);
                        }
                    }
                }
                if (CodeUtils.isForLoopParam(traceResult.get(key))!=-1){
                    exceptionValues.get(infoKey).addAll(traceResult.get(key));
                }
                //delete the blank key-value
                if (exceptionValues.get(infoKey).size() == 0){
                    exceptionValues.remove(infoKey);
                }
            }
        }
        return exceptionValues;
    }

    private static List<ExceptionVariable> cleanVariables(List<ExceptionVariable> exceptionVariable){
        List<ExceptionVariable> cleanedVariable = new ArrayList<>();
        for (ExceptionVariable var: exceptionVariable){
            if (var.variable.isSimpleType && var.variable.variableSimpleType==null){
                continue;
            }
            if (!var.variable.isSimpleType && var.variable.otherType == null){
                continue;
            }
            if (var.values.size() == 0){
                continue;
            }
            if (var.type.equals("STRING")){
                continue;
            }
            cleanedVariable.add(var);
        }
        return cleanedVariable;
    }


    private static Map<VariableInfo, List<String>> filterTrueValue(List<TraceResult> traceResults, List<VariableInfo> vars){
        Map<VariableInfo, List<String>> trueValues = new HashMap<VariableInfo, List<String>>();
        for (TraceResult traceResult: traceResults){
            if (!traceResult.getTestResult()) {
                continue;
            }
            Set<String> keys = traceResult.getResultMap().keySet();
            for (String key: keys){
                VariableInfo infoKey = getVariableInfoWithName(vars, key);
                if (infoKey == null){
                    continue;
                }
                List<String> value = trueValues.containsKey(infoKey)?appandList(trueValues.get(infoKey),traceResult.get(key)):traceResult.get(key);
                trueValues.put(infoKey, value);
            }

        }
        return trueValues;
    }


    public static Map<VariableInfo, List<String>> getTrueValue(List<TraceResult> traceResults, List<VariableInfo> vars){
        Map<VariableInfo, List<String>> trueValues = new HashMap<VariableInfo, List<String>>();
        for (TraceResult traceResult: traceResults){
            if (!traceResult.getTestResult()) {
                continue;
            }
            Set<String> keys = traceResult.getResultMap().keySet();
            for (String key: keys){
                VariableInfo infoKey = getVariableInfoWithName(vars, key);
                if (infoKey == null){
                    continue;
                }
                List<String> value = trueValues.containsKey(infoKey)?appandList(trueValues.get(infoKey),traceResult.get(key)):traceResult.get(key);
                trueValues.put(infoKey, value);
            }

        }
        return trueValues;
    }

    public static Map<VariableInfo, List<String>> getFalseValue(List<TraceResult> traceResults, List<VariableInfo> vars){
        Map<VariableInfo, List<String>> falseValues = new HashMap<VariableInfo, List<String>>();
        for (TraceResult traceResult: traceResults){
            if (traceResult.getTestResult()) {
                continue;
            }
            Set<String> keys = traceResult.getResultMap().keySet();
            for (String key: keys){
                VariableInfo infoKey = getVariableInfoWithName(vars, key);
                if (infoKey == null){
                    continue;
                }
                List<String> value = falseValues.containsKey(infoKey)?appandList(falseValues.get(infoKey),traceResult.get(key)):traceResult.get(key);
                falseValues.put(infoKey, value);
            }
        }
        return falseValues;
    }

    private static <T> List<T> appandList(List<T> aa, List<T> bb){
        List<T> result = new ArrayList<T>();
        result.addAll(aa);
        result.removeAll(bb);
        result.addAll(bb);
        return result;
    }

    private static String[] stringToArray(String value){
        if (value.startsWith("[") && value.endsWith("]")){
            return value.substring(1,value.length()-1).split(",");
        }
        return new String[]{value};
    }

    private static VariableInfo getVariableInfoWithName(List<VariableInfo> infos, String name){
        Collections.sort(infos, new Comparator<VariableInfo>() {
            @Override
            public int compare(VariableInfo variableInfo, VariableInfo t1) {
                return -Integer.valueOf(variableInfo.priority).compareTo(t1.priority);
            }
        });
        List<VariableInfo> addons = new ArrayList<>();
        for (VariableInfo info: infos){
            if (info.variableName.equals(name)){
                return info;
            }
            if (TypeUtils.isComplexType(info.getStringType())){
                addons.addAll(InfoUtils.changeObjectInfo(info));
            }
        }
        for (VariableInfo info: addons){
            info.isAddon = true;
            if (info.variableName.equals(name)){
                return info;
            }
        }
        return null;
    }


}
