package com.neuromask.binparser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;


public class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);
    private static final String INPUT_PATH = ".//input//";
    private static final String OUTPUT_PATH = ".//output//";

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
                processDirFiles(INPUT_PATH);
            }
        }
        else
            LOGGER.error("Input dir does not exist");
    }

    private static void processDirFiles(String dirPath){
        File dir = new File(dirPath);
        File[] fileList = dir.listFiles();
        if (fileList != null) {
            for (File file : fileList) {
                String fileName = file.getName();
                if (fileName.toLowerCase().endsWith(".bin"))
                    try (InputStream in = Files.newInputStream(Paths.get(INPUT_PATH + fileName))) {
                        List<String> records = NeuromaskBinParser.parseStream(in, NeuromaskBinParser.DEFAULT_FLOAT_PRECISION);
                        String newFileName = fileName.toLowerCase().replace("bin", "json");
                        try (FileWriter writer = new FileWriter(OUTPUT_PATH + newFileName)) {
                            for(String str: records) {
                                writer.write(str + System.lineSeparator());
                            }
                            LOGGER.info(String.format("BIN-file %s successfully processed, data written to JSON-file %s", fileName, newFileName));
                        }
                        catch(Exception e) {
                            LOGGER.error("JSON-file writing error {}", e.getMessage());
                        }
                        LOGGER.info(String.format("BIN-file %s successfully processed", fileName));
                    }
                    catch(IOException e) {
                        LOGGER.error(String.format("BIN-file %s reading error", fileName), e.getMessage());
                    }
            }
        }
    }
}
