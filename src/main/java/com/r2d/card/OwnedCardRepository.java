package com.r2d.card;

import java.util.List;
import java.util.Optional;

import com.r2d.player.Player;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OwnedCardRepository extends JpaRepository<OwnedCard, Long> {

    /**
     * 보유 카드 목록.
     *
     * <p>{@code open-in-view=false}이므로 컨트롤러에서 응답을 만들 때는 이미 영속성 컨텍스트가
     * 닫혀 있습니다. 카드 정보를 함께 내려보내야 하니 조회 시점에 fetch join으로 같이 읽습니다.
     */
    @Query("select o from OwnedCard o join fetch o.card where o.player = :player")
    List<OwnedCard> findByPlayerWithCard(@Param("player") Player player);

    Optional<OwnedCard> findByPlayerAndCard(Player player, Card card);

    List<OwnedCard> findByPlayerAndSourceCellIdAndLockedTrue(Player player, String sourceCellId);

    List<OwnedCard> findBySourceCellIdAndLockedTrue(String sourceCellId);
}
