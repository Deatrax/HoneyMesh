package com.honeymesh.threatengine.service;

import com.honeymesh.threatengine.model.CorrelationSnapshot;
import com.honeymesh.threatengine.model.RiskLevel;
import com.honeymesh.threatengine.model.ThreatAssessment;
import com.honeymesh.threatengine.model.ThreatLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ThreatScoringServiceTest {

    private ThreatScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService = new ThreatScoringService();
    }

    @Test
    @DisplayName("Case 1: LOW risk, 1 hit, 1 decoy -> score 15, INFORMATIONAL")
    void testCase1LowRiskSingleHit() {
        CorrelationSnapshot snapshot = new CorrelationSnapshot(1, 1, RiskLevel.LOW);

        ThreatAssessment assessment = scoringService.assess(snapshot);

        assertThat(assessment.score()).isEqualTo(15);
        assertThat(assessment.level()).isEqualTo(ThreatLevel.INFORMATIONAL);
        assertThat(assessment.reasons()).containsExactly(
                "Highest decoy risk in the 5-minute window: LOW (+15)"
        );
    }

    @Test
    @DisplayName("Case 2: MEDIUM risk, 3 hits, 1 decoy -> score 40, SUSPICIOUS")
    void testCase2MediumRiskThreeHits() {
        CorrelationSnapshot snapshot = new CorrelationSnapshot(3, 1, RiskLevel.MEDIUM);

        ThreatAssessment assessment = scoringService.assess(snapshot);

        assertThat(assessment.score()).isEqualTo(40);
        assertThat(assessment.level()).isEqualTo(ThreatLevel.SUSPICIOUS);
        assertThat(assessment.reasons()).containsExactly(
                "Highest decoy risk in the 5-minute window: MEDIUM (+30)",
                "3 to 4 decoy interactions within 5 minutes (+10)"
        );
    }

    @Test
    @DisplayName("Case 3: HIGH risk, 3 hits, 2 decoys -> score 65, HIGH")
    void testCase3HighRiskThreeHitsTwoDecoys() {
        CorrelationSnapshot snapshot = new CorrelationSnapshot(3, 2, RiskLevel.HIGH);

        ThreatAssessment assessment = scoringService.assess(snapshot);

        assertThat(assessment.score()).isEqualTo(65);
        assertThat(assessment.level()).isEqualTo(ThreatLevel.HIGH);
        assertThat(assessment.reasons()).containsExactly(
                "Highest decoy risk in the 5-minute window: HIGH (+45)",
                "3 to 4 decoy interactions within 5 minutes (+10)",
                "2 distinct decoys accessed within 5 minutes (+10)"
        );
    }

    @Test
    @DisplayName("Case 4: CRITICAL risk, 5 hits, 2 decoys -> score 90, CRITICAL")
    void testCase4CriticalRiskFiveHitsTwoDecoys() {
        CorrelationSnapshot snapshot = new CorrelationSnapshot(5, 2, RiskLevel.CRITICAL);

        ThreatAssessment assessment = scoringService.assess(snapshot);

        assertThat(assessment.score()).isEqualTo(90);
        assertThat(assessment.level()).isEqualTo(ThreatLevel.CRITICAL);
        assertThat(assessment.reasons()).containsExactly(
                "Highest decoy risk in the 5-minute window: CRITICAL (+60)",
                "5 or more decoy interactions within 5 minutes (+20)",
                "2 distinct decoys accessed within 5 minutes (+10)"
        );
    }

    @Test
    @DisplayName("Case 5: CRITICAL risk, 5+ hits, 3+ decoys -> raw score 100 capped at 100, CRITICAL")
    void testCase5CapScoreAt100() {
        CorrelationSnapshot snapshot = new CorrelationSnapshot(10, 5, RiskLevel.CRITICAL);

        ThreatAssessment assessment = scoringService.assess(snapshot);

        // Raw score: 60 (CRITICAL) + 20 (5+ hits) + 20 (3+ decoys) = 100
        assertThat(assessment.score()).isEqualTo(100);
        assertThat(assessment.level()).isEqualTo(ThreatLevel.CRITICAL);
        assertThat(assessment.reasons()).hasSize(3);
    }

    @Test
    @DisplayName("Score capping test: Raw score > 100 is capped at 100")
    void testRawScoreExceeding100IsCapped() {
        // Hypothetical huge numbers
        CorrelationSnapshot snapshot = new CorrelationSnapshot(100, 50, RiskLevel.CRITICAL);

        ThreatAssessment assessment = scoringService.assess(snapshot);

        assertThat(assessment.score()).isEqualTo(100);
        assertThat(assessment.level()).isEqualTo(ThreatLevel.CRITICAL);
    }

    @ParameterizedTest
    @CsvSource({
            "0, INFORMATIONAL",
            "15, INFORMATIONAL",
            "24, INFORMATIONAL",
            "25, SUSPICIOUS",
            "40, SUSPICIOUS",
            "49, SUSPICIOUS",
            "50, HIGH",
            "65, HIGH",
            "74, HIGH",
            "75, CRITICAL",
            "90, CRITICAL",
            "100, CRITICAL"
    })
    @DisplayName("Score threshold mapping boundary tests")
    void testScoreThresholdMapping(int score, ThreatLevel expectedLevel) {
        ThreatLevel mappedLevel = scoringService.mapScoreToLevel(score);
        assertThat(mappedLevel).isEqualTo(expectedLevel);
    }
}
