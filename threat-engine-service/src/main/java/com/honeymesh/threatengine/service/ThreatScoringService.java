package com.honeymesh.threatengine.service;

import com.honeymesh.threatengine.model.CorrelationSnapshot;
import com.honeymesh.threatengine.model.RiskLevel;
import com.honeymesh.threatengine.model.ThreatAssessment;
import com.honeymesh.threatengine.model.ThreatLevel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ThreatScoringService {

    private static final int BASE_POINTS_LOW = 15;
    private static final int BASE_POINTS_MEDIUM = 30;
    private static final int BASE_POINTS_HIGH = 45;
    private static final int BASE_POINTS_CRITICAL = 60;

    private static final int BONUS_HITS_5_PLUS = 20;
    private static final int BONUS_HITS_3_TO_4 = 10;

    private static final int BONUS_DECOYS_3_PLUS = 20;
    private static final int BONUS_DECOYS_2 = 10;

    public static final int MAX_SCORE = 100;

    public static final int THRESHOLD_SUSPICIOUS = 25;
    public static final int THRESHOLD_HIGH = 50;
    public static final int THRESHOLD_CRITICAL = 75;

    public ThreatAssessment assess(CorrelationSnapshot snapshot) {
        if (snapshot == null) {
            return new ThreatAssessment(0, ThreatLevel.INFORMATIONAL, List.of());
        }

        int rawScore = 0;
        List<String> reasons = new ArrayList<>();

        RiskLevel highestRisk = snapshot.highestRiskLevel() != null ? snapshot.highestRiskLevel() : RiskLevel.LOW;
        int basePoints = getBaseRiskPoints(highestRisk);
        if (basePoints > 0) {
            rawScore += basePoints;
            reasons.add("Highest decoy risk in the 5-minute window: " + highestRisk.name() + " (+" + basePoints + ")");
        }

        int hitCount = snapshot.recentHitCount();
        if (hitCount >= 5) {
            rawScore += BONUS_HITS_5_PLUS;
            reasons.add("5 or more decoy interactions within 5 minutes (+" + BONUS_HITS_5_PLUS + ")");
        } else if (hitCount >= 3) {
            rawScore += BONUS_HITS_3_TO_4;
            reasons.add("3 to 4 decoy interactions within 5 minutes (+" + BONUS_HITS_3_TO_4 + ")");
        }

        int distinctDecoys = snapshot.distinctDecoyCount();
        if (distinctDecoys >= 3) {
            rawScore += BONUS_DECOYS_3_PLUS;
            reasons.add("3 or more distinct decoys accessed within 5 minutes (+" + BONUS_DECOYS_3_PLUS + ")");
        } else if (distinctDecoys == 2) {
            rawScore += BONUS_DECOYS_2;
            reasons.add("2 distinct decoys accessed within 5 minutes (+" + BONUS_DECOYS_2 + ")");
        }

        int finalScore = Math.min(MAX_SCORE, rawScore);

        ThreatLevel level = mapScoreToLevel(finalScore);

        return new ThreatAssessment(finalScore, level, List.copyOf(reasons));
    }

    public ThreatLevel mapScoreToLevel(int score) {
        if (score >= THRESHOLD_CRITICAL) {
            return ThreatLevel.CRITICAL;
        } else if (score >= THRESHOLD_HIGH) {
            return ThreatLevel.HIGH;
        } else if (score >= THRESHOLD_SUSPICIOUS) {
            return ThreatLevel.SUSPICIOUS;
        } else {
            return ThreatLevel.INFORMATIONAL;
        }
    }

    private int getBaseRiskPoints(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> BASE_POINTS_LOW;
            case MEDIUM -> BASE_POINTS_MEDIUM;
            case HIGH -> BASE_POINTS_HIGH;
            case CRITICAL -> BASE_POINTS_CRITICAL;
        };
    }
}
