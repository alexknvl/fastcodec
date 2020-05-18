package test;

public class Test {
    private final static String array = "abc";

    public int match(Object inp) {
        char[] input = (char[]) inp;
        int current = 0;

        if (current >= input.length) return 10;
        if (input[current] == '0' || input[current] == '1') {
            current += 1;
            return 20;
        }
        return 30;
    }
}
