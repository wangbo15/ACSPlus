package cn.edu.pku.sei.plde.ACS.main;

import cn.edu.pku.sei.plde.ACS.utils.ShellUtils;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Created by yanrunfa on 16/2/21.
 */
public class Main {

    public static void main(String[] args){

        String chartBugs = "Chart_1;Chart_4;Chart_9;Chart_14;Chart_15;Chart_19;Chart_26";
        String langBugs = "Lang_2;Lang_3;Lang_7;Lang_9;;Lang_16;Lang_24;Lang_35;Lang_39;Lang_44;Lang_47;Lang_55;Lang_58";
        String timeBugs = "Time_3;Time_15;Time_19;Time_27";

        //tried
        //Math_1;Math_3;Math_4;Math_5;Math_15;Math_25;Math_26

        //unable to fix
        //Math_61 Math_82 Math_85 Math_89
        String mathBugs = "Math_5;Math_15;Math_25;Math_26;Math_28;Math_32;Math_33;Math_35;Math_48" +
                ";Math_53;Math_61;Math_73;Math_81;Math_82;Math_85;Math_89;Math_90;Math_93;Math_94;Math_97;Math_99;Math_101";


        String bugs = mathBugs + ";" + chartBugs + ";" + timeBugs + ";" + langBugs;

        bugs = "Math_1;Math_3;Math_4;Math_5;Math_15;Math_25;Math_26;Math_28;Math_32;Math_33;Math_35";

        args = new String[]{"/home/nightwish/workspace/bug_repair/tmp/", "Math_25", "timeout:172800"}; //, "Math_3", "timeout:172800"

        if (args.length == 0){
            System.out.println("Hello world");
            System.exit(0);
        }
        new File(Config.TEMP_FILES_PATH).mkdirs();
        new File(Config.LOCALIZATION_RESULT_CACHE).mkdirs();
        String path = args[0];
        File file = new File(path);
        File [] sub_files = file.listFiles();
        if (sub_files == null){
            System.out.println("No file in path");
            System.exit(0);
        }
        List<String> bannedList = new ArrayList<>();
        if (args.length >= 2){
            for (int i=0; i< args.length; i++){
                if (args[i].startsWith("timeout:")){
                    int second = Integer.valueOf(args[i].substring(args[i].indexOf(":")+1));
                    Config.TOTAL_RUN_TIMEOUT = second;
                }
            }
            if (args[1].startsWith("ban:")){
                String banned = args[1].substring(args[1].indexOf(":")+1);
                bannedList.addAll(Arrays.asList(banned.split(";")));
            }
            else if (args[1].contains(";")){
                for (String name: args[1].split(";")){
                    System.out.println("Main: fixing project "+name);
                    try {
                        fixProject(name, path);
                    }
                    catch (Exception e){
                        e.printStackTrace();
                    }
                }
                System.exit(0);
            }
            else if (!args[1].contains(":")) {
                String projectName = args[1];
                try {
                    fixProject(projectName, path);
                } catch (Exception e){
                    e.printStackTrace();
                }
                System.exit(0);
            }
        }

        
        for (File sub_file : sub_files){
            if (sub_file.isDirectory()){
                System.out.println("Main: fixing project "+sub_file.getName());
                try {
                    if (bannedList.contains(sub_file.getName())){
                        System.out.println("Main: jumped project "+sub_file.getName());
                        continue;
                    }
                    fixProject(sub_file.getName(), path);
                }
                catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
        System.exit(0);
    }
    private static void fixProject(String project, String path) throws Exception{
        if (!project.contains("_")){
            System.out.println("Main: cannot recognize project name \""+project+"\"");
            return;
        }
        if (!StringUtils.isNumeric(project.split("_")[1])){
            System.out.println("Main: cannot recognize project name \""+project+"\"");
            return;
        }
        TimeLine timeLine = new TimeLine(Config.TOTAL_RUN_TIMEOUT);
        String projectType = project.split("_")[0];
        int projectNumber = Integer.valueOf(project.split("_")[1]);

        ShellUtils.runCmd("defects4j compile", new File(path + project + "/"));

        MainProcess process = new MainProcess(path);
        boolean result;
        File main = new File(Config.FIX_RESULT_FILE_PATH);
        if (!main.exists()){
            if (!new File(Config.RESULT_PATH).exists()){
                new File(Config.RESULT_PATH).mkdirs();
            }
            main.createNewFile();
        }
        try {
            FileWriter writer = new FileWriter(main, true);
            Format format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            writer.write("project "+project+" begin Time:"+format.format(new Date())+"\n");
            writer.close();
            cleanPrevResult(project);
            result = process.mainProcess(projectType, projectNumber, timeLine);
            writer = new FileWriter(main, true);
            writer.write("project "+project+" "+(result?"Success":"Fail")+" Time:"+format.format(new Date())+"\n");
            writer.close();
        } catch (Exception e){
            result = false;
        }
        if (!result){
           processFail(project, timeLine);
        }
    }

    private static void cleanPrevResult(String projectName){
        cleanMessage(Config.PATCH_PATH, projectName);
        cleanMessage(Config.LOCALIZATION_PATH, projectName);
        cleanMessage(Config.PATCH_SOURCE_PATH, projectName);
        cleanMessage(Config.RUNTIMEMESSAGE_PATH, projectName);
        cleanMessage(Config.PREDICATE_MESSAGE_PATH, projectName);
    }

    private static void cleanMessage(String forder, String projectName){
        if (new File(forder).exists() && new File(forder).isDirectory()){
            for (File file: new File(forder).listFiles()){
                if (file.getName().startsWith(projectName)){
                    file.delete();
                }
            }
        }
    }

    private static void processFail(String project, TimeLine timeLine){
        File[] patchFiles = {
                new File(Config.PATCH_PATH),
                new File(Config.PATCH_SOURCE_PATH)
        };
        for (File patchFile: patchFiles){
            if (patchFile.exists() && patchFile.isDirectory()){
                for (File file: patchFile.listFiles()){
                    if (file.getName().startsWith(project)){
                        file.delete();
                    }
                }
            }
        }
        if (timeLine.isTimeout()){
            try {
                File main = new File(Config.FIX_RESULT_FILE_PATH);
                if (!main.exists()){
                    main.createNewFile();
                }
                FileWriter writer = new FileWriter(main, true);
                Format format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                writer.write("project "+project+" Timeout At :"+format.format(new Date())+"\n");
                writer.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }


}
