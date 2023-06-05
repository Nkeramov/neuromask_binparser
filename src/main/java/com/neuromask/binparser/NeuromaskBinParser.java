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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;



public class NeuromaskBinParser {
    private static final Logger LOGGER = LogManager.getLogger(NeuromaskBinParser.class);
    public static final int DEFAULT_FLOAT_PRECISION = 3;
    private static final int DEFAULT_READING_BUFFER_SIZE = 1024;

    private static final short RECORD_START_MARKER = (short) 0xF0AA;
    private static final short RECORD_END_MARKER = (short) 0xF1AA;


    public static List<String> parseStream(InputStream stream, int precision) throws  IOException{
        List<String> output = new ArrayList<>();
        try(BufferedInputStream fin = new BufferedInputStream(stream, DEFAULT_READING_BUFFER_SIZE)) {
            int bufProcessingSize = 4;
            byte[] c = new byte[bufProcessingSize];
            Arrays.fill(c, (byte)0);
            int m = 0;
            for(int i = 0; i < bufProcessingSize - 1 && m == 0; i++) {
                int x = fin.read();
                if (x < 0)
                    m++;
                c[i] = (byte)(x & 0xFF);
            }
            if (m == 0) {
                List<byte[]> entries = new ArrayList<>();
                ByteArrayOutputStream tmpEntry = new ByteArrayOutputStream();
                byte flag = 0;
                int x = fin.read();
                while (x != -1) {
                    c[bufProcessingSize - 1] = (byte)(x & 0xFF);
                    if (((short)((c[0] & 0xFF) << 8) | (c[1] & 0xFF)) == RECORD_END_MARKER &&
                            ((short)((c[2] & 0xFF) << 8) | (c[3] & 0xFF)) == RECORD_START_MARKER){
                    //if (c[0] == (byte)0xF1 && c[1] == (byte)0xAA && c[2] == (byte)0xF0 && c[3] == (byte)0xAA) {
                        tmpEntry.write(c, 0, 2);
//                        tmpEntry.write(c[0]);
//                        tmpEntry.write(c[1]);
                        entries.add(tmpEntry.toByteArray());
                            tmpEntry.reset();
                            flag = 1;

                    }
                    else {
                        if (flag == 0)
                            tmpEntry.write(c[0]);
                        else
                            flag = 0;
                    }
                    for(int i = 0; i < bufProcessingSize - 1; i++) {
                        c[i] = c[i + 1];
                    }
                    x = fin.read();
                }
                if (tmpEntry.size() > 0) {
                    tmpEntry.write((byte)0xF1);
                    tmpEntry.write((byte)0xAA);
                    entries.add(tmpEntry.toByteArray());
                }
                entries.add(tmpEntry.toByteArray());
                LOGGER.debug(String.format("Found %d entries", entries.size()));
                ObjectMapper mapper = new ObjectMapper();
                mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
                mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
                for(byte[] entry : entries) {
                    int j = 0;
                    for(byte b:entry) {
                        if ((j < 11) || j > 11 && ((j - 11) % 5 != 0))
                            b = reverseByte(b);
                        j++;
                    }
                    int entrySize = ((entry[3] & 0xFF) << 8) | (entry[2] & 0xFF);
                    int entryId = ((entry[6] & 0xFF) << 16) | (entry[5] & 0xFF) << 8 | (entry[4] & 0xFF);
                    int entryTimestamp = ((entry[10] & 0xFF) << 24) | ((entry[9] & 0xFF) << 16) | (entry[8] & 0xFF) << 8 | (entry[7] & 0xFF);
                    LOGGER.debug(String.format("size = %d, id = %d, time = %d", entrySize, entryId, entryTimestamp));
                    NeuromaskRecord rec = new NeuromaskRecord(entryTimestamp, entryId, entrySize);
                    for(int i = 11; i <= entry.length - 5 - 2; i += 5) {
                        if (NeuromaskRecord.RECORD_PARAMETERS.containsKey(entry[i])) {
                            int asInt = (entry[i + 1] & 0xFF) | ((entry[i + 2] & 0xFF) << 8) | ((entry[i + 3] & 0xFF) << 16) | ((entry[i + 4] & 0xFF) << 24);
                            float asFloat = roundToNDecimalPlaces(Float.intBitsToFloat(asInt), precision);
                            LOGGER.debug(String.format("%s: %s", NeuromaskRecord.RECORD_PARAMETERS.get(entry[i]), String.format("%." + precision + "flag", asFloat)));
                            rec.addParameter(NeuromaskRecord.RECORD_PARAMETERS.get(entry[i]), asFloat);
                        } else {
                            LOGGER.warn(String.format("%02X key not found", entry[i]));
                        }
                    }
                    try {
                        output.add(mapper.writeValueAsString(rec));
                    }
                    catch (JsonProcessingException e) {
                        LOGGER.error("JSON processing error {}", e.getMessage());
                    }
                }
            }
        }
        return output;
    }

    public static List<String> parseStream(InputStream stream) throws  IOException{
        return parseStream(stream, DEFAULT_FLOAT_PRECISION);
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