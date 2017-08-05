package cn.edu.pku.sei.plde.ACS.localization.gzoltar;

import com.gzoltar.core.components.Statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by nightwish on 17-7-31.
 */
public class StatementByUW extends Statement {

    private double suspiciousness = 0d;
    private List<String> tests = new ArrayList<String>();
    private List<String> failTests = new ArrayList<>();
    private String clazzName;
    private int line;

    public StatementByUW(Statement s, Map<String, Double> scoreMap) {
        super(s.getParent(), s.getLineNumber());
        this.clazzName = s.getLabel().split("\\{")[0];
        this.line = s.getLineNumber();
        this.suspiciousness = this.setSuspiciousness(scoreMap);
    }

    public void addFailTest(String test){
        failTests.add(test);
    }
    public void addTest(String test){
        tests.add(test);
    }

    public List<String> getTests(){
        return tests;
    }
    public List<String> getFailTests(){
        return failTests;
    }

    @Override
    public double getSuspiciousness(){
        return this.suspiciousness;
    }

    private double setSuspiciousness(Map<String, Double> scoreMap) {
        String key = this.clazzName + "#" + this.line;
        if(scoreMap.containsKey(key)){
            return scoreMap.get(key);
        }else{
            return 0d;
        }
    }

}
