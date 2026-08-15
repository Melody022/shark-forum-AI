package com.itswy.paicodingai.skilltree;

import lombok.Data;

/**
 * 分类结果
 */
@Data
public class ClassifyResult {

    /** 意图ID */
    private String intent;

    /** 置信度（0-1） */
    private double confidence;

    /** 分类方法 */
    private String method;

    /** 匹配详情 */
    private String matchDetail;

    public ClassifyResult(String intent, double confidence, String method, String matchDetail) {
        this.intent = intent;
        this.confidence = confidence;
        this.method = method;
        this.matchDetail = matchDetail;
    }

    /**
     * 是否是高置信度结果
     */
    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }

    /**
     * 是否是中置信度结果
     */
    public boolean isMediumConfidence() {
        return confidence >= 0.6 && confidence < 0.8;
    }

    /**
     * 是否是低置信度结果
     */
    public boolean isLowConfidence() {
        return confidence < 0.6;
    }

    @Override
    public String toString() {
        return String.format("ClassifyResult{intent='%s', confidence=%.2f, method='%s'}",
            intent, confidence, method);
    }
}
