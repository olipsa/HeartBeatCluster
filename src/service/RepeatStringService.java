package service;

import java.io.Serializable;
import java.util.List;

public class RepeatStringService implements ExecutableService, Serializable {
    @Override
    public String execute(List<String> parameters) throws IllegalArgumentException {
        if (parameters.size() != 2) {
            return "EROARE: Serviciul necesita exact 2 parametri: un string si un int.";
        }

        String inputString = parameters.get(0);
        int repeatCount;
        try {
            repeatCount = Integer.parseInt(parameters.get(1));
        } catch (NumberFormatException e) {
            return "EROARE: Al doilea parametru trebuie sa fie un numar intreg.";
        }
        if (repeatCount < 0) {
            return "EROARE: Numarul de repetari trebuie sa fie un numar intreg pozitiv.";
        }

        if (inputString.startsWith("\"") && inputString.endsWith("\"")) {
            inputString = inputString.substring(1, inputString.length() - 1);
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < repeatCount; i++) {
            result.append(inputString);
            if (i < repeatCount - 1) {
                result.append(" ");
            }
        }

        return result.toString();
    }
}
