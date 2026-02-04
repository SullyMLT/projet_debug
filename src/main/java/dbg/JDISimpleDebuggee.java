package dbg;

import result.ObjQuelconque;

import java.util.ArrayList;
import java.util.List;

public class JDISimpleDebuggee {
    public static void main(String[] args) {
        String description = "Simple power printer";
        System.out.println(description + " -- starting");
        int x = 40;
        int power = 2;
        ObjQuelconque obj = new ObjQuelconque();
        printPower(x, power);
        List<String> stringList = new ArrayList<>();
        stringList.add("Bonjour");
        stringList.add("Hello");
        stringList.add("Hola");
        stringList.add("Ciao");
        stringList.add("Ciao");
        stringList.add("Ciao");
        stringList.add("Ciao");
        stringList.add("Ciao");
        stringList.add("Ciao");
        stringList.add("Ciao");
        stringList.add("Ciao");
        stringList.add("Ciao");
        power = 6;
        power = 10;
    }

    public static double power(int x, int power) {
        double powerX = Math.pow(x, power);
        return powerX;
    }

    public static void printPower(int x, int power) {
        double powerX = power(x, power);
        System.out.println(powerX);
    }
}