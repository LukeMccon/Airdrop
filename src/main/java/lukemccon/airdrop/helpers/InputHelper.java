package lukemccon.airdrop.helpers;

public class InputHelper {

    public static Double parseDouble(String str) {
        if (str != null && !str.isEmpty()) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                // Handle the case when the string is not a valid double
                System.err.println("Error parsing double value: " + e.getMessage());
                return null;
            }
        } else {
            // Handle the case when the string is null or empty
            return null;
        }
    }
}
