package com.r2d.card;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, String> {

    List<Card> findByCodeIn(Collection<String> codes);

    List<Card> findByCommemorativeFalse();
}
