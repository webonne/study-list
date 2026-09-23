package com.study.llmgateway.chat.store;

import java.util.ArrayList;
import java.util.List;

/**
 * 把历史收进长度上限。
 *
 * <p>超出时丢掉<b>最早的完整轮次</b>（一问一答算一条轮次），并且不会让留下的历史
 * 以 assistant 开头——那样模型会看到一段没有问题的回答。
 *
 * <p>压缩（把丢掉的早期轮次总结成一条再留着）先不做。以后要换，改这里的丢弃策略即可，
 * {@code /chat} 不用改。
 */
public final class HistoryWindow {

    private HistoryWindow() {
    }

    public static List<Turn> truncate(List<Turn> history, int maxMessages) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(maxMessages, 0);
        if (history.size() <= limit) {
            return List.copyOf(history);
        }
        List<Turn> kept = new ArrayList<>(history.subList(history.size() - limit, history.size()));
        while (!kept.isEmpty() && kept.get(0).role() == Turn.Role.ASSISTANT) {
            kept.remove(0);
        }
        return List.copyOf(kept);
    }
}
