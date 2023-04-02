import com.tianscar.iniproperties.IniProperties;

public class LoadIniTest {

    public static void main(String[] args) {
        try {
            IniProperties ini = new IniProperties();
            ini.load(LoadIniTest.class.getResourceAsStream("test.ini"));
            IniProperties iniXML = new IniProperties();
            iniXML.loadFromXML(LoadIniTest.class.getResourceAsStream("test.xml"));
            System.out.println("Equals: " + ini.equals(iniXML));
            ini.listAll(System.out);
        }
        catch (Throwable t) {
            throw new RuntimeException("LoadIni failed: \n" + t);
        }
    }

}
