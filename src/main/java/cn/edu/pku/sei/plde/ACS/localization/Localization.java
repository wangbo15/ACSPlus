package cn.edu.pku.sei.plde.ACS.localization;

import cn.edu.pku.sei.plde.ACS.localization.common.library.JavaLibrary;
import cn.edu.pku.sei.plde.ACS.localization.common.synth.TestClassesFinder;
import cn.edu.pku.sei.plde.ACS.localization.gzoltar.GZoltarSuspiciousProgramStatements;
import cn.edu.pku.sei.plde.ACS.localization.gzoltar.StatementByUW;
import cn.edu.pku.sei.plde.ACS.localization.gzoltar.StatementExt;
import cn.edu.pku.sei.plde.ACS.localization.metric.Metric;
import cn.edu.pku.sei.plde.ACS.localization.metric.NoMetric;
import cn.edu.pku.sei.plde.ACS.localization.metric.Ochiai;
import cn.edu.pku.sei.plde.ACS.main.Config;
import cn.edu.pku.sei.plde.ACS.utils.FileUtils;
import cn.edu.pku.sei.plde.ACS.utils.RecordUtils;
import com.gzoltar.core.components.Statement;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.net.URL;
import java.util.*;

/**
 * Created by localization on 16/1/23.
 */

public class Localization  {
    private String classpath;
    private String testClassPath;
    private String[] testClasses;
    private String testSrcPath;
    private String srcPath;
    private List<String> libPaths = new ArrayList<>();

    private Map<String, Double> scoreMapOfUW;

    public Localization(String classPath, String testClassPath, String testSrcPath, String srcPath, List<String> libPaths) {
        this(classPath, testClassPath, testSrcPath, srcPath);
        this.libPaths = libPaths;
    }


    private Localization(String classPath, String testClassPath, String testSrcPath, String srcPath){
        this.classpath = classPath;
        this.testClassPath = testClassPath;
        this.testSrcPath = testSrcPath;
        this.srcPath = srcPath;
        testClasses = new TestClassesFinder().findIn(JavaLibrary.classpathFrom(testClassPath), false);
        Arrays.sort(testClasses);
    }

    public Localization(String classPath, String testClassPath, String testSrcPath, String srcPath, String testClass){
        this.classpath = classPath;
        this.testClassPath = testClassPath;
        this.testSrcPath = testSrcPath;
        this.srcPath = srcPath;
        testClasses = new String[]{testClass};
    }




    public List<Statement> getSuspiciousListWithSuspiciousnessBiggerThanZero(Metric metric){
        List<Statement> statements = this.getSuspiciousList(metric);
        List<Statement> result = new ArrayList<>();
        for (Statement statement: statements){
            double score;
            if(statement instanceof StatementByUW){
                score = ((StatementByUW) statement).getSuspiciousness();
            }else{
                score = ((StatementExt) statement).getSuspiciousness();
            }
            if (score > 0){
                result.add(statement);
            }
        }
        return result;
    }


