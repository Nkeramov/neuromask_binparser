package com.neuromask.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class NeuromaskBinParser {
    private static final Logger LOGGER = LogManager.getLogger(NeuromaskBinParser.class);
    private static final String INPUT_PATH = ".//input//";
    private static final String OUTPUT_PATH = ".//output//";
    private static final int DEFAULT_FLOAT_PRECISION = 3;

    public static void main(String[] args) {
        System.setProperty("log4j2.configurationFile",
                String.valueOf(NeuromaskBinParser.class.getClassLoader().getResource("log4j2.xml")));
        processFiles(INPUT_PATH);
    }

    private static void processFiles(String dirPath){
        File dir = new File(dirPath);
        File[] fileList = dir.listFiles();
        if (fileList != null) {
            for (File file : fileList) {
                String fileName = file.getName();
                if (fileName.toLowerCase().endsWith(".bin"))
                    processSingleFile(fileName);
            }
        }
    }

    private static void processSingleFile(String fileName){
        Map<Byte, String> entryFields = new HashMap<Byte, String>() {{
            put((byte) 0x10, "Содержание углекислого газа");
            put((byte) 0x11, "Содержание кислорода в выдохе");
            put((byte) 0x12, "Температура тела");
            put((byte) 0x13, "Влажность");
            put((byte) 0x14, "Индекс летучих органических соединений в выдохе (TVOC)");
            put((byte) 0x15, "Атмосферное давление");
            put((byte) 0x16, "Температура внешняя");
            put((byte) 0x17, "Показания электрокардиограммы");
            put((byte) 0x18, "IMU ax");
            put((byte) 0x19, "IMU ay");
            put((byte) 0x1A, "IMU az");
            put((byte) 0x1B, "IMU gx");
            put((byte) 0x1C, "IMU gy");
            put((byte) 0x1D, "IMU gz");
            put((byte) 0x1E, "IMU mx");
            put((byte) 0x1F, "IMU my");
            put((byte) 0x20, "IMU mz");
            put((byte) 0x21, "Фотоплетизмограмма красный");
            put((byte) 0x22, "Фотоплетизмограмма ИК");
            put((byte) 0x23, "Фотоплетизмограмма зеленый");
            put((byte) 0x24, "Уровень наполненности крови кислородом (SpO2)");
            put((byte) 0x25, "Высота");
            put((byte) 0x26, "ЧСС");
            put((byte) 0x27, "Шаги");
            put((byte) 0x28, "Заряд АКБ");
            put((byte) 0x29, "Индекс летучих органических соединений в выдохе (TVOC) (наружный датчик)");
            put((byte) 0x2A, "Содержание углекислого газа.(наружный датчик)");
            put((byte) 0x2B, "Влажность (наружный датчик)");
            put((byte) 0x00, "Резерв");
        }};
        List<String> outputJsons = new ArrayList<>();
        int bufSize = 512;
        try(BufferedInputStream fin = new BufferedInputStream(Files.newInputStream(
                Paths.get(INPUT_PATH + fileName)), bufSize))
        {
            LOGGER.info(String.format("File name: %s", fileName));
            LOGGER.info(String.format("File size: %d bytes", fin.available()));
            byte[] c = {0, 0, 0, 0};
            int m = 0;
            for(int i = 0; i < 3 && m == 0; i++) {
                int x = fin.read();
                if (x < 0)
                    m++;
                c[i] = (byte)(x & 0xff);
            }
            if (m == 0) {
                List<byte[]> entrie = new ArrayList<>();
                ByteArrayOutputStream tmpEntry = new ByteArrayOutputStream();
                byte f = 0;
                int x = fin.read();
                while (x != -1) {
                    c[3] = (byte)(x & 0xff);
                    if (c[0] == (byte)0xF1 && c[1] == (byte)0xAA && c[2] == (byte)0xF0 && c[3] == (byte)0xAA) {
                        tmpEntry.write(c[0]);
                        tmpEntry.write(c[1]);
                        entrie.add(tmpEntry.toByteArray());
                        tmpEntry.reset();
                        f = 1;
                    }
                    else {
                        if (f == 0)
                            tmpEntry.write(c[0]);
                        else
                            f = 0;
                    }
                    for(int i = 0; i < 4 - 1; i++) {
                        c[i] = c[i + 1];
                    }
                    x = fin.read();
                }
                entrie.add(tmpEntry.toByteArray());
                LOGGER.info(String.format("Found %d entries", entrie.size()));
                for(byte[] entry : entrie) {
                    int j = 0;
                    for(byte b:entry) {
                        if ((j < 11) || j > 11 && ((j - 11) % 5 != 0))
                            b = reverseByte(b);
                        j++;
                    }
                    int entrySize = ((entry[3] & 0xff) << 8) | (entry[2] & 0xff);
                    int entryId = ((entry[6] & 0xff) << 16) | (entry[5] & 0xff) << 8 | (entry[4] & 0xff);
                    int entryTimestamp = ((entry[10] & 0xff) << 24) | ((entry[9] & 0xff) << 16) | (entry[8] & 0xff) << 8 | (entry[7] & 0xff);
                    LOGGER.info(String.format("size = %d | id = %d | time = %d", entrySize, entryId, entryTimestamp));
                    ObjectMapper mapper = new ObjectMapper();
                    ObjectNode rootNode = mapper.createObjectNode();
                    rootNode.put("size", Integer.toString(entrySize));
                    rootNode.put("uuid", Integer.toString(entryId));
                    rootNode.put("_time", Integer.toString(entryTimestamp));
                    for(int i = 11; i <= entry.length - 5 - 2; i += 5) {
                        if (entryFields.containsKey(entry[i])) {
                            int asInt = (entry[i+1] & 0xFF) | ((entry[i+2] & 0xFF) << 8) | ((entry[i+3] & 0xFF) << 16) | ((entry[i+4] & 0xFF) << 24);
                            float asFloat = roundToNDecimalPlaces(Float.intBitsToFloat(asInt), DEFAULT_FLOAT_PRECISION);
                            LOGGER.info(String.format("%s | %s", entryFields.get(entry[i]), String.format("%." + DEFAULT_FLOAT_PRECISION + "f", asFloat)));
                            rootNode.put(entryFields.get(entry[i]), Float.toString(asFloat));
                        }
                        else
                        {
                            LOGGER.error(String.format("%02X key not found", entry[i]));
                        }
                    }
                    try {
                        outputJsons.add(mapper.writer().writeValueAsString(rootNode));
                    }
                    catch (JsonProcessingException e) {
                        LOGGER.error("JSON processing error {}", e.getMessage());
                    }
                }
            }
        }
        catch(IOException e){
            LOGGER.error(String.format("Bin file %s reading error", fileName), e.getMessage());
        }

        try (FileWriter writer = new FileWriter(OUTPUT_PATH + fileName.replace("bin", "json"))) {
            for(String str: outputJsons) {
                writer.write(str + System.lineSeparator());
            }
            LOGGER.info(String.format("Bin file %s successfully processed, JSON written", fileName));
        }
        catch(Exception e){
            LOGGER.error("JSON file writing error {}", e.getMessage());
        }
    }

    private static byte reverseByte(byte b) {
        return (byte)(((b << 4) & 0xF0) | ((b >> 4) & 0x0F));
    }

    private static float roundToNDecimalPlaces(float value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = new BigDecimal(Double.toString(value));
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.floatValue();
    }

}