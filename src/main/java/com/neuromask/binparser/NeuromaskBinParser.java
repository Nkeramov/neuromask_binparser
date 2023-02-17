package com.neuromask.binparser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final int DEFAULT_BUFFER_SIZE = 1024;


    public static void main(String[] args) {
        System.setProperty("log4j2.configurationFile",
                String.valueOf(NeuromaskBinParser.class.getClassLoader().getResource("log4j2.xml")));
        Path inputPath = Paths.get(INPUT_PATH);
        if (Files.isDirectory(inputPath)) {
            Path outputPath = Paths.get(OUTPUT_PATH);
            if (!Files.exists(outputPath)) {
                try {
                    Files.createDirectories(outputPath);
                }
                catch (IOException e) {
                    LOGGER.error("Output dir could not be created");
                }
            }
            if (Files.isDirectory(outputPath)) {
                processFiles(INPUT_PATH);
            }
        }
        else
            LOGGER.error("Input dir does not exist");
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
            put((byte) 0x10, "Exhaled carbon dioxide content");
            put((byte) 0x11, "Exhaled oxygen content");
            put((byte) 0x12, "Body temperature");
            put((byte) 0x13, "Humidity of exhalation");
            put((byte) 0x14, "Index of volatile organic compounds (TVOC) in exhalation");
            put((byte) 0x15, "Atmosphere pressure");
            put((byte) 0x16, "Outside temperature");
            put((byte) 0x17, "Electrocardiogram readings");
            put((byte) 0x18, "IMU ax");
            put((byte) 0x19, "IMU ay");
            put((byte) 0x1A, "IMU az");
            put((byte) 0x1B, "IMU gx");
            put((byte) 0x1C, "IMU gy");
            put((byte) 0x1D, "IMU gz");
            put((byte) 0x1E, "IMU mx");
            put((byte) 0x1F, "IMU my");
            put((byte) 0x20, "IMU mz");
            put((byte) 0x21, "Photoplethysmogram red");
            put((byte) 0x22, "Photoplethysmogram IR");
            put((byte) 0x23, "Photoplethysmogram green");
            put((byte) 0x24, "Blood oxygen level (SpO2)");
            put((byte) 0x25, "Height");
            put((byte) 0x26, "Heart rate");
            put((byte) 0x27, "Steps");
            put((byte) 0x28, "Battery charge");
            put((byte) 0x29, "Exhaled Volatile Organic Compound (TVOC) Index (external sensor)");
            put((byte) 0x2A, "Carbon dioxide content (external sensor)");
            put((byte) 0x2B, "Outside humidity");
            put((byte) 0x00, "Reserved");
        }};
        List<String> outputJsons = new ArrayList<>();
        int bufSize = DEFAULT_BUFFER_SIZE;
        try(BufferedInputStream fin = new BufferedInputStream(Files.newInputStream(
                Paths.get(INPUT_PATH + fileName)), bufSize)) {
            LOGGER.info(String.format("BIN-file %s processing started", fileName));
            byte[] c = {0, 0, 0, 0};
            int m = 0;
            for(int i = 0; i < 3 && m == 0; i++) {
                int x = fin.read();
                if (x < 0)
                    m++;
                c[i] = (byte)(x & 0xff);
            }
            if (m == 0) {
                List<byte[]> entries = new ArrayList<>();
                ByteArrayOutputStream tmpEntry = new ByteArrayOutputStream();
                byte f = 0;
                int x = fin.read();
                while (x != -1) {
                    c[3] = (byte)(x & 0xff);
                    if (c[0] == (byte)0xF1 && c[1] == (byte)0xAA && c[2] == (byte)0xF0 && c[3] == (byte)0xAA) {
                        tmpEntry.write(c[0]);
                        tmpEntry.write(c[1]);
                        entries.add(tmpEntry.toByteArray());
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
                entries.add(tmpEntry.toByteArray());
                LOGGER.info(String.format("Found %d entries", entries.size()));
                for(byte[] entry : entries) {
                    int j = 0;
                    for(byte b:entry) {
                        if ((j < 11) || j > 11 && ((j - 11) % 5 != 0))
                            b = reverseByte(b);
                        j++;
                    }
                    int entrySize = ((entry[3] & 0xff) << 8) | (entry[2] & 0xff);
                    int entryId = ((entry[6] & 0xff) << 16) | (entry[5] & 0xff) << 8 | (entry[4] & 0xff);
                    int entryTimestamp = ((entry[10] & 0xff) << 24) | ((entry[9] & 0xff) << 16) | (entry[8] & 0xff) << 8 | (entry[7] & 0xff);
                    LOGGER.info(String.format("size = %d, id = %d, time = %d", entrySize, entryId, entryTimestamp));
                    ObjectMapper mapper = new ObjectMapper();
                    ObjectNode rootNode = mapper.createObjectNode();
                    rootNode.put("size", entrySize);
                    rootNode.put("uuid", entryId);
                    rootNode.put("_time", entryTimestamp);
                    for(int i = 11; i <= entry.length - 5 - 2; i += 5) {
                        if (entryFields.containsKey(entry[i])) {
                            int asInt = (entry[i+1] & 0xFF) | ((entry[i+2] & 0xFF) << 8) | ((entry[i+3] & 0xFF) << 16) | ((entry[i+4] & 0xFF) << 24);
                            float asFloat = roundToNDecimalPlaces(Float.intBitsToFloat(asInt), DEFAULT_FLOAT_PRECISION);
                            LOGGER.info(String.format("%s: %s", entryFields.get(entry[i]), String.format("%." + DEFAULT_FLOAT_PRECISION + "f", asFloat)));
                            rootNode.put(entryFields.get(entry[i]), asFloat);
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
        catch(IOException e) {
            LOGGER.error(String.format("BIN-file %s reading error", fileName), e.getMessage());
        }
        String newFileName = fileName.toLowerCase().replace("bin", "json");
        try (FileWriter writer = new FileWriter(OUTPUT_PATH + newFileName)) {
            for(String str: outputJsons) {
                writer.write(str + System.lineSeparator());
            }
            LOGGER.info(String.format("BIN-file %s successfully processed, data written to JSON-file %s", fileName, newFileName));
        }
        catch(Exception e) {
            LOGGER.error("JSON-file writing error {}", e.getMessage());
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