import com.tianscar.properties.IniProperties;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SaveIniTest {

    public static void main(String[] args) {
        try {
            IniProperties ini = new IniProperties();
            ini.switchSection("section_1");
            ini.setProperty("val0", "0");
            ini.switchSection(null);
            ini.setProperty("val", "null");
            ini.switchSection(".section_2");
            ini.setProperty("val2", "2");
            ini.switchSection(".section_3");
            ini.setProperty("val3", "3");
            ini.store(new FileWriter("out.ini"));
            ini.storeToXML(Files.newOutputStream(Paths.get("out.xml")));
        }
        catch (Throwable t) {
            throw new RuntimeException("SaveIni failed: \n" + t);
        }
    }

}
