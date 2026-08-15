package com.r2d.card;

import com.r2d.domain.CardLine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 탐사 카드.
 *
 * <p>카드는 게임 내부 성장에만 쓰이며 구매·양도·현금화할 수 없습니다. 유료 상품 보유 여부는
 * 데이터 기여 배율에 영향을 주지 않으므로 카드에는 가격 개념 자체를 두지 않습니다.
 */
@Entity
@Table(name = "cards")
public class Card {

    @Id
    @Column(length = 30)
    private String code;

    @Column(nullable = false, length = 40)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardLine line;

    /** 1~5. 높을수록 계열 보정이 커지지만 계열 자체를 바꾸지는 않습니다. */
    @Column(nullable = false)
    private int tier;

    @Column(nullable = false, length = 200)
    private String description;

    /** 기념 카드 여부. 이상 후보 발견·검증 완료로만 해금되며 팩 확률로는 나오지 않습니다. */
    @Column(nullable = false)
    private boolean commemorative = false;

    protected Card() {
    }

    public Card(String code, String name, CardLine line, int tier, String description) {
        this.code = code;
        this.name = name;
        this.line = line;
        this.tier = tier;
        this.description = description;
    }

    public static Card commemorative(String code, String name, CardLine line, String description) {
        Card card = new Card(code, name, line, 3, description);
        card.commemorative = true;
        return card;
    }

    /** 이 카드가 담당 계열에 더해주는 배율 보정. 티어가 높을수록 커집니다. */
    public double lineBonus(double bonusPerCard) {
        return bonusPerCard * (0.6 + 0.2 * tier);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public CardLine getLine() {
        return line;
    }

    public int getTier() {
        return tier;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCommemorative() {
        return commemorative;
    }
}
