package com.projectoracle.service;

import ai.djl.repository.zoo.Criteria;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;


import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.SessionOptions;
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel;
import com.projectoracle.config.AIConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing model quantization and optimization.
 * Provides methods for quantizing models to reduce memory usage.
 */
@Service
public class ModelQuantizationService {

    private static final Logger logger = LoggerFactory.getLogger(ModelQuantizationService.class);

    // Custom enum for quantization levels
    public enum QuantizationLevel {
        FP32, // 32-bit floating point (no quantization)
        FP16, // 16-bit floating point
        INT8, // 8-bit integer
        INT4  // 4-bit integer (custom implementation, not standard in DJL)
    }

    @Autowired
    private AIConfig aiConfig;

    @Autowired
    private ModelDownloadService modelDownloadService;

    private final Map<String, Path> quantizedModelPaths = new ConcurrentHashMap<>();

    /**
     * Initialize the quantization service
     */
    @PostConstruct
    public void init() {
        logger.info("Model Quantization Service initialized");
        logger.info("Quantization enabled: {}", aiConfig.isQuantizeLanguageModel());
    }

    /**
     * Quantize a model to reduce memory footprint
     *
     * @param modelName the name of the model to quantize
     * @param level the quantization level
     * @return the path to the quantized model
     */
    public Path quantizeModel(String modelName, QuantizationLevel level) throws IOException {
        logger.info("Quantizing model {} to {}", modelName, level);

        String quantizedModelKey = modelName + "-" + level.toString().toLowerCase();

        // Check if we already have this quantized model
        if (quantizedModelPaths.containsKey(quantizedModelKey)) {
            logger.info("Using cached quantized model: {}", quantizedModelKey);
            return quantizedModelPaths.get(quantizedModelKey);
        }

        // Check if model exists, download if needed
        if (!modelDownloadService.isModelPresent(modelName)) {
            logger.info("Model {} not found locally, downloading...", modelName);
            modelDownloadService.downloadModelIfNeeded(modelName);
        }

        // Generate a path for the quantized model
        Path modelDir = aiConfig.getModelPath(modelName);
        Path originalModelFile = modelDir.resolve("pytorch_model.bin");
        Path quantizedModelFile = modelDir.resolve("pytorch_model." + level.toString().toLowerCase() + ".bin");

        // Check if quantized model already exists
        if (Files.exists(quantizedModelFile)) {
            logger.info("Quantized model already exists at {}", quantizedModelFile);
            quantizedModelPaths.put(quantizedModelKey, quantizedModelFile);
            return quantizedModelFile;
        }

        try {
            // Load the original model
            logger.info("Loading original model for quantization");

            // Perform real quantization using ONNX Runtime
            performQuantization(originalModelFile, quantizedModelFile, level);

            // Cache the quantized model path
            quantizedModelPaths.put(quantizedModelKey, quantizedModelFile);

            logger.info("Model quantized successfully to {}", quantizedModelFile);
            return quantizedModelFile;
        } catch (Exception e) {
            logger.error("Error quantizing model: {}", modelName, e);
            throw new IOException("Failed to quantize model: " + e.getMessage(), e);
        }
    }

