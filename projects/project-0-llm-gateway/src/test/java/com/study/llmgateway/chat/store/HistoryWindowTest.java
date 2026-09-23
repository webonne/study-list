package com.study.llmgateway.chat.store;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoryWindowTest {

    @Test
    void keepsHistoryWhenUnderLimit() {
        List<Turn> history = rounds(2);

        assertEquals(history, HistoryWindow.truncate(history, 20));
    }

    @Test
    void dropsEarliestFullRound() {
        List<Turn> history = rounds(3);

        List<Turn> kept = HistoryWindow.truncate(history, 4);

        assertEquals(List.of(
                Turn.user("q2"), Turn.assistant("a2"),
                Turn.user("q3"), Turn.assistant("a3")), kept);
    }

    @Test
    void doesNotStartWithOrphanAssistant() {
        List<Turn> history = rounds(3);

        List<Turn> kept = HistoryWindow.truncate(history, 5);

        assertEquals(List.of(
                Turn.user("q2"), Turn.assistant("a2"),
                Turn.user("q3"), Turn.assistant("a3")), kept);
    }

    @Test
    void maxZeroDropsAll() {
        assertEquals(List.of(), HistoryWindow.truncate(rounds(2), 0));
    }

    private static List<Turn> rounds(int count) {
        List<Turn> turns = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            turns.add(Turn.user("q" + i));
            turns.add(Turn.assistant("a" + i));
        }
        return turns;
    }
}
