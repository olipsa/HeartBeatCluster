package service;

import java.io.Serializable;
import java.util.List;

public class AddNumbersService implements ExecutableService, Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    public String execute(List<String> inputParams) {
        try {
            int sum = inputParams.stream().mapToInt(Integer::parseInt).sum();
            return "Suma numerelor este: " + sum;
        } catch (NumberFormatException e) {
            return "Eroare: Parametrii trebuie să fie numere.";
        }
    }
}
