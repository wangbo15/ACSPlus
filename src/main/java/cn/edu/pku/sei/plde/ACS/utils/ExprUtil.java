package cn.edu.pku.sei.plde.ACS.utils;

import cn.edu.pku.sei.plde.ACS.main.Config;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by nightwish on 17-6-2.
 */
public class ExprUtil {
    public static List<String> loadConditions(String projectAndBug, int ith, int topK){
        List<String> res = new ArrayList<>(topK);
        String project = projectAndBug.split("_")[0].toLowerCase();

        String filePath = Config.PREDICTOR_OUT_ROOT + project + "/res/" + projectAndBug.toLowerCase() + "_" + ith + ".res.csv";

        File rslFile = new File(filePath);
        BufferedReader bReader = null;
        try {
            bReader = new BufferedReader(new FileReader(rslFile));
            String line = null;

            int size = 0;
            while ((line = bReader.readLine()) != null) {
                if(!line.contains("\t")){
                    continue;
                }
                line = (line.split("\t")[0]).trim();
                res.add(line);
                size++;
                if(size > topK){
                    break;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(bReader != null) {
                try {
                    bReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return res;
    }
}
