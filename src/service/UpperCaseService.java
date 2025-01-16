package service;

import java.io.Serializable;
import java.util.List;

public class UpperCaseService implements ExecutableService, Serializable {
    @Override
    public String execute(List<String> inputParams) {
        if (inputParams.isEmpty()) {
            return "Eroare: Lista de parametri este goala.";
        }
        return String.join(", ", inputParams).toUpperCase();
    }
}