    private Map<String, Double> loadUWData(String subject, String bugid){
        Map<String, Double> result = new HashMap<>();
        String proj = subject.toLowerCase().substring(1);
        proj = Character.toUpperCase(subject.charAt(0)) + proj;
        String locPath = "localization/ochiai/" + proj + "/" + bugid + ".txt";
        File locFile = new File(locPath);
        BufferedReader bReader = null;
        FileReader fReader = null;
        try {
            fReader = new FileReader(locFile);
            bReader = new BufferedReader(fReader);
            String line = null;
            List<String> lines = new ArrayList<>();
            List<Double> vals = new ArrayList<>();
            while ((line = bReader.readLine()) != null) {
                String[] columns = line.split(",");
                //sth like 'org.apache.commons.math3.optimization.linear.Relationship#59'
                String key = columns[0];
                String val = columns[1];
                Double score = Double.valueOf(val);
                if(score > 0.0D) {
                    lines.add(key);
                    vals.add(score);
                }
            }//end while
            assert lines.size() == vals.size();
            for(int i = 0; i < lines.size() - 1 ;i++){
                String curr = lines.get(i);
                String next = lines.get(i+1);
                if(inSameBlock(curr, next)){
                    continue;
                }else{
                    result.put(curr, vals.get(i));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally{
            if(bReader != null){
                try {
                    bReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if(fReader != null){
                try {
                    fReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    //TODO: navive
    private boolean inSameBlock(String key0, String key1) {
        if(key0 == null || key1 == null || key0.split("#").length < 2 ||  key1.split("#").length < 2){
            return false;
        }

        String cls0 = key0.split("#")[0];
        String cls1 = key1.split("#")[0];

        if(cls0.equals(cls1) == false){
            return false;
        }
        int line0 = new Integer(key0.split("#")[1]);
        int line1 = new Integer(key1.split("#")[1]);
        if(Math.abs(line0 - line1) <= 10){
            return true;
        }
        return false;
    }


    public List<Suspicious> getSuspiciousLiteOfUW(String subject, String bugid){

        List<Suspicious> result = new ArrayList<>();

        scoreMapOfUW = loadUWData(subject, bugid);

        List<Statement> statements = statementFilter(this.getSuspiciousListWithSuspiciousnessBiggerThanZero(new NoMetric()));
        if (statements.size() == 0){
            return result;
        }
        StatementByUW firstline = (StatementByUW) statements.get(0);
//        List<String> lineNumbers = new ArrayList<String>();
        for(Statement s: statements){
            StatementByUW currStmt = (StatementByUW) s;
            if (getClassAddressFromStatement(currStmt).equals(getClassAddressFromStatement(firstline)) &&
                    getTargetFunctionFromStatement(currStmt).equals(getTargetFunctionFromStatement(firstline))){
//                lineNumbers.add(String.valueOf(currStmt.getLineNumber()));

            }else {
                String clzAddress = getClassAddressFromStatement(currStmt);
                String tagFunction = getTargetFunctionFromStatement(currStmt);
                double score = currStmt.getSuspiciousness();
                List<String> lineList = new ArrayList<String>();
                lineList.add("" + currStmt.getLineNumber());
                Suspicious susp = new Suspicious(classpath, testClassPath,srcPath,testSrcPath, clzAddress, tagFunction,
                        score, currStmt.getTests(),currStmt.getFailTests(), lineList,libPaths);
                result.add(susp);
                firstline = currStmt;
//                lineNumbers.clear();
//                lineNumbers.add(String.valueOf(currStmt.getLineNumber()));
            }
//            if (lineNumbers.size() != 0 && firstline.getTests().size()< 30){
//                result.add(new Suspicious(classpath, testClassPath,srcPath,testSrcPath, getClassAddressFromStatement(firstline), getTargetFunctionFromStatement(firstline), firstline.getSuspiciousness(), firstline.getTests(),firstline.getFailTests(), new ArrayList<String>(lineNumbers),libPaths));
//            }

        }//end for
        Collections.reverse(result);
        Collections.sort(result, new Comparator<Suspicious>() {
            @Override
            public int compare(Suspicious o1, Suspicious o2) {
                return Double.compare(o2._suspiciousness, o1._suspiciousness);
            }
        });

        /*
        try {
            suspicousFile.createNewFile();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(suspicousFile));
            objectOutputStream.writeObject(result);
            objectOutputStream.close();
        } catch (IOException e){
            e.printStackTrace();
            return new ArrayList<Suspicious>();
        }
        */
        RecordUtils recordUtils = new RecordUtils("locationLog");
        for (Suspicious suspicious: result){
            recordUtils.write(suspicious.classname()+"#"+suspicious.functionnameWithoutParam()+"#"+suspicious.getDefaultErrorLine()+"\n");
        }
        recordUtils.close();
        return result;
    }

    public List<Suspicious> getSuspiciousLite(boolean jump){
        File suspicousFile = new File(Config.LOCALIZATION_RESULT_CACHE+ FileUtils.getMD5(StringUtils.join(testClasses,"")+classpath+testClassPath+srcPath+testSrcPath)+".sps");

        /*
        if (suspicousFile.exists() && jump){
            try {
                ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(suspicousFile));
                List<Suspicious> result = (List<Suspicious>) objectInputStream.readObject();
                objectInputStream.close();
                return result;
            }catch (Exception e){
                System.out.println("Reloading Localization Result...");
            }
        }
        */

        List<Statement> statements = statementFilter(this.getSuspiciousListWithSuspiciousnessBiggerThanZero(new Ochiai()));
        List<Suspicious> result = new ArrayList<Suspicious>();
        if (statements.size() == 0){
            return result;
        }
        //firstline and lineNumbers are used to make each func have only one stmt left?
        StatementExt firstline = (StatementExt) statements.get(0);
        List<String> lineNumbers = new ArrayList<String>();
        for (Statement stmt: statements){
            StatementExt statement = (StatementExt) stmt;
            if (getClassAddressFromStatement(statement).equals(getClassAddressFromStatement(firstline)) &&
                    getTargetFunctionFromStatement(statement).equals(getTargetFunctionFromStatement(firstline))){
                lineNumbers.add(String.valueOf(statement.getLineNumber()));
            }else {
                String clzAdd = getClassAddressFromStatement(firstline);
                String tagFun = getTargetFunctionFromStatement(firstline);
                double score = firstline.getSuspiciousness();
                result.add(new Suspicious(classpath, testClassPath,srcPath,testSrcPath, clzAdd, tagFun,
                        score, firstline.getTests(),firstline.getFailTests(),
                        new ArrayList<>(lineNumbers),libPaths));
                firstline = statement;
                lineNumbers.clear();
                //nann da ko no ba ka no codo??????? First clear(), then contains() ??
                if (!lineNumbers.contains(String.valueOf(statement.getLineNumber()))){
                    lineNumbers.add(String.valueOf(statement.getLineNumber()));
                }
            }
        }
        if (lineNumbers.size() != 0 && firstline.getTests().size()< 30){
            result.add(new Suspicious(classpath, testClassPath,srcPath,testSrcPath, getClassAddressFromStatement(firstline), getTargetFunctionFromStatement(firstline), firstline.getSuspiciousness(), firstline.getTests(),firstline.getFailTests(), new ArrayList<String>(lineNumbers),libPaths));
        }
        Collections.sort(result, new Comparator<Suspicious>() {
            @Override
            public int compare(Suspicious o1, Suspicious o2) {
                return Double.compare(o2._suspiciousness, o1._suspiciousness);
            }
        });
        if (!jump){
            return result;
        }

        /*
        try {
            suspicousFile.createNewFile();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(suspicousFile));
            objectOutputStream.writeObject(result);
            objectOutputStream.close();
        } catch (IOException e){
            e.printStackTrace();
            return new ArrayList<>();
        }
        */

        RecordUtils recordUtils = new RecordUtils("localization");
        for (Suspicious suspicious: result){
            recordUtils.write(suspicious.classname()+"#"+suspicious.functionnameWithoutParam()+"#"+suspicious.getDefaultErrorLine()+"\n");
        }
        recordUtils.close();
        return result;
    }

    private List<Statement> statementFilter(List<Statement> statements){
        List<Statement> result = new ArrayList<>();
        String packageName = "";
        for (int i=0; i< 2; i++){
            String[] test = testClasses[0].split("\\.");
            packageName += test[i];
            packageName += ".";
        }
        for (Statement statement: statements){
            if (statement.getName().contains("exception") || statement.getName().contains("Exception")){
                continue;
            }
            if (!statement.getLabel().trim().startsWith(packageName.trim())){
                continue;
            }
            result.add(statement);
        }
        return result;
    }



    /**
     *
     * @return the list of suspicious statement
     */
    public List<Statement> getSuspiciousList(Metric metric){
        URL[] classpaths = JavaLibrary.classpathFrom(testClassPath);
        classpaths = JavaLibrary.extendClasspathWith(classpath, classpaths);
        if (libPaths != null){
            for (String path: libPaths){
                classpaths = JavaLibrary.extendClasspathWith(path, classpaths);
            }
        }
        GZoltarSuspiciousProgramStatements gZoltar = GZoltarSuspiciousProgramStatements.create(classpaths, testClasses, metric,testSrcPath, srcPath, libPaths);

        if(metric instanceof NoMetric){
            gZoltar.getGzoltar().setScoreMapOfUW(scoreMapOfUW);
        }

        return gZoltar.sortBySuspiciousness(testClasses);//will call gzoltar.run
    }

    /**
     *
     * @param statement target statement
     * @return class address of statement
     */
    public static String getClassAddressFromStatement(Statement statement){
        return statement.getLabel().split("\\{")[0].replace(".head.",".");
    }

    public static String getClassNameFromStatement(Statement statement){
        String classAddress = getClassAddressFromStatement(statement);
        return classAddress.substring(classAddress.lastIndexOf(".")+1);
    }

    /**
     *
     * @param statement target statement
     * @return class address of statement
     */
    public static String getLineNumberFromStatement(Statement statement){
        return String.valueOf(statement.getLineNumber());
    }

    /**
     *
     * @param statement target statement
     * @return class address of statement
     */
    public static String getSupiciousnessFromStatement(Statement statement){
        return String.valueOf(statement.getSuspiciousness());
    }

    /**
     *
     * @param statement target statement
     * @return class address of statement
     */
    public static String getTargetFunctionFromStatement(Statement statement){
        return statement.getLabel().split("\\{")[1].split("\\)")[0]+"\\)";
    }

    public static String getFunctionNameFromStatement(Statement statement){
        return getTargetFunctionFromStatement(statement).split("\\(")[0];
    }

    public static String getErrorTestsStringFromStatement(StatementExt statementExt){
        return StringUtils.join(statementExt.getTests(),"-");
    }


}
