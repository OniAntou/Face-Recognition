package com.example.facedetection.util;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.facedetection.util.MatUtils.safeRelease;

/**
 * Object pool for reusable OpenCV Mat objects to reduce memory allocation pressure.
 * Provides pools for common Mat types used in image processing.
 */
public class MatPool implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(MatPool.class);

    private final GenericObjectPool<Mat> genericPool;
    private final GenericObjectPool<Mat> byteMatPool;
    private final GenericObjectPool<Mat> floatMatPool;

    private static final int DEFAULT_MAX_TOTAL = 20;
    private static final int DEFAULT_MAX_IDLE = 10;

    public MatPool() {
        this(DEFAULT_MAX_TOTAL, DEFAULT_MAX_IDLE);
    }

    public MatPool(int maxTotal, int maxIdle) {
        // Generic Mat pool
        GenericObjectPoolConfig<Mat> genericConfig = new GenericObjectPoolConfig<>();
        genericConfig.setMaxTotal(maxTotal);
        genericConfig.setMaxIdle(maxIdle);
        genericConfig.setMinIdle(2);
        genericConfig.setBlockWhenExhausted(false);
        genericConfig.setTestOnBorrow(true);
        genericConfig.setTestOnReturn(true);

        genericPool = new GenericObjectPool<>(new MatFactory(), genericConfig);

        // CV_8UC3 Mat pool (for RGB/BGR images)
        GenericObjectPoolConfig<Mat> byteConfig = new GenericObjectPoolConfig<>();
        byteConfig.setMaxTotal(maxTotal);
        byteConfig.setMaxIdle(maxIdle);
        byteConfig.setMinIdle(2);
        byteConfig.setBlockWhenExhausted(false);

        byteMatPool = new GenericObjectPool<>(new ByteMatFactory(), byteConfig);

        // Float Mat pool (for normalized data)
        GenericObjectPoolConfig<Mat> floatConfig = new GenericObjectPoolConfig<>();
        floatConfig.setMaxTotal(maxTotal / 2);
        floatConfig.setMaxIdle(maxIdle / 2);
        floatConfig.setMinIdle(1);
        floatConfig.setBlockWhenExhausted(false);

        floatMatPool = new GenericObjectPool<>(new FloatMatFactory(), floatConfig);

        logger.info("MatPool initialized with maxTotal={}, maxIdle={}", maxTotal, maxIdle);
    }

    /**
     * Borrows a generic Mat from the pool.
     * @return a Mat object, or null if pool exhausted
     */
    public Mat borrowGeneric() {
        try {
            return genericPool.borrowObject();
        } catch (Exception e) {
            logger.debug("Generic pool exhausted, creating new Mat");
            return new Mat();
        }
    }

    /**
     * Borrows a CV_8UC3 Mat from the pool.
     * @return a Mat object with CV_8UC3 type, or null if pool exhausted
     */
    public Mat borrowByteMat() {
        try {
            return byteMatPool.borrowObject();
        } catch (Exception e) {
            logger.debug("Byte pool exhausted, creating new Mat");
            return new Mat();
        }
    }

    /**
     * Borrows a float Mat from the pool.
     * @return a Mat object for float data, or null if pool exhausted
     */
    public Mat borrowFloatMat() {
        try {
            return floatMatPool.borrowObject();
        } catch (Exception e) {
            logger.debug("Float pool exhausted, creating new Mat");
            return new Mat();
        }
    }

    /**
     * Returns a Mat to the appropriate pool.
     * @param mat the Mat to return
     */
    public void returnMat(Mat mat) {
        if (mat == null || mat.nativeObj == 0) {
            return;
        }

        if (mat.empty()) {
            safeRelease(mat);
            return;
        }

        int type = mat.type();
        try {
            if (type == CvType.CV_8UC3) {
                byteMatPool.returnObject(mat);
                return;
            } else if (type == CvType.CV_32F || type == CvType.CV_32FC1) {
                floatMatPool.returnObject(mat);
                return;
            }
        } catch (IllegalStateException ignored) {
            // Not from specific pool, try generic
        }

        try {
            genericPool.returnObject(mat);
        } catch (Exception e) {
            // Not from any pool or pool closed
            safeRelease(mat);
        }
    }

    /**
     * Returns a Mat to a specific pool.
     * @param mat the Mat to return
     * @param poolType the type hint for the pool
     */
    public void returnMat(Mat mat, PoolType poolType) {
        if (mat == null || mat.nativeObj == 0) {
            return;
        }

        if (mat.empty()) {
            safeRelease(mat);
            return;
        }

        try {
            switch (poolType) {
                case BYTE_MAT -> byteMatPool.returnObject(mat);
                case FLOAT_MAT -> floatMatPool.returnObject(mat);
                case GENERIC -> genericPool.returnObject(mat);
                default -> returnMat(mat); // Auto-detect
            }
        } catch (IllegalStateException e) {
            // If it failed the specific pool, try generic as last resort
            try {
                genericPool.returnObject(mat);
            } catch (Exception ex) {
                safeRelease(mat);
            }
        } catch (Exception e) {
            safeRelease(mat);
        }
    }

    @Override
    public void close() {
        logger.info("Closing MatPool - active objects: generic={}, byte={}, float={}",
                genericPool.getNumActive(), byteMatPool.getNumActive(), floatMatPool.getNumActive());

        genericPool.close();
        byteMatPool.close();
        floatMatPool.close();
    }


    /**
     * Pool type hints for returning Mats.
     */
    public enum PoolType {
        GENERIC,
        BYTE_MAT,
        FLOAT_MAT,
        AUTO
    }

    /**
     * Factory for generic Mat objects.
     */
    private static class MatFactory extends BasePooledObjectFactory<Mat> {
        @Override
        public Mat create() {
            return new Mat();
        }

        @Override
        public PooledObject<Mat> wrap(Mat mat) {
            return new DefaultPooledObject<>(mat);
        }

        @Override
        public boolean validateObject(PooledObject<Mat> p) {
            Mat mat = p.getObject();
            return mat != null && mat.nativeObj != 0;
        }

        @Override
        public void destroyObject(PooledObject<Mat> p) {
            safeRelease(p.getObject());
        }

        @Override
        public void activateObject(PooledObject<Mat> p) {
            // Mat is ready to use
        }

        @Override
        public void passivateObject(PooledObject<Mat> p) {
            // DO NOT release here. We want to keep the buffer.
            // If we want to ensure it's not "dirty" we could clear it,
            // but usually OpenCV ops overwrite the data anyway.
        }
    }

    /**
     * Factory for CV_8UC3 Mat objects.
     */
    private static class ByteMatFactory extends BasePooledObjectFactory<Mat> {
        @Override
        public Mat create() {
            return new Mat();
        }

        @Override
        public PooledObject<Mat> wrap(Mat mat) {
            return new DefaultPooledObject<>(mat);
        }

        @Override
        public boolean validateObject(PooledObject<Mat> p) {
            Mat mat = p.getObject();
            return mat != null && mat.nativeObj != 0 && mat.type() == CvType.CV_8UC3;
        }

        @Override
        public void destroyObject(PooledObject<Mat> p) {
            safeRelease(p.getObject());
        }

        @Override
        public void passivateObject(PooledObject<Mat> p) {
            // Keep buffer for reuse
        }
    }

    /**
     * Factory for float Mat objects.
     */
    private static class FloatMatFactory extends BasePooledObjectFactory<Mat> {
        @Override
        public Mat create() {
            return new Mat();
        }

        @Override
        public PooledObject<Mat> wrap(Mat mat) {
            return new DefaultPooledObject<>(mat);
        }

        @Override
        public boolean validateObject(PooledObject<Mat> p) {
            Mat mat = p.getObject();
            return mat != null && mat.nativeObj != 0 && (mat.type() == CvType.CV_32F || mat.type() == CvType.CV_32FC1);
        }

        @Override
        public void destroyObject(PooledObject<Mat> p) {
            safeRelease(p.getObject());
        }

        @Override
        public void passivateObject(PooledObject<Mat> p) {
            // Keep buffer for reuse
        }
    }
}
