package com.neuromask.binparser;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;



public class NeuromaskBinParser {
    private static final Logger LOGGER = LogManager.getLogger(NeuromaskBinParser.class);
    public static final int DEFAULT_FLOAT_PRECISION = 6;
    private static final int DEFAULT_READING_BUFFER_SIZE = 1024;


    public static List<String> parseStream(InputStream stream, int floatPrecision) throws  IOException{
        List<String> output = new ArrayList<>();
        try(BufferedInputStream fin = new BufferedInputStream(stream, DEFAULT_READING_BUFFER_SIZE)) {
            if (fin.available() >= 2) {
                List<byte[]> entries = new ArrayList<>();
                int x_old = fin.read();
                int x = fin.read();
                byte[] buf = {(byte)x_old, (byte)x};
                while (x != -1) {
                    while (x != -1 && bytesToShort(buf) != NeuromaskRecord.RECORD_START_MARKER) {
                        buf[0] = buf[1];
                        x = fin.read();
                        buf[1] = (byte)(x & 0xFF);
                    }
                    if (x >= 0) {
                        ByteArrayOutputStream tmpEntry = new ByteArrayOutputStream();
                        while (x != -1 && bytesToShort(buf) != NeuromaskRecord.RECORD_END_MARKER) {
                            tmpEntry.write(buf[0]);
                            buf[0] = buf[1];
                            x = fin.read();
                            buf[1] = (byte) (x & 0xFF);
                        }
                        if (x >= 0) {
                            tmpEntry.write(buf);
                            entries.add(tmpEntry.toByteArray());
                        }
                    }
                }
                LOGGER.debug("Found {} entries", entries.size());
                ObjectMapper mapper = new ObjectMapper();
                mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
                mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
                for(byte[] entry : entries) {
                    int entrySize = ((entry[3] & 0xFF) << 8) | (entry[2] & 0xFF);
                    // 6 = len(size_field) + len(START_BYTES) + len(END_BYTES)
                    if (entrySize == entry.length - 6) {
                        int entryId = ((entry[6] & 0xFF) << 16) | (entry[5] & 0xFF) << 8 | (entry[4] & 0xFF);
                        int entryTimestamp = ((entry[10] & 0xFF) << 24) | ((entry[9] & 0xFF) << 16) |
                                (entry[8] & 0xFF) << 8 | (entry[7] & 0xFF);
                        LOGGER.debug("size = {}, id = {}, time = {}", entrySize, entryId, entryTimestamp);
                        LOGGER.debug("entry actual size: {}", entry.length);
                        StringBuilder sb = new StringBuilder();
                        for (byte b : entry) {
                            sb.append(String.format("%02X ", b & 0xFF));
                        }
                        String hexString = sb.toString().trim();
                        LOGGER.debug("entry bytes: {}", hexString);
                        NeuromaskRecord rec = new NeuromaskRecord(entryTimestamp, entryId, entrySize);
                        for (int i = 11; i <= entry.length - 5 - 2; i += 5) {
                            if (NeuromaskRecord.RECORD_FIELDS.containsKey(entry[i])) {
                                int asInt = (entry[i + 1] & 0xFF) | ((entry[i + 2] & 0xFF) << 8) |
                                        ((entry[i + 3] & 0xFF) << 16) | ((entry[i + 4] & 0xFF) << 24);
                                float asFloat = Float.intBitsToFloat(asInt);
                                DecimalFormat df = new DecimalFormat("0." + String.valueOf('#')
                                        .repeat(floatPrecision));
                                LOGGER.debug("{}: {}", NeuromaskRecord.RECORD_FIELDS.get(entry[i]),
                                        df.format(asFloat));
                                rec.addParameter(NeuromaskRecord.RECORD_FIELDS.get(entry[i]), df.format(asFloat));
                            } else {
                                LOGGER.warn(String.format("Key %02X not found in neuromask record fields", entry[i]));
                            }
                        }
                        try {
                            output.add(mapper.writeValueAsString(rec));
                        } catch (JsonProcessingException e) {
                            LOGGER.error("JSON processing error {}", e.getMessage());
                        }
                    }
                    else  {
                        LOGGER.warn("Packet with incorrect size value");
                    }
                }
            }
        }
        return output;
    }

    public static List<String> parseStream(InputStream stream) throws  IOException{
        return parseStream(stream, DEFAULT_FLOAT_PRECISION);
    }

    private static byte swapNibbles(byte b) {
        return (byte)(((b << 4) & 0xF0) | ((b >> 4) & 0x0F));
    }

    public static byte[] shortToBytes(short value) {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) (value >>> 8);
        bytes[1] = (byte) value;
        return bytes;
    }

    public static short bytesToShort(byte[] bytes) {
        if (bytes == null || bytes.length != 2) {
            throw new IllegalArgumentException("Array must contain exactly 2 bytes");
        }
        return (short) ((bytes[0] << 8) | (bytes[1] & 0xFF));
    }

}