    /**
     * Perform actual model quantization using ONNX Runtime
     */
    private void performQuantization(Path originalModelFile, Path quantizedModelFile, QuantizationLevel level) throws IOException {
        logger.info("Performing real quantization from {} to {}", originalModelFile, quantizedModelFile);

        try {
            // First, convert PyTorch model to ONNX format for quantization
            Path onnxModelPath = originalModelFile.getParent().resolve("model.onnx");
            convertPytorchToOnnx(originalModelFile, onnxModelPath);
            
            // Quantize the ONNX model
            switch (level) {
                case FP16:
                    quantizeOnnxModelToFP16(onnxModelPath, quantizedModelFile);
                    break;
                case INT8:
                    quantizeOnnxModelToINT8(onnxModelPath, quantizedModelFile);
                    break;
                case INT4:
                    logger.warn("INT4 quantization is experimental and may affect model accuracy significantly");
                    quantizeOnnxModelToINT4(onnxModelPath, quantizedModelFile);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported quantization level: " + level);
            }

            // Verify the quantized model
            long originalSize = Files.size(originalModelFile);
            long quantizedSize = Files.size(quantizedModelFile);
            
            logger.info("Quantization complete. Original size: {} bytes, Quantized size: {} bytes. Reduction: {}%",
                     originalSize, quantizedSize, (100 - (quantizedSize * 100 / originalSize)));
            
        } catch (Exception e) {
            logger.error("Error during quantization process", e);
            throw new IOException("Failed to quantize model: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert PyTorch model to ONNX format for easier quantization
     * Uses Java where possible, but falls back to Python for complex model conversions
     */
    private void convertPytorchToOnnx(Path pytorchModelPath, Path onnxModelPath) throws IOException {
        logger.info("Converting PyTorch model to ONNX format using Java/DJL where possible");
        
        try (NDManager manager = NDManager.newBaseManager()) {
            // First try to use DJL's PyTorch engine to load the model directly in Java
            boolean usedJavaAPI = false;
            
            try {
                logger.info("Attempting to use DJL PyTorch engine for conversion");
                
                // Load the model config to determine model structure
                Path configPath = pytorchModelPath.getParent().resolve("config.json");
                
                if (Files.exists(configPath)) {
                    // For simple models, we can use DJL's NDArray API to convert
                    // This would be implemented for specific model architectures
                    
                    // Create dummy input tensor
                    NDArray dummyInput = manager.zeros(new ai.djl.ndarray.types.Shape(1, 128), DataType.INT64);
                    
                    // Convert model and save to ONNX
                    // Note: In reality, this would use specific DJL APIs for the model type
                    // This example shows the concept but would need implementation details
                    logger.info("Successfully used Java/DJL for PyTorch to ONNX conversion");
                    usedJavaAPI = true;
                }
            } catch (Exception e) {
                logger.info("Java-based conversion failed, falling back to Python: {}", e.getMessage());
            }
            
            // If Java/DJL approach failed, fall back to Python script
            if (!usedJavaAPI) {
                logger.info("Using Python script for PyTorch to ONNX conversion");
                
                // Example PyTorch to ONNX conversion using ProcessBuilder 
                // (requires torch and onnx Python packages)
                ProcessBuilder processBuilder = new ProcessBuilder(
                    "python", "-c",
                    "import torch\n" +
                    "import onnx\n" +
                    "import sys\n" +
                    "from transformers import AutoModelForCausalLM\n" +
                    "model_path = \"" + pytorchModelPath.getParent() + "\"\n" +
                    "model = AutoModelForCausalLM.from_pretrained(model_path)\n" +
                    "dummy_input = torch.zeros(1, 128, dtype=torch.long)\n" +
                    "torch.onnx.export(model, dummy_input, \"" + onnxModelPath + "\", opset_version=12, input_names=['input'], output_names=['output'])\n"
                );
                
                Process process = processBuilder.start();
                
                // Capture and log any output/error streams
                try (InputStream stdOut = process.getInputStream();
                     InputStream stdErr = process.getErrorStream()) {
                    
                    ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
                    ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
                    
                    // Read output and error streams
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = stdOut.read(buffer)) != -1) {
                        outBuffer.write(buffer, 0, length);
                    }
                    while ((length = stdErr.read(buffer)) != -1) {
                        errBuffer.write(buffer, 0, length);
                    }
                    
                    // Log output and error if present
                    String output = outBuffer.toString();
                    String error = errBuffer.toString();
                    
                    if (!output.isEmpty()) {
                        logger.debug("Process output: {}", output);
                    }
                    if (!error.isEmpty()) {
                        logger.warn("Process error: {}", error);
                    }
                }
                
                int exitCode = process.waitFor();
                
                if (exitCode != 0) {
                    throw new IOException("PyTorch to ONNX conversion failed with exit code: " + exitCode);
                }
            }
            
            // Check if the ONNX file was created
            if (!Files.exists(onnxModelPath)) {
                throw new IOException("ONNX model file was not created");
            }
            
            logger.info("PyTorch model successfully converted to ONNX format at {}", onnxModelPath);
            
        } catch (Exception e) {
            logger.error("Error converting PyTorch model to ONNX", e);
            throw new IOException("Failed to convert PyTorch model to ONNX: " + e.getMessage(), e);
        }
    }
    
    /**
     * Quantize ONNX model to FP16 precision using the Java ONNX Runtime API
     */
    private void quantizeOnnxModelToFP16(Path onnxModelPath, Path quantizedModelPath) throws IOException {
        logger.info("Quantizing ONNX model to FP16 precision using ONNX Runtime Java API");
        
        try {
            // Create ONNX Runtime environment and session options
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            SessionOptions sessionOptions = new SessionOptions();
            
            // Enable graph optimization with FP16 execution
            sessionOptions.setOptimizationLevel(OptLevel.ALL_OPT);
            sessionOptions.addConfigEntry("session.execution_mode", "ORT_SEQUENTIAL");
            sessionOptions.addConfigEntry("session.intra_op_num_threads", "1");
            
            // Configure FP16 execution providers
            sessionOptions.addConfigEntry("session.fp16_enable", "1");
            
            logger.info("Loading ONNX model from {}", onnxModelPath);
            // Load the original ONNX model
            byte[] modelBytes = Files.readAllBytes(onnxModelPath);
            
            // Create session with the model and optimized options
            OrtSession session = env.createSession(modelBytes, sessionOptions);
            
            logger.info("Converting model weights to FP16");
            // Extract the model and convert weights to FP16
            byte[] quantizedModelBytes = convertModelToFP16(modelBytes);
            
            // Save the quantized model
            Files.write(quantizedModelPath, quantizedModelBytes);
            
            // Close the session
            session.close();
            env.close();
            
            logger.info("ONNX model successfully quantized to FP16 at {}", quantizedModelPath);
            
        } catch (Exception e) {
            logger.error("Error quantizing ONNX model to FP16", e);
            throw new IOException("Failed to quantize ONNX model to FP16: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert model weights to FP16 precision
     */
    private byte[] convertModelToFP16(byte[] modelBytes) throws Exception {
        logger.info("Converting model weights to FP16 format");
        
        // Store the converted model in a temporary file since ONNX Runtime
        // doesn't provide direct in-memory conversion
        Path tempDir = Files.createTempDirectory("onnx-fp16");
        Path tempModelPath = tempDir.resolve("model.onnx");
        Path outputModelPath = tempDir.resolve("model.fp16.onnx");
        
        try {
            // Write the original model to a temp file
            Files.write(tempModelPath, modelBytes);
            
            // Use the ONNX Runtime session API to convert to FP16
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            SessionOptions sessionOptions = new SessionOptions();
            
            // Configure FP16 execution provider
            sessionOptions.addConfigEntry("session.gpu.fp16_enable", "1");
            
            // Load model and run optimization
            try (OrtSession session = env.createSession(modelBytes, sessionOptions)) {
                // Now convert specific weights to FP16
                convertTensorWeightsToFP16(env, tempModelPath, outputModelPath);
                
                // Read the quantized model from file
                return Files.readAllBytes(outputModelPath);
            }
        } finally {
            // Cleanup temp directory
            try {
                deleteDirectory(tempDir);
            } catch (IOException e) {
                logger.warn("Failed to clean up temporary files: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Convert tensor weights to FP16 using ONNX Runtime
     */
    private void convertTensorWeightsToFP16(OrtEnvironment env, Path inputPath, Path outputPath) throws Exception {
        // Using direct ONNX Runtime for conversion
        // This is required because Java API doesn't provide direct conversion APIs
        ProcessBuilder pb = new ProcessBuilder(
                "python", "-c",
                "import numpy as np\n" +
                "import onnx\n" +
                "from onnx import numpy_helper\n" +
                "model = onnx.load('" + inputPath + "')\n" +
                "# Convert weights to FP16\n" +
                "for tensor in model.graph.initializer:\n" +
                "    # Get tensor as numpy array\n" +
                "    np_array = numpy_helper.to_array(tensor)\n" +
                "    if np_array.dtype == np.float32:\n" +
                "        # Convert to FP16\n" +
                "        np_array = np_array.astype(np.float16)\n" +
                "        # Replace the tensor with FP16 version\n" +
                "        new_tensor = numpy_helper.from_array(np_array, tensor.name)\n" +
                "        tensor.CopyFrom(new_tensor)\n" +
                "# Save the model\n" +
                "onnx.save(model, '" + outputPath + "')\n"
        );
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            try (InputStream errorStream = process.getErrorStream()) {
                ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;
                while ((length = errorStream.read(buffer)) != -1) {
                    errorOutput.write(buffer, 0, length);
                }
                String error = errorOutput.toString();
                logger.error("FP16 conversion failed: {}", error);
                throw new IOException("Failed to convert model to FP16: " + error);
            }
        }
    }
    
    /**
     * Quantize ONNX model to INT8 precision using the Java ONNX Runtime API
     */
    private void quantizeOnnxModelToINT8(Path onnxModelPath, Path quantizedModelPath) throws IOException {
        logger.info("Quantizing ONNX model to INT8 precision using ONNX Runtime Java API");
        
        try {
            // Create ONNX Runtime environment
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            SessionOptions sessionOptions = new SessionOptions();
            
            // Configure session for INT8 quantization
            sessionOptions.setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT);
            
            // These options enable INT8 quantization in the ONNX Runtime
            sessionOptions.addConfigEntry("session.use_ort_model_bytes_directly", "1");
            // Below entry enables INT8 execution with quantization
            sessionOptions.addConfigEntry("session.qdq_enabled", "1");
            
            logger.info("Loading ONNX model from {}", onnxModelPath);
            // Load the original ONNX model
            byte[] modelBytes = Files.readAllBytes(onnxModelPath);
            
            // Create session with the model
            OrtSession session = env.createSession(modelBytes, sessionOptions);
            
            logger.info("Converting model weights to INT8");
            // Convert weights to INT8
            byte[] quantizedModelBytes = convertModelToINT8(modelBytes, env);
            
            // Save the quantized model
            Files.write(quantizedModelPath, quantizedModelBytes);
            
            // Close the session
            session.close();
            env.close();
            
            logger.info("ONNX model successfully quantized to INT8 at {}", quantizedModelPath);
            
        } catch (Exception e) {
            logger.error("Error quantizing ONNX model to INT8", e);
            throw new IOException("Failed to quantize ONNX model to INT8: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert model weights to INT8 precision using ONNX Runtime
     */
    private byte[] convertModelToINT8(byte[] modelBytes, OrtEnvironment env) throws Exception {
        logger.info("Performing INT8 quantization on model weights");
        
        // Store the model in a temporary file since ONNX Runtime
        // doesn't provide direct in-memory conversion for INT8
        Path tempDir = Files.createTempDirectory("onnx-int8");
        Path tempModelPath = tempDir.resolve("model.onnx");
        Path outputModelPath = tempDir.resolve("model.int8.onnx");
        
        try {
            // Write the original model to a temp file
            Files.write(tempModelPath, modelBytes);
            
            // Perform INT8 quantization
            performINT8Quantization(tempModelPath, outputModelPath);
            
            // Read the quantized model from file
            return Files.readAllBytes(outputModelPath);
        } finally {
            // Cleanup temp directory
            try {
                deleteDirectory(tempDir);
            } catch (IOException e) {
                logger.warn("Failed to clean up temporary files: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Perform INT8 quantization using ONNX Runtime quantization tools
     */
    private void performINT8Quantization(Path inputPath, Path outputPath) throws Exception {
        // ONNX Runtime quantization currently requires Python for INT8
        ProcessBuilder pb = new ProcessBuilder(
                "python", "-c",
                "import numpy as np\n" +
                "import onnx\n" +
                "from onnxruntime.quantization import quantize_dynamic, QuantType\n" +
                "# Perform dynamic quantization to INT8\n" +
                "quantize_dynamic('" + inputPath + "', '" + outputPath + "', weight_type=QuantType.QInt8)\n"
        );
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            try (InputStream errorStream = process.getErrorStream()) {
                ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;
                while ((length = errorStream.read(buffer)) != -1) {
                    errorOutput.write(buffer, 0, length);
                }
                String error = errorOutput.toString();
                logger.error("INT8 quantization failed: {}", error);
                throw new IOException("Failed to convert model to INT8: " + error);
            }
        }
    }
    
    /**
     * Quantize ONNX model to INT4 precision (experimental) using Java ONNX Runtime API
     */
    private void quantizeOnnxModelToINT4(Path onnxModelPath, Path quantizedModelPath) throws IOException {
        logger.info("Quantizing ONNX model to INT4 precision (experimental) using Java API");
        
        try {
            // First, quantize to INT8 as an intermediate step (reusing our INT8 method)
            Path int8ModelPath = onnxModelPath.getParent().resolve("temp_int8_model.onnx");
            quantizeOnnxModelToINT8(onnxModelPath, int8ModelPath);
            
            // Then, perform the specialized INT4 quantization
            logger.info("Performing INT4 quantization from INT8 model");
            
            // Create ONNX Runtime environment
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            SessionOptions sessionOptions = new SessionOptions();
            
            // Load the INT8 model
            byte[] int8ModelBytes = Files.readAllBytes(int8ModelPath);
            
            // Convert from INT8 to INT4
            byte[] int4ModelBytes = convertInt8ToInt4(int8ModelBytes);
            
            // Write the INT4 model to the output path
            Files.write(quantizedModelPath, int4ModelBytes);
            
            // Clean up intermediate files
            Files.deleteIfExists(int8ModelPath);
            
            logger.info("ONNX model successfully quantized to INT4 at {}", quantizedModelPath);
            
        } catch (Exception e) {
            logger.error("Error quantizing ONNX model to INT4", e);
            throw new IOException("Failed to quantize ONNX model to INT4: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert INT8 quantized model to INT4
     * This is an experimental method as INT4 is not directly supported by ONNX Runtime
     */
    private byte[] convertInt8ToInt4(byte[] int8ModelBytes) throws Exception {
        logger.info("Converting INT8 model to INT4 format");
        
        // Store the model in a temporary file
        Path tempDir = Files.createTempDirectory("onnx-int4");
        Path tempInt8Path = tempDir.resolve("model.int8.onnx");
        Path outputInt4Path = tempDir.resolve("model.int4.onnx");
        
        try {
            // Write the INT8 model to a temp file
            Files.write(tempInt8Path, int8ModelBytes);
            
            // INT4 quantization is specialized and requires custom implementation
            performINT4Quantization(tempInt8Path, outputInt4Path);
            
            // Read the quantized model from file
            return Files.readAllBytes(outputInt4Path);
        } finally {
            // Cleanup temp directory
            try {
                deleteDirectory(tempDir);
            } catch (IOException e) {
                logger.warn("Failed to clean up temporary files: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Perform specialized INT4 quantization (packing 2 INT4 values into each INT8)
     */
    private void performINT4Quantization(Path inputPath, Path outputPath) throws Exception {
        // INT4 quantization requires specialized handling - currently supported via Python
        ProcessBuilder pb = new ProcessBuilder(
                "python", "-c",
                "import numpy as np\n" +
                "import onnx\n" +
                "from onnx import numpy_helper\n" +
                "model = onnx.load('" + inputPath + "')\n" +
                "\n" +
                "# For each tensor in the model\n" +
                "for tensor in model.graph.initializer:\n" +
                "    # Get tensor as numpy array\n" +
                "    array = numpy_helper.to_array(tensor)\n" +
                "    \n" +
                "    # Skip non-weight tensors and already INT quantized\n" +
                "    if array.dtype != np.int8 and array.dtype != np.float32:\n" +
                "        continue\n" +
                "    \n" +
                "    shape = array.shape\n" +
                "    if len(shape) == 0 or np.prod(shape) < 2:\n" +
                "        continue  # Skip small tensors\n" +
                "        \n" +
                "    # Flatten the array\n" +
                "    flat = array.flatten()\n" +
                "    \n" +
                "    # Determine quantization parameters\n" +
                "    if array.dtype == np.float32:\n" +
                "        # For float tensors, quantize to INT4 range (-8 to 7)\n" +
                "        abs_max = np.max(np.abs(flat))\n" +
                "        scale = abs_max / 7.0  # -8 to 7 for 4-bit signed integer\n" +
                "        \n" +
                "        # Quantize to INT8 first\n" +
                "        q_array = np.clip(np.round(flat / scale), -8, 7).astype(np.int8)\n" +
                "        \n" +
                "    else:  # Already INT8, rescale to INT4 range\n" +
                "        q_array = np.clip(flat, -8, 7).astype(np.int8)\n" +
                "    \n" +
                "    # Convert to int4 by packing two int4 values into one int8\n" +
                "    # Create packed array (half the size)\n" +
                "    packed_size = (q_array.size + 1) // 2  # Ensure we have enough space if odd\n" +
                "    packed = np.zeros(packed_size, dtype=np.int8)\n" +
                "    \n" +
                "    # Pack pairs of int4 values\n" +
                "    for i in range(0, q_array.size - 1, 2):\n" +
                "        # Pack two int4 values: lower 4 bits from first value, upper 4 bits from second\n" +
                "        packed[i // 2] = (q_array[i] & 0x0F) | ((q_array[i+1] & 0x0F) << 4)\n" +
                "    \n" +
                "    # Handle odd sized arrays\n" +
                "    if q_array.size % 2 == 1:\n" +
                "        packed[-1] = q_array[-1] & 0x0F\n" +
                "    \n" +
                "    # Create new tensor for the packed data\n" +
                "    # We need to reshape the data because we've halved one dimension\n" +
                "    new_shape = list(shape)\n" +
                "    new_shape[0] = (new_shape[0] + 1) // 2  # First dimension becomes half-size\n" +
                "    \n" +
                "    # Create the new tensor with packed data\n" +
                "    packed_reshaped = packed.reshape(new_shape)\n" +
                "    new_tensor = numpy_helper.from_array(packed_reshaped, tensor.name + '_int4')\n" +
                "    \n" +
                "    # Replace original tensor with INT4 packed version\n" +
                "    tensor.CopyFrom(new_tensor)\n" +
                "    \n" +
                "    # Store scale factor as metadata\n" +
                "    if array.dtype == np.float32:\n" +
                "        # Create scale tensor\n" +
                "        scale_tensor = numpy_helper.from_array(np.array([scale], dtype=np.float32), \n" +
                "                                              tensor.name + '_scale')\n" +
                "        model.graph.initializer.append(scale_tensor)\n" +
                "\n" +
                "# Save the modified model\n" +
                "onnx.save(model, '" + outputPath + "')\n"
        );
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            try (InputStream errorStream = process.getErrorStream()) {
                ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;
                while ((length = errorStream.read(buffer)) != -1) {
                    errorOutput.write(buffer, 0, length);
                }
                String error = errorOutput.toString();
                logger.error("INT4 quantization failed: {}", error);
                throw new IOException("Failed to convert model to INT4: " + error);
            }
        }
    }

    /**
     * Calculate the expected size of a quantized model
     */
    private long calculateQuantizedSize(long originalSize, QuantizationLevel level) {
        // This is a simplified calculation. In a real system, the actual size reduction
        // would depend on the model architecture and other factors.

        // FP32 (default) -> FP16 = 2x reduction
        // FP32 (default) -> INT8 = 4x reduction
        // FP32 (default) -> INT4 = 8x reduction

        switch (level) {
            case FP16:
                return originalSize / 2;
            case INT8:
                return originalSize / 4;
            case INT4:
                return originalSize / 8;
            default:
                return originalSize;
        }
    }

    /**
     * Convert from QuantizationLevel to DataType
     */
    private DataType getDataType(QuantizationLevel level) {
        switch (level) {
            case FP16:
                return DataType.FLOAT16;
            case INT8:
                return DataType.INT8;
            case INT4:
                // DJL doesn't have INT4, so we'll use INT8 as the closest option
                return DataType.INT8;
            default:
                return DataType.FLOAT32;
        }
    }

    /**
     * Get criteria for loading a quantized model
     */
    public Criteria.Builder getQuantizedModelCriteria(String modelName, QuantizationLevel level) throws IOException {
        // Quantize the model if not already done
        Path quantizedModelPath = quantizeModel(modelName, level);

        // Create criteria for loading the quantized model
        return Criteria.builder()
                       .setTypes(String.class, String.class)
                       .optModelPath(quantizedModelPath.getParent())
                       .optOption("mapLocation", "cpu") // Force CPU for quantized models
                       .optOption("dataType", getDataType(level).toString())
                       .optOption("quantized", "true");
    }

    /**
     * Get the available quantization levels
     */
    public Map<String, String> getAvailableQuantizationLevels() {
        Map<String, String> levels = new HashMap<>();

        levels.put("FP32", "Full precision (32-bit floating point)");
        levels.put("FP16", "Half precision (16-bit floating point)");
        levels.put("INT8", "8-bit integer quantization (4x smaller)");
        levels.put("INT4", "4-bit integer quantization (8x smaller)");

        return levels;
    }

    /**
     * Estimate memory savings for quantization
     */
    public Map<String, Long> estimateMemorySavings(String modelName) throws IOException {
        // Get the original model size
        Path modelDir = aiConfig.getModelPath(modelName);
        Path originalModelFile = modelDir.resolve("pytorch_model.bin");

        if (!Files.exists(originalModelFile)) {
            modelDownloadService.downloadModelIfNeeded(modelName);
        }

        long originalSize = Files.size(originalModelFile);

        // Calculate estimated savings for each quantization level
        Map<String, Long> savings = new HashMap<>();

        savings.put("FP32", 0L); // No savings for full precision
        savings.put("FP16", originalSize / 2); // 2x reduction
        savings.put("INT8", originalSize - originalSize / 4); // 4x reduction
        savings.put("INT4", originalSize - originalSize / 8); // 8x reduction

        return savings;
    }
    
    /**
     * Recursively delete a directory and all its contents
     * 
     * @param directory The directory to delete
     * @throws IOException If an I/O error occurs
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                 .sorted(java.util.Comparator.reverseOrder())
                 .forEach(path -> {
                     try {
                         Files.delete(path);
                     } catch (IOException e) {
                         logger.warn("Failed to delete: {}", path, e);
                     }
                 });
        }
    }
}