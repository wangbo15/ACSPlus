package cn.edu.pku.sei.plde.ACS.main;

import cn.edu.pku.sei.plde.ACS.fix.SuspiciousFixer;
import cn.edu.pku.sei.plde.ACS.localization.Localization;
import cn.edu.pku.sei.plde.ACS.localization.Suspicious;
import cn.edu.pku.sei.plde.ACS.utils.FileUtils;
import cn.edu.pku.sei.plde.ACS.utils.PathUtils;
import cn.edu.pku.sei.plde.ACS.utils.RecordUtils;
import cn.edu.pku.sei.plde.ACS.utils.TestUtils;
import org.apache.commons.io.IOUtils;
import org.easymock.EasyMock;
import org.joda.convert.FromString;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by yanrunfa on 16/4/23.
 */
public class MainProcess {

    private String PATH_OF_DEFECTS4J;
    private String classpath;
    private String classSrc;
    private String testClasspath;
    private String testClassSrc;
    public static String PROJECT_NAME;

    private List<String> libPath = new ArrayList<>();
    private boolean successHalfFlag = false;
    public List<Suspicious> triedSuspicious = new ArrayList<>();

    public MainProcess(String path){
        if (!path.endsWith("/")){
            path += "/";
        }
        PATH_OF_DEFECTS4J = path;
    }

    public  boolean mainProcess(String projectType, int projectNumber, TimeLine timeLine) throws Exception{
        String project = setWorkDirectory(projectType,projectNumber);
        if (!checkProjectDirectory()){
            System.out.println("Main Process: set work directory error at project "+projectType+"-"+projectNumber);
            File recordPackage = new File(Config.PATCH_PATH);
            recordPackage.mkdirs();
            File main = new File(Config.FIX_RESULT_FILE_PATH);
            try {
                if (!main.exists()) {
                    main.createNewFile();
                }
                FileWriter writer = new FileWriter(main, true);
                writer.write("project "+project+" path error\n");
                writer.close();
            }catch (IOException e){
                e.printStackTrace();
            }
            return false;
        }
        PROJECT_NAME = project;
        Localization localization = new Localization(classpath, testClasspath, testClassSrc, classSrc,libPath);
        List<Suspicious> suspiciouses;
        if(Config.USING_UW_FL_DATA){
            suspiciouses = localization.getSuspiciousLiteOfUW(projectType.toLowerCase(), "" + projectNumber);   // will call gzoltar.run()
        }else{
            suspiciouses = localization.getSuspiciousLite(true);
        }

//        File locationDumpFile = new File(Config.LOCALIZATION_DUMP_PATH + "/" + project + ".loc");
//        String locMsg = "TOTOAL HAS " + suspiciouses.size() + " SUSPICIOUSES\n";
//        FileUtils.writeStringToFile(locationDumpFile, locMsg, true);

        if (timeLine.isTimeout()){
            return false;
        }
        if (suspiciouses.size() == 0){
            System.out.println("no suspicious found\n");
        }
        return suspiciousLoop(suspiciouses, project, timeLine);
    }


    private boolean checkProjectDirectory(){
        if (!new File(classpath).exists()){
            System.out.println("Classpath :"+classpath+" do not exist!");
            return false;
        }
        if (!new File(classSrc).exists()){
            System.out.println("ClassSourcePath :"+classSrc+" do not exist!");
            return false;
        }
        if (!new File(testClasspath).exists()){
            System.out.println("TestClassPath :"+testClasspath+" do not exist!");
            return false;
        }
        if (!new File(testClassSrc).exists()){
            System.out.println("TestSourcePath :"+testClassSrc+" do not exist!");
            return false;
        }
        return true;
    }


    public boolean suspiciousLoop(List<Suspicious> suspiciouses, String project, TimeLine timeLine) {

        for (int i = 0; i < suspiciouses.size(); i++){
            Suspicious suspicious = suspiciouses.get(i);

//            if(suspicious.getDefaultErrorLine() != 327){
//                continue;
//            }

            suspicious._libPath = libPath;
            boolean tried = false;
            for (Suspicious _suspicious: triedSuspicious){
                if (_suspicious._function.equals(suspicious._function) && _suspicious.classname().equals(suspicious.classname())){
                    tried = true;
                }
            }
            if (tried){
                continue;
            }
            try {
                if (timeLine.isTimeout()){
                    return false;
                }
                //修复入口
                if (fixSuspicious(i, suspicious, project, timeLine)){
                    return true;
                }
            } catch (Exception e){
                e.printStackTrace();
            }
            if (!successHalfFlag){
                triedSuspicious.add(suspicious);
            }

        }
        return false;
    }


