package service;

import java.io.Serializable;
import java.util.List;

public class CheckEvenService implements ExecutableService, Serializable {
    private static final long serialVersionUID = 1L;
    @Override
    public String execute(List<String> inputParams) {
        if (inputParams.size() != 1) {
            return "Eroare: Serviciul accepta un singur parametru.";
        }
        try {
            int number = Integer.parseInt(inputParams.get(0));
            return number % 2 == 0 ? "Numarul este par." : "Numarul este impar.";
        } catch (NumberFormatException e) {
            return "Eroare: Parametrul trebuie să fie un numar.";
        }
    }
}

