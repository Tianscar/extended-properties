import com.tianscar.properties.IniProperties;

import java.io.IOException;

public class LoadIniTest {

    public static void main(String[] args) {
        try {
            IniProperties ini = new IniProperties();
            ini.load(LoadIniTest.class.getResourceAsStream("test.ini"));
            ini.listAll(System.out);
        }
        catch (IOException e) {
            throw new RuntimeException("LoadIni failed: " + e);
        }
    }

}
