import com.tianscar.properties.IniProperties;

public class LoadIniTest {

    public static void main(String[] args) {
        try {
            IniProperties ini = new IniProperties();
            ini.load(LoadIniTest.class.getResourceAsStream("test.ini"));
            System.out.println();
            System.out.println("test.ini");
            System.out.println();
            ini.listAll(System.out);
            IniProperties iniXML = new IniProperties();
            iniXML.loadFromXML(LoadIniTest.class.getResourceAsStream("test.xml"));
            System.out.println();
            System.out.println("test.xml");
            System.out.println();
            iniXML.listAll(System.out);
        }
        catch (Throwable t) {
            throw new RuntimeException("LoadIni failed: \n" + t);
        }
    }

}
