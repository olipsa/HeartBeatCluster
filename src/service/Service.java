package service;

import java.io.Serializable;
import java.util.List;

public class Service implements Serializable {
    private final String uid;
    private final String name;
    private final String version;
    private final List<String> inputParams;
    private final List<String> returnParams;
    private final ExecutableService executableService;

    public Service(String uid, String name, String version, List<String> inputParams, List<String> returnParams, ExecutableService executableService) {
        this.uid = uid;
        this.name = name;
        this.version = version;
        this.inputParams = inputParams;
        this.returnParams = returnParams;
        this.executableService = executableService;
    }

    public String execute(List<String> parameters) {
        return executableService.execute(parameters);
    }

    @Override
    public String toString() {
        return "Service{" +
                "uid='" + uid + '\'' +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", inputParams=" + inputParams +
                ", returnParams=" + returnParams +
                '}';
    }

    public String getUid() {
        return uid;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public List<String> getInputParams() {
        return inputParams;
    }

    public List<String> getReturnParams() {
        return returnParams;
    }
}