    public boolean fixSuspicious(int i, Suspicious suspicious, String project, TimeLine timeLine) throws Exception{
        successHalfFlag = false;

        SuspiciousFixer fixer = new SuspiciousFixer(i, suspicious, project, timeLine);// get trace
        if (timeLine.isTimeout()){
            return false;
        }

        boolean fixed;
        if(Config.USING_ML){
            fixed = fixer.mainFixProcessByML();
        }else {
            fixed = fixer.mainFixProcess();
        }

        if(fixed){
            RecordUtils.printCollectingMessage(suspicious, timeLine);
            return isFixSuccess(project, timeLine);
        }

        return false;
    }

    public boolean isFixSuccess(String project, TimeLine timeLine){
        System.out.println("Fix Success One Place");
        if (timeLine.isTimeout()){
            return false;
        }
        int failTest = SuspiciousFixer.FAILED_TEST_NUM;
        if (failTest == 0){
            failTest = TestUtils.getFailTestNumInProject(project);//run defects4j test
        }
        if (failTest > 0){
            SuspiciousFixer.FAILED_TEST_NUM = failTest;
            Localization localization = new Localization(classpath, testClasspath, testClassSrc, classSrc,libPath);
            List<Suspicious> suspiciouses = localization.getSuspiciousLite(false);
            if (suspiciouses.size() == 0){
                successHalfFlag = true;
                return false;
            }

//            File locationDumpFile = new File(Config.LOCALIZATION_DUMP_PATH + "/" + project + ".loc");
//            String locMsg = "STILL HAS " + suspiciouses.size() + " SUSPICIOUSES\n";
//            FileUtils.writeStringToFile(locationDumpFile, locMsg, true);

            return suspiciousLoop(suspiciouses, project, timeLine);
        }
        else {
            System.out.println("Fix All Place Success");
            return true;
        }

    }

    public String setWorkDirectory(String projectName, int number){
        libPath.add(FromString.class.getProtectionDomain().getCodeSource().getLocation().getFile());
        libPath.add(EasyMock.class.getProtectionDomain().getCodeSource().getLocation().getFile());
        libPath.add(IOUtils.class.getProtectionDomain().getCodeSource().getLocation().getFile());
        File projectDir = new File(System.getProperty("user.dir")+"/project/");
        System.out.println("Project Dir: "+projectDir.getAbsolutePath());
        FileUtils.deleteDirNow(projectDir.getAbsolutePath());
        if (!projectDir.exists()){
            projectDir.mkdirs();
        }
        String project = projectName+"_"+number;
        /* 四个整个项目需要的参数 */
        FileUtils.copyDirectory(PATH_OF_DEFECTS4J+project,projectDir.getAbsolutePath());
        List<String> paths = PathUtils.getSrcPath(project);
        classpath = projectDir+"/"+project+paths.get(0);
        testClasspath = projectDir+"/"+project+paths.get(1);
        classSrc = projectDir+"/"+project+paths.get(2);
        testClassSrc = projectDir+"/"+ project + paths.get(3);
        FileUtils.copyDirectory(PATH_OF_DEFECTS4J+project+"/src/test/resources/",System.getProperty("user.dir")+"/src/test");
        File libPkg = new File(projectDir.getAbsolutePath()+"/"+project+"/lib/");
        if (libPkg.exists() && libPkg.list() != null){
            for (String p: libPkg.list()){
                if (p.endsWith(".jar")){
                    libPath.add(libPkg.getAbsolutePath()+"/"+p);
                }
            }
        }
        libPkg = new File(projectDir.getAbsolutePath()+"/"+project+"/build/lib/");
        if (libPkg.exists() && libPkg.list() != null){
            for (String p: libPkg.list()){
                if (p.endsWith(".jar")){
                    libPath.add(libPkg.getAbsolutePath()+"/"+p);
                }
            }
        }
        return project;
    }
}