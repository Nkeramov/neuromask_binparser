import java.io.*;
import java.util.ArrayList;
import java.util.List;

import com.neuromask.binparser.NeuromaskBinParser;
import org.junit.Test;

import static net.javacrumbs.jsonunit.JsonAssert.assertJsonEquals;
import static net.javacrumbs.jsonunit.JsonAssert.when;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static org.junit.Assert.assertEquals;


public class NeuromaskBinParserTest {

    private void testFile(String fileName) throws IOException{
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);
        List<String> parseRecords = NeuromaskBinParser.parseStream(in, NeuromaskBinParser.DEFAULT_FLOAT_PRECISION);
        String newFileName = fileName.toLowerCase().replace("bin", "json");
        BufferedReader br = new BufferedReader(new FileReader("src/test/resources/" + newFileName));
        List<String> expectedRecords = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            expectedRecords.add(line);
        }
        br.close();
        assertEquals(parseRecords, expectedRecords);
        assertJsonEquals(parseRecords, expectedRecords, when(IGNORING_ARRAY_ORDER));
    }

    @Test
    public void testParser1() throws IOException {
        testFile("test-1.bin");
    }

    @Test
    public void testParser2() throws IOException {
        testFile("test-2.bin");
    }
}
