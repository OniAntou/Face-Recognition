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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

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
    private final Map<Mat, PoolType> borrowedMats = Collections.synchronizedMap(new IdentityHashMap<>());

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
            Mat mat = genericPool.borrowObject();
            borrowedMats.put(mat, PoolType.GENERIC);
            return mat;
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
            Mat mat = byteMatPool.borrowObject();
            borrowedMats.put(mat, PoolType.BYTE_MAT);
            return mat;
        } catch (Exception e) {
            logger.debug("Byte pool exhausted, creating new Mat");
            return new Mat(1, 1, CvType.CV_8UC3);
        }
    }

    /**
     * Borrows a float Mat from the pool.
     * @return a Mat object for float data, or null if pool exhausted
     */
    public Mat borrowFloatMat() {
        try {
            Mat mat = floatMatPool.borrowObject();
            borrowedMats.put(mat, PoolType.FLOAT_MAT);
            return mat;
        } catch (Exception e) {
            logger.debug("Float pool exhausted, creating new Mat");
            return new Mat(1, 1, CvType.CV_32FC1);
        }
    }

    /**
     * Returns a Mat to the appropriate pool.
     * @param mat the Mat to return
     */
    public void returnMat(Mat mat) {
        if (mat == null) {
            return;
        }

        PoolType ownedType = borrowedMats.remove(mat);
        if (mat.nativeObj == 0) {
            safeRelease(mat);
            return;
        }

        if (ownedType != null) {
            if (returnToPool(mat, ownedType)) {
                return;
            }
            safeRelease(mat);
            return;
        }

        int type = mat.type();
        PoolType inferredType = type == CvType.CV_8UC3
                ? PoolType.BYTE_MAT
                : (type == CvType.CV_32F || type == CvType.CV_32FC1
                ? PoolType.FLOAT_MAT : PoolType.GENERIC);
        if (!returnToPool(mat, inferredType)) {
            safeRelease(mat);
        }
    }

    /**
     * Returns a Mat to a specific pool.
     * @param mat the Mat to return
     * @param poolType the type hint for the pool
     */
    public void returnMat(Mat mat, PoolType poolType) {
        if (mat == null) {
            return;
        }

        PoolType ownedType = borrowedMats.remove(mat);
        if (mat.nativeObj == 0) {
            safeRelease(mat);
            return;
        }

        PoolType target = poolType == PoolType.AUTO || poolType == null
                ? (ownedType != null ? ownedType : inferPoolType(mat))
                : poolType;
        if (!returnToPool(mat, target)) {
            safeRelease(mat);
        }
    }

    private PoolType inferPoolType(Mat mat) {
        int type = mat.type();
        if (type == CvType.CV_8UC3) {
            return PoolType.BYTE_MAT;
        }
        if (type == CvType.CV_32F || type == CvType.CV_32FC1) {
            return PoolType.FLOAT_MAT;
        }
        return PoolType.GENERIC;
    }

    private boolean returnToPool(Mat mat, PoolType poolType) {
        try {
            switch (poolType) {
                case BYTE_MAT -> byteMatPool.returnObject(mat);
                case FLOAT_MAT -> floatMatPool.returnObject(mat);
                case GENERIC -> genericPool.returnObject(mat);
                case AUTO -> {
                    return returnToPool(mat, inferPoolType(mat));
                }
            }
            return true;
        } catch (Exception e) {
            logger.debug("Could not return Mat to {} pool: {}", poolType, e.getMessage());
            return false;
        }
    }

    public int getActiveCount(PoolType poolType) {
        return switch (poolType) {
            case BYTE_MAT -> byteMatPool.getNumActive();
            case FLOAT_MAT -> floatMatPool.getNumActive();
            case GENERIC -> genericPool.getNumActive();
            case AUTO -> genericPool.getNumActive() + byteMatPool.getNumActive() + floatMatPool.getNumActive();
        };
    }

    @Override
    public void close() {
        logger.info("Closing MatPool - active objects: generic={}, byte={}, float={}",
                genericPool.getNumActive(), byteMatPool.getNumActive(), floatMatPool.getNumActive());

        genericPool.close();
        byteMatPool.close();
        floatMatPool.close();
        borrowedMats.clear();
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
            return new Mat(1, 1, CvType.CV_8UC3);
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
            Mat mat = p.getObject();
            if (mat != null && mat.nativeObj != 0) {
                mat.create(1, 1, CvType.CV_8UC3);
            }
        }
    }

    /**
     * Factory for float Mat objects.
     */
    private static class FloatMatFactory extends BasePooledObjectFactory<Mat> {
        @Override
        public Mat create() {
            return new Mat(1, 1, CvType.CV_32FC1);
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
            Mat mat = p.getObject();
            if (mat != null && mat.nativeObj != 0) {
                mat.create(1, 1, CvType.CV_32FC1);
            }
        }
    }
}
