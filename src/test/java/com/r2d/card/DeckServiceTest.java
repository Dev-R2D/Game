package com.r2d.card;

import java.util.List;

import com.r2d.common.R2dException;
import com.r2d.domain.ContributionType;
import com.r2d.support.DatabaseCleaner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class DeckServiceTest {

    @Autowired
    private DeckService deckService;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void resetDatabase() {
        databaseCleaner.resetToSeedState();
    }

    @Test
    @DisplayName("같은 계열 3장은 상위 시너지를 발동한다")
    void tripleSameLineGivesTopSynergy() {
        DeckEffect effect = deckService.resolve(List.of("TB_SCOUT_1", "TB_PATH_3", "TB_FRONTIER_5"));

        assertThat(effect.synergyLabel()).contains("상위 시너지");
        assertThat(effect.synergy()).isEqualTo(1.30);
        assertThat(effect.trailblazeCount()).isEqualTo(3);
        assertThat(effect.bonusFor(ContributionType.NEW)).isGreaterThan(1.0);
        // 개척 덱은 갱신·검증 구간에서는 아무 보정도 받지 못합니다.
        assertThat(effect.bonusFor(ContributionType.UPDATE)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("같은 계열 2장은 계열 시너지를 발동한다")
    void pairGivesLineSynergy() {
        DeckEffect effect = deckService.resolve(List.of("TB_SCOUT_1", "TB_PATH_3", "EN_STEADY_1"));

        assertThat(effect.synergyLabel()).contains("계열 시너지");
        assertThat(effect.synergy()).isEqualTo(1.15);
        assertThat(effect.enduranceBonus()).isGreaterThan(0);
    }

    @Test
    @DisplayName("계열을 섞으면 최대 배율은 낮아지지만 여러 기여 종류를 함께 보정한다")
    void mixedDeckTradesPeakForCoverage() {
        DeckEffect mixed = deckService.resolve(List.of("TB_PATH_3", "SC_REVISIT_3", "VF_CHAIN_3"));
        DeckEffect focused = deckService.resolve(List.of("TB_SCOUT_1", "TB_PATH_3", "TB_FRONTIER_5"));

        assertThat(mixed.synergy()).isLessThan(focused.synergy());
        assertThat(mixed.contributionBonus()).containsKeys(
                ContributionType.NEW, ContributionType.UPDATE, ContributionType.VERIFY);
        assertThat(mixed.bonusFor(ContributionType.NEW)).isLessThan(focused.bonusFor(ContributionType.NEW));
    }

    @Test
    @DisplayName("덱은 정확히 3장이어야 하고 중복 편성은 막는다")
    void deckValidation() {
        assertThatThrownBy(() -> deckService.resolve(List.of("TB_PATH_3", "SC_REVISIT_3")))
                .isInstanceOf(R2dException.class)
                .hasMessageContaining("3장");

        assertThatThrownBy(() -> deckService.resolve(List.of("TB_PATH_3", "TB_PATH_3", "SC_REVISIT_3")))
                .isInstanceOf(R2dException.class)
                .hasMessageContaining("중복");

        assertThatThrownBy(() -> deckService.resolve(List.of("NOPE_1", "TB_PATH_3", "SC_REVISIT_3")))
                .isInstanceOf(R2dException.class)
                .hasMessageContaining("존재하지 않는");
    }

    @Test
    @DisplayName("덱을 고르지 않아도 중립 덱으로 주행할 수 있다")
    void emptyDeckIsNeutral() {
        DeckEffect effect = deckService.resolve(List.of());

        assertThat(effect.synergy()).isEqualTo(1.0);
        assertThat(effect.bonusFor(ContributionType.NEW)).isEqualTo(1.0);
    }
}
