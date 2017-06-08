package cn.edu.pku.sei.plde.ACS.utils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by nightwish on 17-6-2.
 */
public class ExprUtil {
    public static List<String> loadConditions(String projectAndBug, int ith){
        List<String> res = new ArrayList<>();
        String project = projectAndBug.split("_")[0].toLowerCase();

        List<String> thisExprs = new ArrayList<>();
//        String bugID = projectAndBug.split("_")[1];

        String predictorOutRoot = "/home/nightwish/workspace/eclipse/StateCoverLocator/python/output/";
        String filePath = predictorOutRoot + project + "/res/" + projectAndBug.toLowerCase() + "_" + ith + ".res.csv";

        File rslFile = new File(filePath);
        BufferedReader bReader = null;
        try {
            bReader = new BufferedReader(new FileReader(rslFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        String line = null;

        try {
            while ((line = bReader.readLine()) != null) {
                if(line.contains(" & ") || line.contains(" | ")){
                    continue;
                }
                if(line.contains("this.")){
                    thisExprs.add("if(" + line + ")");
                }else{
                    res.add("if(" + line + ")");
                }
            }
            bReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //add this exprs to the end
        res.addAll(thisExprs);
        return res;
    }
}
