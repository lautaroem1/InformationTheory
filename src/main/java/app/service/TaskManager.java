package app.service;

import app.model.ApplicationExecutionSettings;
import app.model.ProtectionCustomSetting;
import app.model.TimeLockSettings;
import app.service.timelock.TimeLocker;
import hamming.lib.Hamming;
import hamming.lib.services.Intoxicator;
import huffman.lib.Compressor;
import huffman.lib.Decompressor;
import huffman.lib.models.CompressionResult;

import java.io.File;
import java.nio.file.Files;
import java.util.BitSet;

public class TaskManager {
    public static long runApplicationWithSettings(ApplicationExecutionSettings settings) throws Error {
        validatePath(settings.getSourcePath(), "Invalid Source Path");
        validatePath(settings.getOutputPath(), "Invalid Output Path");
        String sourcePath = settings.getSourcePath();
        String outputPath = buildOutputPath(settings);

        long startTime = System.nanoTime();

        File sourceFile = new File(sourcePath);
        byte[] dataBytes;
        try {
            dataBytes = Files.readAllBytes(sourceFile.toPath());
        } catch (Exception e) {
            throw new Error("Failed to read file.");
        }
        switch (settings.getOperations()) {
            case PROTECT:
                dataBytes = timeLockData(
                        protectData(
                                dataBytes,
                                settings.getProtectionLevel(),
                                settings.getProtectionCustomSetting()
                        ),
                        settings.getTimeLockSettings()
                );
                break;
            case UNLOCK:
                dataBytes = unlockData(
                        timeUnlockData(dataBytes),
                        settings.getProtectionLevel(),
                        settings.getProtectionCustomSetting()
                );
                break;
            case COMPRESS:
                dataBytes = timeLockData(
                        compressData(dataBytes),
                        settings.getTimeLockSettings()
                );
                break;
            case DECOMPRESS:
                dataBytes = decompressData(
                        timeUnlockData(dataBytes)
                );
                break;
            case PROTECT_AND_COMPRESS:
                dataBytes = timeLockData(
                        protectData(
                                compressData(dataBytes),
                                settings.getProtectionLevel(),
                                settings.getProtectionCustomSetting()
                        ),
                        settings.getTimeLockSettings()
                );
                break;
            case UNLOCK_AND_DECOMPRESS:
                dataBytes = decompressData(
                        unlockData(
                                timeUnlockData(dataBytes),
                                settings.getProtectionLevel(),
                                settings.getProtectionCustomSetting()
                        ));
                break;
        }
        try {
            Files.write(new File(outputPath).toPath(), dataBytes);
        } catch (Exception e) {
            throw new Error("Failed to write file.");
        }
        return ((System.nanoTime() - startTime) / 1000000);
    }

    private static String buildOutputPath(ApplicationExecutionSettings settings) {
        return settings.getOutputPath() + "." + ExtensionBuilder.buildFrom(settings);
    }

    private static byte[] timeLockData(byte[] dataBytes, TimeLockSettings timeLockSettings) throws Error {
        if (timeLockSettings.isEnabled()) {
            if (timeLockSettings.getUnlockDate() != null) {
                return TimeLocker.lock(dataBytes, timeLockSettings.getUnlockDate());
            } else {
                throw new Error("A Lock Date must be specified.");
            }
        } else {
            return TimeLocker.buildUnlockedFile(dataBytes);
        }
    }

    private static byte[] timeUnlockData(byte[] dataBytes) throws Error {
        return TimeLocker.unlock(dataBytes);
    }

    private static byte[] protectData(byte[] dataBytes, int protectionLevel, ProtectionCustomSetting customSetting) {
        Hamming hammingUtility = new Hamming(protectionLevel);
        BitSet dataBits = BitSet.valueOf(dataBytes);
        BitSet outputBits = hammingUtility.encode(dataBits);
        if (customSetting == ProtectionCustomSetting.ADD_RANDOM_ERROR) {
            Intoxicator.flipRandomBitsInChunks(outputBits, protectionLevel);
        }
        return outputBits.toByteArray();
    }

    private static byte[] unlockData(byte[] dataBytes, int protectionLevel, ProtectionCustomSetting customSetting) {
        Hamming hammingUtility = new Hamming(protectionLevel);
        BitSet dataBits = BitSet.valueOf(dataBytes);
        boolean correct = customSetting == ProtectionCustomSetting.CORRECT_ERRORS;
        BitSet outputBits = hammingUtility.decode(dataBits, correct);
        return outputBits.toByteArray();
    }

    private static byte[] compressData(byte[] dataBytes) throws Error {
        try {
            return Compressor.compress(dataBytes).toByteArray();
        } catch (Exception e) {
            throw new Error("Failed to compress data");
        }
    }

    private static byte[] decompressData(byte[] dataBytes) throws Error {
        try {
            CompressionResult cr = CompressionResult.fromByteArray(dataBytes);
            return Decompressor.decompress(cr);
        } catch (Exception e) {
            throw new Error("Failed to decompress data.");
        }

    }

    private static void validatePath(String path, String message) {
        if (path == null || path.trim().equals("")) {
            throw new Error(message);
        }
    }
}
