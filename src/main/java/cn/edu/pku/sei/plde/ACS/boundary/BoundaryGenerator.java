package cn.edu.pku.sei.plde.ACS.boundary;

import cn.edu.pku.sei.plde.ACS.boundary.model.Interval;
import cn.edu.pku.sei.plde.ACS.localization.Suspicious;
import cn.edu.pku.sei.plde.ACS.trace.ExceptionVariable;
import cn.edu.pku.sei.plde.ACS.trace.filter.SearchBoundaryFilter;
import cn.edu.pku.sei.plde.ACS.type.TypeUtils;
import cn.edu.pku.sei.plde.ACS.utils.MathUtils;
import cn.edu.pku.sei.plde.ACS.utils.VariableUtils;
import edu.pku.sei.conditon.auxiliary.ASTLocator;
import edu.pku.sei.conditon.util.JavaFile;
import org.eclipse.jdt.core.dom.*;


import java.util.*;

/**
 * Created by yanrunfa on 16/2/23.
 */
public class BoundaryGenerator {

    public static ExceptionVariable GENERATING_VARIABLE;
    //TODO: 可能会改这里
    public static List<String> generate(Suspicious suspicious, ExceptionVariable exceptionVar) {
        List<Interval> intervals = new ArrayList<>();
        GENERATING_VARIABLE = exceptionVar;

        //去下载的 codes 里面找
        List<Interval> variableBoundary = SearchBoundaryFilter.getInterval(exceptionVar, suspicious);

        for (String value : exceptionVar.values){ // 变量在失败测试上的值
            try {
                if (MathUtils.isMaxMinValue(value)){
                    Double doubleValue = MathUtils.parseStringValue(value);
                    intervals.add(new Interval(doubleValue, doubleValue, true, true));
                    continue;
                }
                for (Interval interval: variableBoundary){// 留下异常值所在的 interval
                    if ((interval.containsValue(value)
                            || !TypeUtils.isSimpleType(exceptionVar.type)) && !intervals.contains(interval)){
                        intervals.add(interval);
                    }
                }
            } catch (NumberFormatException e){
                continue;
            }
        }
        List<String> condList = new ArrayList<>();
        for (Interval interval: intervals){
            //TODO: 生成 Patch 列表
            String condition = generateWithSingleWord(exceptionVar, interval.toString());
            if (!condition.equals("") && !condition.contains("!=")){
                condList.add(condition);
            }
        }
        return condList;
    }

    /**
     * 根据exception var 来过滤预测出的 conds，BoundaryGenerator#generate 的 ML 版
     */
    public static List<String> generateForML(Suspicious suspicious, ExceptionVariable exceptionVar, List<String> conds) {

        // 对于该特殊类型沿用原来的生成方法
        String excepVarName = exceptionVar.variable.variableName;
        if(excepVarName.endsWith(".Comparable") || excepVarName.endsWith(".null") || VariableUtils.isExpression(exceptionVar.variable)){
            return generate(suspicious, exceptionVar);
        }

        GENERATING_VARIABLE = exceptionVar;

        List<String> returnList = new ArrayList<>(200);

        OUTER:
        for(String condition: conds){
            try {
                Expression expr = (Expression) JavaFile.genASTFromSourceAsJava7(condition, ASTParser.K_EXPRESSION);

                VarCollectVisitor visitor = new VarCollectVisitor();
                expr.accept(visitor);
                List<String> vars = visitor.getEachVars();
                if(vars.size() != 1 || !vars.get(0).equals(excepVarName)){   //只有一个变量，且是当前异常变量
                    continue;
                }

                if(!isSimpleInfix(expr, excepVarName)){
                    returnList.add(condition); //不处理直接添加
                    continue;
                }
                //靠 exception val 过滤一些简单中缀表达式， `var op num`
                InfixExpression infix = (InfixExpression) expr;

                assert infix.getRightOperand() instanceof NumberLiteral;

                List<Interval> variableBoundary = SearchBoundaryFilter.getIntervalML(exceptionVar, expr);
                if(variableBoundary == null || variableBoundary.isEmpty()){
                    continue;
                }

                String newCond = getInteractedCondition(exceptionVar, variableBoundary, condition);
                if(!newCond.equals("") && !newCond.contains("!=")){
                    returnList.add(newCond);
                }

            } catch (NumberFormatException e){

            }
        }

        return returnList;
    }

