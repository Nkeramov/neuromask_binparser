import java.io.*;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.neuromask.binparser.NeuromaskBinParser;
import org.junit.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public class NeuromaskBinParserTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    private void testFile(String fileName) throws IOException{
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);
        List<String> parseRecords = NeuromaskBinParser.parseStream(in, NeuromaskBinParser.DEFAULT_FLOAT_PRECISION);

        // Чтение ожидаемых данных из JSON-файла
        String newFileName = fileName.toLowerCase().replace("bin", "json");
        BufferedReader br = new BufferedReader(new FileReader("src/test/resources/" + newFileName));
        String line;
        ArrayNode expectedJson = objectMapper.createArrayNode();
        while ((line = br.readLine()) != null) {
            expectedJson.add(objectMapper.readTree(line));
        }
        br.close();

        // Преобразование списка строк в массив JSON-объектов
        ArrayNode actualJson = objectMapper.createArrayNode();
        for (String record : parseRecords) {
            actualJson.add(objectMapper.readTree(record));
        }

        assertEquals(expectedJson, actualJson);
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
