package cn.edu.pku.sei.plde.ACS.fix;

import cn.edu.pku.sei.plde.ACS.main.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by yanrunfa on 16/3/21.
 */
public class Patch {
    public final String _testClassName;
    public final String _testMethodName;
    public final String _className;
    public final List<Integer> _patchLines = new ArrayList<>();
    public List<String> _patchString = new ArrayList<>();
    public String _addonFunction = "";
    public String _addonImport = "";


    public Patch(String testClassName, String testMethodName, String className, List<Integer> patchLine, List<String> ifStrings, String fixString){
        _testClassName = testClassName;
        _testMethodName = testMethodName;
        _className = className;
        _patchLines.addAll(patchLine);
        if (fixString.contains(">>>")){
            String addonFunctionName = fixString.split(">>>")[1].split("<<<")[0];
            _addonFunction = fixString.split(">>>")[1].split("<<<")[1];
            _addonFunction = _addonFunction.replace(addonFunctionName, "patch_method");
            fixString = fixString.split(">>>")[0];
            fixString = fixString.replace(addonFunctionName, "patch_method");
        }
        if (fixString.contains("///")){
            _addonImport = fixString.split("///")[0];  // 所谓 addon 是填一些 import 之类的东西
            fixString = fixString.split("///")[1];
        }
        //add to judge the fixString is legal statement
        _patchString.add(generatePatchString("if(false)", fixString));
        for (String ifString: ifStrings){
            _patchString.add(generatePatchString(ifString, fixString));
        }
    }

    private String generatePatchString(String ifString, String fixString){
        String patchString = "";
        for (String _if: ifString.split("\n")){
            patchString += _if + "{" + fixString + "}";
        }
        return patchString;
    }

}
