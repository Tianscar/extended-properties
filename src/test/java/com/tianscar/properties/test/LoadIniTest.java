package com.tianscar.properties.test;

import com.tianscar.properties.IniProperties;

public class LoadIniTest {

    public static void main(String[] args) {
        try {
            IniProperties ini = new IniProperties();
            ini.load(LoadIniTest.class.getClassLoader().getResourceAsStream("test.ini"));
            IniProperties iniXML = new IniProperties();
            iniXML.loadFromXML(LoadIniTest.class.getClassLoader().getResourceAsStream("test.xml"));
            System.out.println("Equals: " + ini.equals(iniXML));
            ini.listAll(System.out);
        }
        catch (Throwable t) {
            throw new RuntimeException("LoadIni failed: \n" + t);
        }
    }

}
