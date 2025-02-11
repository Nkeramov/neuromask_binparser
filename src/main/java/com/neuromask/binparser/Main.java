package com.neuromask.binparser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;


public class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);
    private static final String INPUT_PATH = ".//input//";
    private static final String OUTPUT_PATH = ".//output//";

    public static void main(String[] args) {
        System.setProperty("log4j2.configurationFile",
                String.valueOf(NeuromaskBinParser.class.getClassLoader().getResource("log4j2.xml")));
        Path inputDirPath = Paths.get(INPUT_PATH);
        Path outputDirPath = Paths.get(OUTPUT_PATH);

        if (!Files.isDirectory(inputDirPath)) {
            LOGGER.error("Input directory '{}' does not exist.", inputDirPath);
            return;
        }

        createOutputDirectory(outputDirPath);

        if (!Files.isDirectory(outputDirPath)) {
            LOGGER.error("Could not create output directory '{}'", outputDirPath);
            return;
        }

        processBinFiles(inputDirPath, outputDirPath);

    }

    private static void processBinFiles(Path inputDirPath, Path outputDirPath) {
        try {
            Files.list(inputDirPath)
                    .filter(path -> path.toString().toLowerCase().endsWith(".bin"))
                    .forEach(binFile -> processBinFile(binFile, outputDirPath));
        } catch (IOException e) {
            LOGGER.error("Error processing files in input directory: {}", e.getMessage());

        }
    }

    private static void processBinFile(Path binFile, Path outputDirPath) {
        LOGGER.info("BIN-file '{}' processing started", binFile);
        String fileName = binFile.getFileName().toString();
        String jsonFileName = fileName.toLowerCase().replace(".bin", ".json");
        Path jsonFilePath = outputDirPath.resolve(jsonFileName);
        try (InputStream in = Files.newInputStream(binFile)) {
            List<String> records = NeuromaskBinParser.parseStream(in, NeuromaskBinParser.DEFAULT_FLOAT_PRECISION);
            Files.write(jsonFilePath, records, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            LOGGER.info("BIN-file '{}' successfully processed, data written to JSON-file '{}'", fileName,
                    jsonFilePath);
        } catch (IOException e) {
            LOGGER.error("Error processing BIN-file '{}': {}", fileName, e.getMessage());
        }
    }

    private static void createOutputDirectory(Path outputDirPath) {
        try {
            Files.createDirectories(outputDirPath);
        } catch (IOException e) {
            LOGGER.error("Error creating output directory: {}", e.getMessage());
        }
    }
}