    /**
     * 生成 Patch 列表的方法
     * @param exceptionVar
     * @param intervals
     * @return
     */
    private static String generateWithSingleWord(ExceptionVariable exceptionVar, String intervals) {
        if (exceptionVar.variable.variableName.equals("this")){
            return intervals.equals("this") ? "" : "this.equals(" + intervals + ")";
        }
        if (exceptionVar.variable.variableName.equals("return")){
            return intervals;
        }
        if (exceptionVar.variable.isAddon){// isAddon 是什么意思？ 好像是添加的变量，例如别的 if express，var.Comparable
            if (exceptionVar.variable.variableName.endsWith(".Comparable")){
                String variableName = exceptionVar.variable.variableName.substring(0,exceptionVar.variable.variableName.lastIndexOf("."));
                switch (intervals){
                    case "true":
                        return variableName + " instanceof Comparable<?>";
                    case "false":
                        return "!(" + variableName + " instanceof Comparable<?>)";
                }
            }
            if (exceptionVar.variable.variableName.endsWith(".null")){
                String variableName = exceptionVar.variable.variableName.substring(0,exceptionVar.variable.variableName.lastIndexOf("."));
                switch (intervals){
                    case "true":
                        return variableName+" == null";
                    case "false":
                        return variableName+" != null";
                }
            }
        }
        if (MathUtils.isNumberType(exceptionVar.variable.getStringType())) {
            if (!intervals.contains(", ")){
                if (intervals.equals("NaN")){
                    return  MathUtils.getComplexOfNumberType(exceptionVar.variable.getStringType()) +".isNaN("+exceptionVar.variable.variableName+")";
                }
                else {
                    return exceptionVar.variable.variableName + "==" + intervals;
                }
            }


            boolean biggestClose = false;
            boolean smallestClose = false;
            String biggest = intervals.split(", ")[0];  //为何数轴左边的是biggest，右边是smallest
            String smallest = intervals.split(", ")[1];
            String varType = MathUtils.getSimpleOfNumberType(exceptionVar.variable.getStringType());
            if (biggest.startsWith("[")){
                biggestClose = true;
            }
            biggest = biggest.substring(1);
            if (smallest.endsWith("]")){
                smallestClose = true;
            }
            smallest = smallest.substring(0, smallest.length() - 1);
            if (biggest.contains("MIN_VALUE") || biggest.equals("-Double.MAX_VALUE")){
                return exceptionVar.variable.variableName + lessSymbol(smallestClose)+"("+varType+")" + smallest;
            }
            if (smallest.contains("MAX_VALUE")){
                return exceptionVar.variable.variableName + greaterSymbol(biggestClose)+"("+varType+")" + biggest;
            }
            if (biggest.equals(smallest) && biggestClose && smallestClose){
                return exceptionVar.variable.variableName + "==" +smallest;
            }

            double biggestBoundary;
            double smallestBoundary;
            try {
                biggestBoundary = MathUtils.parseStringValue(biggest);
                smallestBoundary = MathUtils.parseStringValue(smallest);
            } catch (Exception e){
                return "";
            }

            if (biggestBoundary > smallestBoundary){
                double temp = biggestBoundary;
                biggestBoundary = smallestBoundary;
                smallestBoundary = temp;
            }

            Map<String, String> interval = new HashMap<>();

            //for (String valueString: variable.values){
            //    double value = MathUtils.parseStringValue(valueString);
            //    if (value < biggestBoundary) {
            //        interval.put("forwardInterval", variable.variable.variableName + lessSymbol(biggestClose)+"("+varType+")" + biggestBoundary);
            //    }
            //    else if (value > smallestBoundary) {
            //        interval.put("backwardInterval", variable.variable.variableName + greaterSymbol(smallestClose)+"("+ varType+")" + smallestBoundary);
            //    }
            //    else if (value <= smallestBoundary && value >= biggestBoundary) {
                    interval.put("innerInterval", "("+exceptionVar.variable.variableName + lessSymbol(smallestClose)+"("+varType+")" + smallestBoundary + " && " + exceptionVar.variable.variableName + greaterSymbol(biggestClose)+"("+varType+")" + biggestBoundary+")");
            //    }
            //}

            if (interval.size() == 0){
                return "";
            }
            if (interval.size() == 1) {
                return generateWithOneInterval(interval);
            }
            if (interval.size() == 2) {
                return generateWithTwoInterval(interval);
            }
        }
        if (exceptionVar.variable.variableName.contains("==")||exceptionVar.variable.variableName.contains("!=") || exceptionVar.variable.variableName.contains(">") || exceptionVar.variable.variableName.contains("<")){
            if (intervals.equals("true")){
                return exceptionVar.variable.variableName;
            }
            if (intervals.equals("false")){
                return "!("+exceptionVar.variable.variableName+")";
            }
        }
        return exceptionVar.variable.variableName + "==" + intervals;
    }

    private static String generateWithOneInterval(Map<String, String> intervals) {
        return (String) intervals.values().toArray()[0];
    }

    private static String generateWithTwoInterval(Map<String, String> intervals) {
        return (String) intervals.values().toArray()[0] + "||" + (String) intervals.values().toArray()[1];
    }

    private static String lessSymbol(boolean close){
        return close?"<=":"<";
    }

    private static String greaterSymbol(boolean close){
        return close?">=":">";
    }


    private static boolean isSimpleInfix(Expression expr, String varName){
        if(expr instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression) expr;
            if(!infix.getLeftOperand().toString().equals(varName)){
                return false;
            }

            Expression right = infix.getRightOperand();
            if(right instanceof PrefixExpression){//处理负数
                right = ((PrefixExpression) right).getOperand();
            }
            if (right instanceof NumberLiteral) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return 获得与异常值有交集的
     */
    private static String getInteractedCondition(ExceptionVariable exceptionVar, List<Interval> variableBoundary, String condition){

        List<Interval> intervals = new ArrayList<>();
        for (String value : exceptionVar.values) { // 异常值是否在某个 interval 里
            for (Interval interval: variableBoundary){
                if ((interval.containsValue(value)
                        || !TypeUtils.isSimpleType(exceptionVar.type)) && !variableBoundary.contains(interval)){
                    intervals.add(interval);
                }
            }
            for (Interval interval: intervals) {
                //TODO: 生成 Patch 列表
                String newCond = generateWithSingleWord(exceptionVar, interval.toString());
                if (!condition.equals("") && !condition.contains("!=")) {
                    //returnList.add(newCond);
                    return newCond;
                }
            }
        }
        return "";
    }
}

class VarCollectVisitor extends ASTVisitor{
    private List<String> eachVars = new ArrayList<>();

    public List<String> getEachVars(){
        return this.eachVars;
    }

    public boolean visit(SimpleName node) {
        if(Character.isUpperCase(node.getIdentifier().charAt(0)) || ASTLocator.notVarLocation(node) || ASTLocator.maybeConstant(node.getIdentifier())){
            return false;
        }
        eachVars.add(node.getIdentifier());
        return super.visit(node);
    }
}
